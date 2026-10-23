
package com.ofss.risk.service;

import com.ofss.risk.repository.ApprovalAssignmentOutboxRepository;
import com.ofss.risk.repository.ApprovalAssignmentOutboxRepository.AssignmentEvent;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(
        prefix = "approval-assignment",
        name = "enabled",
        havingValue = "true"
)
public class ApprovalAssignmentDispatcher {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ApprovalAssignmentDispatcher.class
            );

    private final ApprovalAssignmentOutboxRepository outbox;
    private final RestClient securityClient;
    private final RestClient approvalClient;

    private final String clientId;
    private final String clientSecret;

    public ApprovalAssignmentDispatcher(
            ApprovalAssignmentOutboxRepository outbox,

            @Value("${integration.security-url}")
            String securityUrl,

            @Value("${integration.approval-workflow-url}")
            String approvalWorkflowUrl,

            @Value("${integration.approval-assignment.client-id}")
            String clientId,

            @Value("${integration.approval-assignment.client-secret}")
            String clientSecret
    ) {
        this.outbox = outbox;

        this.clientId = Objects.requireNonNull(
                clientId
        );

        this.clientSecret = Objects.requireNonNull(
                clientSecret
        );

        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .build()
        );

        factory.setReadTimeout(Duration.ofSeconds(10));

        this.securityClient = RestClient.builder()
                .baseUrl(securityUrl)
                .requestFactory(factory)
                .build();

        this.approvalClient = RestClient.builder()
                .baseUrl(approvalWorkflowUrl)
                .requestFactory(factory)
                .build();
    }

    @Scheduled(
            fixedDelayString =
                    "${approval-assignment.dispatch.fixed-delay-ms:5000}"
    )
    public void dispatch() {

        /*
         * Recover abandoned claims.
         *
         * The five-minute threshold is longer
         * than the configured HTTP timeouts.
         */
        try {
            int recovered = outbox.recoverStaleClaims(5);

            if (recovered > 0) {
                log.warn(
                        "Recovered {} stale assignment claims",
                        recovered
                );
            }

        } catch (Exception ex) {
            log.error(
                    "Could not recover stale assignment claims",
                    ex
            );
            return;
        }

        AssignmentEvent event;

        /*
         * This repository method commits the claim
         * before we contact any external service.
         */
        try {
            var claimed = outbox.claimNextPending();

            if (claimed.isEmpty()) {
                return;
            }

            event = claimed.get();

        } catch (Exception ex) {
            log.error(
                    "Could not claim approval assignment event",
                    ex
            );
            return;
        }

        try {
            String token = obtainMachineToken();

            AssignmentResponse response =
                    approvalClient.post()
                            .uri(
                                    "/api/v1/internal/approval-assignments/{paymentId}",
                                    event.paymentId()
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + token
                            )
                            .retrieve()
                            .body(AssignmentResponse.class);

            /*
             * HTTP success alone is insufficient:
             * verify the confirmed workflow belongs
             * to the expected payment.
             */
            if (response == null
                    || response.workflowId() == null
                    || response.workflowId() <= 0
                    || response.paymentId() == null
                    || response.paymentId().longValue()
                            != event.paymentId()) {

                throw new IllegalStateException(
                        "Invalid assignment confirmation"
                );
            }

            /*
             * Pass ATTEMPT_COUNT as the claim version.
             * An old worker cannot complete a claim
             * recovered by another dispatcher.
             */
            outbox.markCompleted(
                    event.eventId(),
                    event.attemptCount()
            );

            log.info(
                    "Approval assignment completed: "
                            + "eventId={}, paymentId={}, workflowId={}",
                    event.eventId(),
                    event.paymentId(),
                    response.workflowId()
            );

        } catch (Exception ex) {

            log.error(
                    "Approval assignment failed: "
                            + "eventId={}, paymentId={}, attempt={}",
                    event.eventId(),
                    event.paymentId(),
                    event.attemptCount(),
                    ex
            );

            /*
             * Retrying is safe because the approval
             * API returns an existing task for the
             * same payment.
             *
             * A 409 response is not treated as
             * successful assignment.
             */
            try {
                outbox.markFailed(
                        event.eventId(),
                        event.attemptCount(),
                        safeError(ex)
                );

            } catch (Exception updateError) {
                log.warn(
                        "Could not reset assignment claim: "
                                + "eventId={}, attempt={}",
                        event.eventId(),
                        event.attemptCount(),
                        updateError
                );
            }
        }
    }

    private String obtainMachineToken() {

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
                    "Invalid machine-token response"
            );
        }

        return response.accessToken();
    }

    /*
     * Store exception type only.
     * Never persist credentials, JWTs or
     * sensitive HTTP request details.
     */
    private String safeError(Exception exception) {

        return exception.getClass().getSimpleName();
    }

    private record TokenRequest(
            String clientId,
            String clientSecret
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(
            ignoreUnknown = true
    )
    private record TokenResponse(
            String accessToken,
            String tokenType,
            int expiresIn
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(
            ignoreUnknown = true
    )
    private record AssignmentResponse(
            Long workflowId,
            Long paymentId
    ) {}
}
