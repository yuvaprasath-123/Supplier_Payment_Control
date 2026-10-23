
package com.ofss.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.ofss.entity.ApprovalStatus;
import com.ofss.entity.ApprovalTask;
import com.ofss.repository.ApprovalTaskRepository;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import jakarta.persistence.criteria.Predicate;

@Component
@ConditionalOnProperty(
        prefix = "approval-sla",
        name = "enabled",
        havingValue = "true"
)
public class ApprovalSlaScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ApprovalSlaScheduler.class
            );

    private static final int MAX_BATCH_SIZE = 50;

    private final ApprovalTaskRepository taskRepository;
    private final ApprovalSlaEscalationService escalationService;
    private final RestClient securityClient;

    private final String clientId;
    private final String clientSecret;

    private final boolean testOnly;
    private final long testTaskId;
    private final boolean allowAllTasks;
    private final int batchSize;

    /*
     * Fixed new-payments-only rollout cutoff.
     *
     * Null means scheduling is blocked.
     * This value is parsed once at startup.
     */
    private final OffsetDateTime rolloutCutoff;

    /*
     * In-memory keyset cursor.
     *
     * Tasks are ordered by dueAt and task ID.
     * Failed tasks remain eligible on later passes.
     *
     * Cursor state is not durable across restarts.
     */
    private OffsetDateTime cursorDueAt;
    private Long cursorTaskId;

    public ApprovalSlaScheduler(
            ApprovalTaskRepository taskRepository,
            ApprovalSlaEscalationService escalationService,

            @Value("${integration.security-url:http://localhost:8081}")
            String securityUrl,

            @Value("${approval-sla.client-id:}")
            String clientId,

            @Value("${approval-sla.client-secret:}")
            String clientSecret,

            @Value("${approval-sla.test-only:true}")
            boolean testOnly,

            @Value("${approval-sla.test-task-id:0}")
            long testTaskId,

            @Value("${approval-sla.allow-all-tasks:false}")
            boolean allowAllTasks,

            @Value("${approval-sla.batch-size:25}")
            int batchSize,

            @Value("${approval-sla.rollout-cutoff:}")
            String rolloutCutoffText
    ) {
        this.taskRepository = taskRepository;
        this.escalationService = escalationService;

        this.clientId = clientId;
        this.clientSecret = clientSecret;

        this.testOnly = testOnly;
        this.testTaskId = testTaskId;
        this.allowAllTasks = allowAllTasks;

        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "SLA batch size must be between 1 and 50"
            );
        }

        this.batchSize = batchSize;

        /*
         * Missing cutoff: fail closed.
         *
         * Malformed cutoff: reject startup.
         *
         * Never silently replace an invalid
         * value with the current timestamp.
         */
        if (rolloutCutoffText == null
                || rolloutCutoffText.isBlank()) {

            this.rolloutCutoff = null;

        } else {

            try {
                this.rolloutCutoff =
                        OffsetDateTime.parse(
                                rolloutCutoffText.trim()
                        );

            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(
                        "Invalid approval-sla.rollout-cutoff; "
                        + "expected ISO-8601 date-time with offset",
                        ex
                );
            }
        }

        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .build()
        );

        factory.setReadTimeout(Duration.ofSeconds(5));

        this.securityClient = RestClient.builder()
                .baseUrl(securityUrl)
                .requestFactory(factory)
                .build();
    }

    @Scheduled(
            fixedDelayString =
                    "${approval-sla.scan-interval-ms:60000}"
    )
    public synchronized void scanOverdueApprovals() {

        /*
         * Missing rollout cutoff blocks
         * ALL automatic SLA processing.
         *
         * This applies to both test mode
         * and unrestricted mode.
         */
        if (rolloutCutoff == null) {
            log.warn(
                    "SLA scheduler blocked: "
                    + "rollout cutoff is not configured"
            );
            return;
        }

        /*
         * Test mode must specify one
         * positive approval task ID.
         */
        if (testOnly && testTaskId <= 0) {
            log.warn(
                    "SLA test-only mode requires a valid task ID"
            );
            return;
        }

        /*
         * Unrestricted scanning requires
         * an additional explicit permission.
         */
        if (!testOnly && !allowAllTasks) {
            log.warn(
                    "SLA unrestricted scanning is blocked; "
                    + "allow-all-tasks is disabled"
            );
            return;
        }

        List<ApprovalTask> tasks;

        try {
            if (testOnly) {

                /*
                 * TEST MODE:
                 *
                 * Retrieve only the configured task.
                 * Check its deadline, status,
                 * and creation-time cutoff.
                 */
                tasks = taskRepository.findById(testTaskId)
                        .filter(this::isEligibleTestTask)
                        .map(List::of)
                        .orElseGet(List::of);

            } else {

                /*
                 * NORMAL MODE:
                 *
                 * Query only overdue tasks
                 * created after rollout activation.
                 *
                 * The query remains bounded
                 * by batchSize.
                 */
                tasks = findNextOverdueBatch();
            }

        } catch (Exception ex) {
            log.error(
                    "Automatic SLA task lookup failed: {}",
                    ex.getClass().getSimpleName()
            );
            return;
        }

        if (tasks.isEmpty()) {
            return;
        }

        log.info(
                "SLA scan selected {} eligible overdue tasks; "
                + "testOnly={}, batchSize={}",
                tasks.size(),
                testOnly,
                batchSize
        );

        /*
         * Obtain the dedicated machine JWT.
         *
         * Never use human credentials
         * as a scheduler fallback.
         */
        String authorization;

        try {
            authorization =
                    "Bearer " + obtainMachineToken();

        } catch (Exception ex) {
            log.error(
                    "SLA scheduler authentication failed: {}",
                    ex.getClass().getSimpleName()
            );
            return;
        }

        /*
         * Each task is independently locked,
         * rechecked and updated by the
         * transactional escalation service.
         *
         * That service enforces the rollout
         * cutoff again under Oracle row lock.
         */
        for (ApprovalTask task : tasks) {

            try {
                boolean changed =
                        escalationService.escalateOverdueTask(
                                task.id,
                                authorization
                        );

                if (changed) {
                    log.info(
                            "SLA transition successful: taskId={}",
                            task.id
                    );
                }

            } catch (Exception ex) {
                log.error(
                        "SLA transition failed: "
                        + "taskId={}, error={}",
                        task.id,
                        ex.getClass().getSimpleName()
                );
            }
        }
    }

    /*
     * Bounded keyset pagination.
     *
     * Order:
     *   1. Earliest dueAt
     *   2. Smallest task ID
     *
     * Once the end is reached, return
     * to the oldest eligible overdue task.
     */
    private List<ApprovalTask> findNextOverdueBatch() {

        OffsetDateTime now = OffsetDateTime.now();

        List<ApprovalTask> tasks =
                queryOverdueBatch(now, true);

        if (tasks.isEmpty()
                && cursorDueAt != null
                && cursorTaskId != null) {

            cursorDueAt = null;
            cursorTaskId = null;

            tasks = queryOverdueBatch(now, false);
        }

        if (!tasks.isEmpty()) {

            ApprovalTask last =
                    tasks.get(tasks.size() - 1);

            cursorDueAt = last.dueAt;
            cursorTaskId = last.id;
        }

        return tasks;
    }

    private List<ApprovalTask> queryOverdueBatch(
            OffsetDateTime now,
            boolean useCursor
    ) {

        OffsetDateTime startingDueAt =
                useCursor ? cursorDueAt : null;

        Long startingTaskId =
                useCursor ? cursorTaskId : null;

        Specification<ApprovalTask> criteria =
                (root, query, cb) -> {

                    Predicate statusEligible =
                            root.get("status").in(
                                    ApprovalStatus.PENDING_APPROVAL,
                                    ApprovalStatus.ESCALATED
                            );

                    Predicate deadlineExpired =
                            cb.lessThanOrEqualTo(
                                    root.<OffsetDateTime>get("dueAt"),
                                    now
                            );

                    /*
                     * NEW:
                     *
                     * Exclude historical tasks at
                     * the database-query level.
                     *
                     * CREATED_AT is mapped to
                     * TIMESTAMP WITH TIME ZONE.
                     */
                    Predicate createdAfterCutoff =
                            cb.greaterThanOrEqualTo(
                                    root.<OffsetDateTime>get("createdAt"),
                                    rolloutCutoff
                            );

                    Predicate conditions =
                            cb.and(
                                    statusEligible,
                                    deadlineExpired,
                                    createdAfterCutoff
                            );

                    if (startingDueAt != null
                            && startingTaskId != null) {

                        Predicate laterDeadline =
                                cb.greaterThan(
                                        root.<OffsetDateTime>get("dueAt"),
                                        startingDueAt
                                );

                        Predicate sameDeadlineLaterId =
                                cb.and(
                                        cb.equal(
                                                root.get("dueAt"),
                                                startingDueAt
                                        ),
                                        cb.greaterThan(
                                                root.<Long>get("id"),
                                                startingTaskId
                                        )
                                );

                        conditions = cb.and(
                                conditions,
                                cb.or(
                                        laterDeadline,
                                        sameDeadlineLaterId
                                )
                        );
                    }

                    return conditions;
                };

        return taskRepository.findAll(
                criteria,
                PageRequest.of(
                        0,
                        batchSize,
                        Sort.by(
                                Sort.Order.asc("dueAt"),
                                Sort.Order.asc("id")
                        )
                )
        ).getContent();
    }

    /*
     * Single-task test selection.
     *
     * Tasks before the rollout cutoff
     * must be excluded even when a
     * matching testTaskId is supplied.
     */
    private boolean isEligibleTestTask(
            ApprovalTask task
    ) {

        if (task == null
                || task.createdAt == null
                || task.dueAt == null
                || rolloutCutoff == null) {

            return false;
        }

        /*
         * Compare actual instants to account
         * for different timezone offsets.
         */
        if (task.createdAt.toInstant().isBefore(
                rolloutCutoff.toInstant()
        )) {
            return false;
        }

        boolean eligibleStatus =
                task.status == ApprovalStatus.PENDING_APPROVAL
                || task.status == ApprovalStatus.ESCALATED;

        return eligibleStatus
                && !OffsetDateTime.now().isBefore(
                        task.dueAt
                );
    }

    private String obtainMachineToken() {

        if (clientId == null
                || clientId.isBlank()
                || clientSecret == null
                || clientSecret.isBlank()) {

            throw new IllegalStateException(
                    "SLA machine credentials are not configured"
            );
        }

        TokenResponse response =
                securityClient.post()
                        .uri("/api/v1/internal/service-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                new TokenRequest(
                                        clientId,
                                        clientSecret
                                )
                        )
                        .retrieve()
                        .body(TokenResponse.class);

        if (response == null
                || response.accessToken() == null
                || response.accessToken().isBlank()
                || !"Bearer".equalsIgnoreCase(
                        response.tokenType())) {

            throw new IllegalStateException(
                    "Invalid SLA machine-token response"
            );
        }

        return response.accessToken();
    }

    private record TokenRequest(
            String clientId,
            String clientSecret
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            String accessToken,
            String tokenType,
            int expiresIn
    ) {}
}


