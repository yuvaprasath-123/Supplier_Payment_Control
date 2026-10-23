
package com.ofss.service;

import com.ofss.event.PaymentApprovedEvent;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentProcessingAttemptService {

    private final JdbcTemplate jdbcTemplate;

    public PaymentProcessingAttemptService(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public enum ClaimResult {
        CLAIMED,
        ALREADY_PROCESSING,
        ALREADY_COMPLETED,
        REQUIRES_RECONCILIATION
    }

    private record TrustedPayment(
            Long paymentId,
            Long invoiceId,
            Long supplierId,
            BigDecimal paymentAmount,
            String status,
            LocalDateTime approvedAt
    ) {}

    @Transactional
    public ClaimResult claim(PaymentApprovedEvent event) {

        if (event == null
                || event.getPaymentId() == null
                || event.getPaymentId() <= 0
                || event.getInvoiceId() == null
                || event.getSupplierId() == null
                || event.getPaymentAmount() == null
                || event.getApprovedBy() == null
                || event.getApprovedAt() == null) {

            throw new IllegalArgumentException(
                    "Approved-payment event is missing required data"
            );
        }

        Long paymentId = event.getPaymentId();

        /*
         * Lock the actual payment before validating
         * the Kafka event or creating an attempt.
         */
        List<TrustedPayment> payments = jdbcTemplate.query(
                """
                SELECT
                    PAYMENT_ID,
                    INVOICE_ID,
                    SUPPLIER_ID,
                    PAYMENT_AMOUNT,
                    STATUS,
                    APPROVED_AT
                FROM PAY_PAYMENT_REQUESTS
                WHERE PAYMENT_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> {

                    Timestamp approvedTimestamp =
                            rs.getTimestamp("APPROVED_AT");

                    LocalDateTime approvedAt =
                            approvedTimestamp == null
                                    ? null
                                    : approvedTimestamp.toLocalDateTime();

                    return new TrustedPayment(
                            rs.getLong("PAYMENT_ID"),
                            rs.getLong("INVOICE_ID"),
                            rs.getLong("SUPPLIER_ID"),
                            rs.getBigDecimal("PAYMENT_AMOUNT"),
                            rs.getString("STATUS"),
                            approvedAt
                    );
                },
                paymentId
        );

        if (payments.size() != 1) {
            throw new IllegalStateException(
                    "Expected one Oracle payment for ID " + paymentId
            );
        }

        TrustedPayment payment = payments.get(0);

        /*
         * Validate the full event against Oracle.
         * Kafka-provided amounts and IDs are never
         * treated as authoritative.
         */
        if (!payment.paymentId().equals(event.getPaymentId())
                || !payment.invoiceId().equals(event.getInvoiceId())
                || !payment.supplierId().equals(event.getSupplierId())
                || payment.paymentAmount() == null
                || payment.paymentAmount().compareTo(
                        event.getPaymentAmount()) != 0
                || payment.approvedAt() == null
                || !payment.approvedAt().equals(event.getApprovedAt())) {

            throw new IllegalStateException(
                    "Approved-payment event does not match Oracle payment "
                            + paymentId
            );
        }

        /*
         * Verify the approver against the persisted
         * final approval decision, not the Kafka payload.
         */
        List<Long> approvers = jdbcTemplate.query(
                """
                SELECT D.APPROVER_ID
                FROM APPROVAL_TASKS T
                JOIN APPROVAL_DECISIONS D
                  ON D.TASK_ID = T.ID
                WHERE T.PAYMENT_ID = ?
                  AND T.STATUS = 'APPROVED'
                  AND T.DECIDED_BY = D.APPROVER_ID
                  AND D.DECISION IN (
                      'APPROVED',
                      'OVERRIDE_APPROVED'
                  )
                """,
                (rs, rowNum) -> rs.getLong("APPROVER_ID"),
                paymentId
        );

        if (approvers.size() != 1
                || !approvers.get(0).equals(event.getApprovedBy())) {

            throw new IllegalStateException(
                    "Event approver does not match verified Oracle decision "
                            + paymentId
            );
        }

        /*
         * Lock any existing attempt before deciding
         * whether another Stripe call is allowed.
         */
        List<String> attemptStatuses = jdbcTemplate.query(
                """
                SELECT STATUS
                FROM PAY_PROCESSING_ATTEMPTS
                WHERE PAYMENT_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getString("STATUS"),
                paymentId
        );

        if (!attemptStatuses.isEmpty()) {

            String attemptStatus = attemptStatuses.get(0);

            if ("COMPLETED".equals(attemptStatus)) {
                return ClaimResult.ALREADY_COMPLETED;
            }

            if ("PROCESSING".equals(attemptStatus)) {
                return ClaimResult.ALREADY_PROCESSING;
            }

            /*
             * Any other existing attempt is ambiguous.
             * Require reconciliation rather than
             * automatically calling Stripe again.
             */
            return ClaimResult.REQUIRES_RECONCILIATION;
        }

        /*
         * An unclaimed payment may start processing
         * only when its trusted status is PROCESSING.
         */
        if (!"PROCESSING".equals(payment.status())) {
            throw new IllegalStateException(
                    "Payment " + paymentId
                            + " is not ready for Stripe: "
                            + payment.status()
            );
        }

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO PAY_PROCESSING_ATTEMPTS (
                    PAYMENT_ID,
                    STATUS,
                    ATTEMPT_COUNT
                )
                VALUES (?, 'PROCESSING', 1)
                """,
                paymentId
        );

        if (inserted != 1) {
            throw new IllegalStateException(
                    "Failed to claim payment " + paymentId
            );
        }

        return ClaimResult.CLAIMED;
    }
}

