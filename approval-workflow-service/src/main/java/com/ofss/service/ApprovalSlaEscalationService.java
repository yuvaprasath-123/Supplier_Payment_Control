package com.ofss.service;

import com.ofss.entity.ApprovalEscalation;
import com.ofss.entity.ApprovalLevel;
import com.ofss.entity.ApprovalStatus;
import com.ofss.entity.ApprovalTask;

import com.ofss.repository.ApprovalEscalationRepository;
import com.ofss.repository.ApprovalTaskRepository;
import com.ofss.repository.BackupApproverRepository;
import com.ofss.repository.TrustedApprovalDataRepository;
import com.ofss.repository.TrustedApprovalDataRepository.SlaPolicy;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalSlaEscalationService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ApprovalSlaEscalationService.class
            );

    private static final String AUTO_ESCALATED =
            "AUTO_ESCALATED";

    private static final String AUTO_L3_BACKUP =
            "AUTO_REASSIGNED_TO_L3_BACKUP";

    private static final String AUTO_MANUAL_REVIEW =
            "AUTO_MANUAL_REVIEW";

    private static final String NEXT_LEVEL_POLICY =
            "NEXT_LEVEL_V1";

    private static final String L3_BACKUP_POLICY =
            "SAME_LEVEL_BACKUP_ONCE_V1";

    private static final String MANUAL_REVIEW_POLICY =
            "MANUAL_REVIEW_V1";


    private final ApprovalTaskRepository
            taskRepository;

    private final ApprovalEscalationRepository
            escalationRepository;

    private final BackupApproverRepository
            backupRepository;

    private final EligibleApproverSelector
            approverSelector;

    private final TrustedApprovalDataRepository
            trustedData;

    /*
     * NEW:
     *
     * Calculates SLA deadlines using the configured
     * business calendar:
     *
     * Monday-Friday
     * 09:00-17:00
     * Asia/Kolkata
     *
     * Weekends and configured holidays consume
     * zero SLA time.
     */
    private final BusinessCalendarService
            businessCalendarService;


    /*
     * Fixed creation-time cutoff for the
     * new-payments-only SLA rollout.
     *
     * Null means automatic escalation is blocked.
     *
     * This value is read once at startup.
     * It must not advance on every scheduler scan.
     */
    private final OffsetDateTime
            rolloutCutoff;


    public ApprovalSlaEscalationService(
            ApprovalTaskRepository taskRepository,
            ApprovalEscalationRepository escalationRepository,
            BackupApproverRepository backupRepository,
            EligibleApproverSelector approverSelector,
            TrustedApprovalDataRepository trustedData,
            BusinessCalendarService businessCalendarService,

            @Value("${approval-sla.rollout-cutoff:}")
            String rolloutCutoffText
    ) {

        this.taskRepository =
                taskRepository;

        this.escalationRepository =
                escalationRepository;

        this.backupRepository =
                backupRepository;

        this.approverSelector =
                approverSelector;

        this.trustedData =
                trustedData;

        this.businessCalendarService =
                businessCalendarService;


        /*
         * Fail closed when the cutoff is absent.
         *
         * Reject malformed values rather than
         * silently allowing historical tasks.
         */
        if (rolloutCutoffText == null
                || rolloutCutoffText.isBlank()) {

            this.rolloutCutoff =
                    null;

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
    }


    /*
     * Process one overdue task in one Oracle
     * transaction.
     *
     * The pessimistic lock protects against
     * concurrent approval decisions, rejection,
     * manual escalation and duplicate scheduler
     * processing.
     *
     * The rollout cutoff is also enforced here
     * so scheduler query changes cannot bypass
     * the historical-task restriction.
     */
    @Transactional
    public boolean escalateOverdueTask(
            Long taskId,
            String authorization
    ) {

        if (taskId == null
                || taskId <= 0) {

            return false;
        }


        /*
         * Missing cutoff blocks all
         * automatic escalation attempts.
         *
         * This check occurs before querying
         * or locking any approval task.
         */
        if (rolloutCutoff == null) {

            log.warn(
                    "Automatic SLA escalation blocked: "
                    + "rollout cutoff is not configured"
            );

            return false;
        }


        ApprovalTask task =
                taskRepository
                        .findForUpdate(taskId)
                        .orElse(null);


        if (task == null) {

            return false;
        }


        /*
         * Enforce the fixed cutoff AFTER
         * obtaining the Oracle row lock.
         *
         * Compare actual instants so different
         * timezone offsets cannot change the
         * eligibility decision.
         *
         * A missing task creation timestamp
         * must never be treated as eligible.
         */
        if (task.createdAt == null
                || task.createdAt
                        .toInstant()
                        .isBefore(
                                rolloutCutoff.toInstant()
                        )) {

            log.debug(
                    "SLA task excluded by rollout cutoff: taskId={}",
                    task.id
            );

            return false;
        }


        /*
         * Revalidate after acquiring the row lock.
         *
         * Never modify an approved, rejected
         * or manual-review task.
         */
        if (task.status
                != ApprovalStatus.PENDING_APPROVAL
                && task.status
                != ApprovalStatus.ESCALATED) {

            return false;
        }


        OffsetDateTime now =
                OffsetDateTime.now();


        /*
         * dueAt is already business-calendar-aware.
         *
         * Therefore the scheduler/service only needs
         * to compare the current instant against
         * the stored deadline.
         */
        if (task.dueAt == null
                || now.isBefore(
                        task.dueAt
                )) {

            return false;
        }


        if (task.requiredLevel == null
                || task.slaMinutes <= 0
                || task.requesterId == null
                || task.assignedApproverId == null) {

            throw new IllegalStateException(
                    "Invalid approval task configuration"
            );
        }


        /*
         * Read trusted payment data and its
         * stored configuration version.
         *
         * Do not use the globally active
         * configuration version here.
         */
        var payment =
                trustedData.getPaymentData(
                        task.paymentId
                );


        if (!"PENDING_APPROVAL".equals(
                payment.paymentStatus()
        )) {

            return false;
        }


        if (!"COMPLETED".equals(
                payment.assessmentStatus()
        )) {

            throw new IllegalStateException(
                    "Risk assessment must be completed"
            );
        }


        if (!task.requesterId.equals(
                payment.requesterId()
        )) {

            throw new IllegalStateException(
                    "Approval requester does not match payment"
            );
        }


        ApprovalLevel previousLevel =
                task.requiredLevel;


        Long previousApproverId =
                task.assignedApproverId;


        /*
         * Look up the Oracle SLA policy for the
         * CURRENT approval level.
         *
         * Example:
         *
         * A payment originally requiring L1
         * might currently be escalated to L3.
         *
         * Its current policy must be read
         * from the L3 SLA configuration.
         */
        SlaPolicy currentPolicy =
                trustedData.getSlaPolicy(
                        payment.configurationVersionId(),
                        previousLevel
                );


        validatePolicy(
                previousLevel,
                currentPolicy
        );


        ApprovalEscalation escalation =
                new ApprovalEscalation();


        escalation.taskId =
                task.id;

        escalation.paymentId =
                task.paymentId;

        escalation.previousApproverId =
                previousApproverId;

        escalation.previousLevel =
                previousLevel;

        escalation.occurredAt =
                now;


        /*
         * =====================================================
         * L1 / L2 ESCALATION
         * =====================================================
         *
         * L1 -> L2
         * L2 -> L3
         *
         * Oracle must explicitly specify:
         *
         * NEXT_LEVEL_V1
         */
        if (previousLevel == ApprovalLevel.L1
                || previousLevel == ApprovalLevel.L2) {


            ApprovalLevel nextLevel =
                    previousLevel == ApprovalLevel.L1
                            ? ApprovalLevel.L2
                            : ApprovalLevel.L3;


            /*
             * Obtain the SLA duration for
             * the NEW approval level.
             *
             * Do not assume all levels always
             * have the same SLA duration.
             */
            SlaPolicy nextPolicy =
                    trustedData.getSlaPolicy(
                            payment.configurationVersionId(),
                            nextLevel
                    );


            validatePolicy(
                    nextLevel,
                    nextPolicy
            );


            Long nextApprover =
                    approverSelector.selectAtExactLevel(
                            nextLevel,
                            task.requesterId,
                            previousApproverId,
                            authorization
                    );


            task.requiredLevel =
                    nextLevel;

            task.assignedApproverId =
                    nextApprover;

            task.status =
                    ApprovalStatus.ESCALATED;


            /*
             * Store configured SLA duration in minutes.
             */
            task.slaMinutes =
                    nextPolicy.durationSeconds()
                            / 60;


            /*
             * =================================================
             * HOLIDAY-AWARE SLA DEADLINE
             * =================================================
             *
             * OLD:
             *
             * now.plusSeconds(
             *     nextPolicy.durationSeconds()
             * )
             *
             * NEW:
             *
             * Business SLA time counts only:
             *
             * Monday-Friday
             * 09:00-17:00 IST
             *
             * Saturdays = 0 SLA minutes
             * Sundays   = 0 SLA minutes
             * Holidays  = 0 SLA minutes
             *
             * Outside working hours the SLA waits
             * until the next working period.
             */
            task.dueAt =
                    businessCalendarService
                            .calculateDueAt(
                                    now,
                                    task.slaMinutes
                            );


            escalation.requiredLevel =
                    nextLevel;

            escalation.assignedApproverId =
                    nextApprover;

            escalation.action =
                    AUTO_ESCALATED;


            escalation.remark =
                    "Automatic SLA escalation from "
                    + previousLevel.name()
                    + " to "
                    + nextLevel.name()
                    + " under "
                    + NEXT_LEVEL_POLICY;


            log.info(
                    "Holiday-aware SLA assigned after escalation: "
                    + "taskId={}, paymentId={}, "
                    + "level={}, slaMinutes={}, dueAt={}",
                    task.id,
                    task.paymentId,
                    nextLevel,
                    task.slaMinutes,
                    task.dueAt
            );


        /*
         * =====================================================
         * L3 ESCALATION
         * =====================================================
         */
        } else if (
                previousLevel == ApprovalLevel.L3
        ) {


            /*
             * L3:
             *
             * Oracle must specify:
             *
             * Escalation:
             * SAME_LEVEL_BACKUP_ONCE_V1
             *
             * Terminal:
             * MANUAL_REVIEW_V1
             *
             * A configured and eligible backup
             * can be assigned only once.
             */

            List<ApprovalEscalation> history =
                    escalationRepository
                            .findByPaymentIdOrderByOccurredAtAsc(
                                    task.paymentId
                            );


            /*
             * Recognize both automatic and
             * existing manual backup assignments.
             *
             * This prevents the scheduler from
             * repeatedly selecting an L3 backup.
             */
            boolean backupAlreadyUsed =
                    history.stream()
                            .anyMatch(
                                    record ->
                                            record.previousLevel
                                                    == ApprovalLevel.L3
                                            && (
                                                AUTO_L3_BACKUP.equals(
                                                        record.action
                                                )
                                                ||
                                                "REASSIGNED_TO_BACKUP"
                                                        .equals(
                                                                record.action
                                                        )
                                            )
                            );


            if (backupAlreadyUsed) {


                moveToManualReview(
                        task,
                        escalation,
                        "L3 backup SLA expired; "
                        + "manual review required"
                );


            } else {


                var mapping =
                        backupRepository
                                .findByApproverIdAndApprovalLevel(
                                        previousApproverId,
                                        ApprovalLevel.L3
                                );


                if (mapping.isEmpty()) {


                    /*
                     * If no L3 backup mapping
                     * exists, enter manual review.
                     *
                     * Never assign an arbitrary
                     * or lower-level approver.
                     */
                    moveToManualReview(
                            task,
                            escalation,
                            "L3 SLA expired; "
                            + "no configured backup approver"
                    );


                } else {


                    Long backupApproverId =
                            mapping.get()
                                    .backupApproverId;


                    /*
                     * Verify the exact configured
                     * backup through Security Service.
                     *
                     * A service outage throws
                     * an exception and rolls back.
                     */
                    boolean eligible =
                            approverSelector
                                    .isEligibleBackup(
                                            ApprovalLevel.L3,
                                            backupApproverId,
                                            task.requesterId,
                                            previousApproverId,
                                            authorization
                                    );


                    if (!eligible) {


                        moveToManualReview(
                                task,
                                escalation,
                                "L3 SLA expired; "
                                + "configured backup is ineligible"
                        );


                    } else {


                        task.requiredLevel =
                                ApprovalLevel.L3;


                        task.assignedApproverId =
                                backupApproverId;


                        task.status =
                                ApprovalStatus.ESCALATED;


                        /*
                         * A same-level L3 backup
                         * receives the configured
                         * L3 SLA duration.
                         */
                        task.slaMinutes =
                                currentPolicy.durationSeconds()
                                        / 60;


                        /*
                         * =================================================
                         * HOLIDAY-AWARE L3 BACKUP SLA
                         * =================================================
                         *
                         * The backup approver receives a new SLA period,
                         * but only working time is counted.
                         */
                        task.dueAt =
                                businessCalendarService
                                        .calculateDueAt(
                                                now,
                                                task.slaMinutes
                                        );


                        escalation.requiredLevel =
                                ApprovalLevel.L3;


                        escalation.assignedApproverId =
                                backupApproverId;


                        escalation.action =
                                AUTO_L3_BACKUP;


                        escalation.remark =
                                "L3 SLA expired; "
                                + "automatically reassigned "
                                + "to configured L3 backup "
                                + "under "
                                + L3_BACKUP_POLICY;


                        log.info(
                                "Holiday-aware L3 backup SLA assigned: "
                                + "taskId={}, paymentId={}, "
                                + "slaMinutes={}, dueAt={}",
                                task.id,
                                task.paymentId,
                                task.slaMinutes,
                                task.dueAt
                        );
                    }
                }
            }


        } else {


            throw new IllegalStateException(
                    "Unsupported approval level"
            );
        }


        /*
         * Save the task transition and the
         * escalation-history record in the
         * same Oracle transaction.
         *
         * If either write fails, the entire
         * transaction rolls back.
         */
        taskRepository.save(
                task
        );


        escalationRepository.save(
                escalation
        );


        taskRepository.flush();

        escalationRepository.flush();


        log.info(
                "Automatic SLA transition: "
                + "taskId={}, paymentId={}, "
                + "previousLevel={}, currentLevel={}, "
                + "status={}, action={}, dueAt={}",
                task.id,
                task.paymentId,
                previousLevel,
                task.requiredLevel,
                task.status,
                escalation.action,
                task.dueAt
        );


        return true;
    }


    /*
     * Reject unsupported or inconsistent
     * Oracle SLA configurations.
     *
     * A validation failure occurs before
     * modifying the approval task.
     *
     * The existing transaction then rolls back,
     * leaving the task available for retry
     * after the configuration is corrected.
     */
    private void validatePolicy(
            ApprovalLevel level,
            SlaPolicy policy
    ) {


        if (policy == null
                || policy.durationSeconds() <= 0
                || policy.durationSeconds() % 60 != 0
                || policy.durationSeconds() / 60 > 525600) {


            throw new IllegalStateException(
                    "Invalid configured SLA duration"
            );
        }


        String escalationCode =
                policy.escalationPolicyCode();


        String terminalCode =
                policy.terminalLevelPolicyCode();


        /*
         * L1 / L2
         */
        if (level == ApprovalLevel.L1
                || level == ApprovalLevel.L2) {


            if (!NEXT_LEVEL_POLICY.equals(
                    escalationCode
            )) {


                throw new IllegalStateException(
                        "Unsupported L1/L2 escalation policy"
                );
            }


            if (terminalCode != null
                    && !terminalCode.isBlank()) {


                throw new IllegalStateException(
                        "Unexpected terminal policy at L1/L2"
                );
            }


            return;
        }


        /*
         * L3
         */
        if (level == ApprovalLevel.L3) {


            if (!L3_BACKUP_POLICY.equals(
                    escalationCode
            )) {


                throw new IllegalStateException(
                        "Unsupported L3 escalation policy"
                );
            }


            if (!MANUAL_REVIEW_POLICY.equals(
                    terminalCode
            )) {


                throw new IllegalStateException(
                        "Unsupported L3 terminal policy"
                );
            }


            return;
        }


        throw new IllegalStateException(
                "Unsupported approval level"
        );
    }


    /*
     * Terminal fallback:
     *
     * Keep the approval level at L3.
     *
     * Preserve the assigned approver for
     * audit visibility.
     *
     * Only an authorized Finance Manager
     * may resolve manual review through
     * the existing override mechanism.
     */
    private void moveToManualReview(
            ApprovalTask task,
            ApprovalEscalation escalation,
            String reason
    ) {


        task.status =
                ApprovalStatus.MANUAL_REVIEW;


        escalation.requiredLevel =
                ApprovalLevel.L3;


        escalation.assignedApproverId =
                task.assignedApproverId;


        escalation.action =
                AUTO_MANUAL_REVIEW;


        escalation.remark =
                reason;
    }
}
