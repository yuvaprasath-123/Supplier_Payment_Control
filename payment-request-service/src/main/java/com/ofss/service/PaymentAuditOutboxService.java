package com.ofss.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentAuditOutboxService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PaymentAuditOutboxService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /*
     * Record payment creation inside the existing
     * payment creation transaction.
     *
     * Preserves the original behavior.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaymentCreated(
            Long paymentId,
            Long invoiceId,
            Long supplierId,
            BigDecimal paymentAmount,
            Long requesterId
    ) {

        if (paymentId == null || paymentId <= 0
                || invoiceId == null || invoiceId <= 0
                || supplierId == null || supplierId <= 0
                || requesterId == null || requesterId <= 0
                || paymentAmount == null
                || paymentAmount.signum() <= 0) {

            throw new IllegalArgumentException(
                    "Valid payment, supplier, invoice, "
                            + "amount and requester are required"
            );
        }

        Map<String, Object> afterState =
                new LinkedHashMap<>();

        afterState.put("paymentId", paymentId);
        afterState.put("invoiceId", invoiceId);
        afterState.put("supplierId", supplierId);
        afterState.put("paymentAmount", paymentAmount);
        afterState.put("status", "DRAFT");

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "sourceService",
                "payment-request-service"
        );

        metadata.put(
                "action",
                "PAYMENT_REQUEST_CREATED"
        );

        writeAuditOutbox(
                paymentId,
                "PAYMENT_REQUEST_CREATED",
                requesterId.toString(),
                null,
                afterState,
                metadata
        );
    }

    /*
     * Record confirmed payment completion.
     *
     * The caller must already be handling a verified
     * PROCESSING -> COMPLETE transition.
     *
     * This method only inserts an Oracle audit outbox
     * row. It never contacts Stripe or Audit Service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaymentCompleted(
            Long paymentId,
            BigDecimal paymentAmount,
            String stripePaymentIntentId,
            LocalDateTime completedAt
    ) {

        if (paymentId == null || paymentId <= 0
                || paymentAmount == null
                || paymentAmount.signum() <= 0
                || stripePaymentIntentId == null
                || stripePaymentIntentId.isBlank()
                || completedAt == null) {

            throw new IllegalArgumentException(
                    "Valid confirmed payment completion "
                            + "details are required"
            );
        }

        Map<String, Object> beforeState =
                new LinkedHashMap<>();

        beforeState.put("paymentId", paymentId);
        beforeState.put("status", "PROCESSING");

        Map<String, Object> afterState =
                new LinkedHashMap<>();

        afterState.put("paymentId", paymentId);
        afterState.put("status", "COMPLETE");
        afterState.put("paymentAmount", paymentAmount);

        afterState.put(
                "transactionReference",
                stripePaymentIntentId
        );

        afterState.put(
                "completedAt",
                completedAt.toString()
        );

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "sourceService",
                "payment-request-service"
        );

        metadata.put(
                "action",
                "PAYMENT_COMPLETED"
        );

        metadata.put(
                "paymentProvider",
                "STRIPE"
        );

        metadata.put(
                "stripePaymentIntentId",
                stripePaymentIntentId
        );

        metadata.put(
                "completedAt",
                completedAt.toString()
        );

        /*
         * Kafka/system-triggered transition.
         * There is no authenticated human actor
         * executing this method.
         */
        writeAuditOutbox(
                paymentId,
                "PAYMENT_COMPLETED",
                null,
                beforeState,
                afterState,
                metadata
        );
    }

    /*
     * NEW: Record a verified payment rejection.
     *
     * This method must be called only when a trusted,
     * persisted approval decision causes the payment
     * to move from PENDING_APPROVAL to REJECTED.
     *
     * Propagation.MANDATORY ensures that this audit
     * insertion participates in the same Oracle
     * transaction as the payment status update.
     *
     * The caller is responsible for ensuring that
     * the transition is applied only once.
     *
     * No Stripe or HTTP request occurs here.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaymentRejected(
            Long paymentId,
            Long approvalTaskId,
            Long approvalDecisionId,
            Long approverId,
            String rejectionReason,
            LocalDateTime rejectedAt
    ) {

        if (paymentId == null || paymentId <= 0
                || approvalTaskId == null || approvalTaskId <= 0
                || approvalDecisionId == null
                || approvalDecisionId <= 0
                || approverId == null || approverId <= 0
                || rejectionReason == null
                || rejectionReason.isBlank()
                || rejectedAt == null) {

            throw new IllegalArgumentException(
                    "Valid verified payment rejection "
                            + "details are required"
            );
        }

        Map<String, Object> beforeState =
                new LinkedHashMap<>();

        beforeState.put("paymentId", paymentId);
        beforeState.put("status", "PENDING_APPROVAL");

        Map<String, Object> afterState =
                new LinkedHashMap<>();

        afterState.put("paymentId", paymentId);
        afterState.put("status", "REJECTED");

        afterState.put(
                "rejectedBy",
                approverId
        );

        afterState.put(
                "rejectionReason",
                rejectionReason
        );

        afterState.put(
                "rejectedAt",
                rejectedAt.toString()
        );

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "sourceService",
                "payment-request-service"
        );

        metadata.put(
                "action",
                "PAYMENT_REJECTED"
        );

        metadata.put(
                "approvalTaskId",
                approvalTaskId
        );

        metadata.put(
                "approvalDecisionId",
                approvalDecisionId
        );

        metadata.put(
                "approverId",
                approverId
        );

        metadata.put(
                "decision",
                "REJECTED"
        );

        metadata.put(
                "rejectionReason",
                rejectionReason
        );

        metadata.put(
                "rejectedAt",
                rejectedAt.toString()
        );

        writeAuditOutbox(
                paymentId,
                "PAYMENT_REJECTED",
                approverId.toString(),
                beforeState,
                afterState,
                metadata
        );
    }

    /*
     * Shared audit event construction and insertion.
     *
     * All audit metadata is saved in Oracle first.
     * The existing dispatcher publishes it later.
     *
     * The method intentionally remains private.
     */
    private void writeAuditOutbox(
            Long paymentId,
            String eventType,
            String actorId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            Map<String, Object> metadata
    ) {

        Map<String, Object> auditEvent =
                new LinkedHashMap<>();

        auditEvent.put("eventType", eventType);
        auditEvent.put("resourceType", "PAYMENT");

        auditEvent.put(
                "resourceId",
                paymentId.toString()
        );

        auditEvent.put("paymentId", paymentId);
        auditEvent.put("actorId", actorId);

        auditEvent.put(
                "correlationId",
                UUID.randomUUID().toString()
        );

        auditEvent.put(
                "beforeState",
                beforeState == null
                        ? null
                        : objectMapper.writeValueAsString(beforeState)
        );

        auditEvent.put(
                "afterState",
                objectMapper.writeValueAsString(afterState)
        );

        auditEvent.put(
                "metadata",
                objectMapper.writeValueAsString(metadata)
        );

        auditEvent.put(
                "occurredAt",
                OffsetDateTime.now().toString()
        );

        String payload =
                objectMapper.writeValueAsString(auditEvent);

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO PAY_AUDIT_OUTBOX (
                    PAYMENT_ID,
                    EVENT_TYPE,
                    PAYLOAD,
                    STATUS,
                    ATTEMPT_COUNT
                )
                VALUES (?, ?, ?, 'PENDING', 0)
                """,
                paymentId,
                eventType,
                payload
        );

        if (inserted != 1) {
            throw new IllegalStateException(
                    "Failed to create audit outbox event "
                            + eventType
                            + " for payment "
                            + paymentId
            );
        }
    }
}


