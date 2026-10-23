
package com.ofss.service;

import com.ofss.event.PaymentCompletedEvent;
import com.ofss.event.PaymentFailedEvent;
import com.stripe.model.PaymentIntent;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentProcessingAttemptResultService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String stripeCurrency;

    public PaymentProcessingAttemptResultService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${stripe.currency:inr}") String stripeCurrency
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.stripeCurrency = stripeCurrency;
    }

    public record ResultOutboxEntry(
            Long paymentId,
            Long outboxEventId,
            String eventType
    ) {}

    private record PaymentRow(
            BigDecimal amount,
            String status
    ) {}

    private record AttemptRow(
            String status,
            String stripePaymentIntentId
    ) {}

    /*
     * Record normal successful Stripe processing.
     */
    @Transactional
    public ResultOutboxEntry recordCompleted(
            Long paymentId,
            String stripePaymentIntentId,
            BigDecimal paymentAmount
    ) {

        validateCompletionInputs(
                paymentId,
                stripePaymentIntentId,
                paymentAmount
        );

        int updated = jdbcTemplate.update(
                """
                UPDATE PAY_PROCESSING_ATTEMPTS
                SET
                    STATUS = 'COMPLETED',
                    STRIPE_PAYMENT_INTENT_ID = ?,
                    UPDATED_AT = SYSTIMESTAMP,
                    LAST_ERROR = NULL
                WHERE PAYMENT_ID = ?
                  AND STATUS = 'PROCESSING'
                """,
                stripePaymentIntentId,
                paymentId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Cannot complete processing attempt: " + paymentId
            );
        }

        Long outboxEventId = createCompletionEvent(
                paymentId,
                stripePaymentIntentId,
                paymentAmount
        );

        return new ResultOutboxEntry(
                paymentId,
                outboxEventId,
                "payment.completed"
        );
    }

    /*
     * Complete reconciliation after Stripe success.
     *
     * This method never creates or confirms a payment.
     */
    @Transactional
    public ResultOutboxEntry recordReconciledCompletion(
            Long paymentId,
            PaymentIntent intent
    ) {

        validateSuccessfulIntent(paymentId, intent);

        PaymentRow payment = lockPayment(paymentId);

        validateProcessingPayment(paymentId, payment);

        validateStripeAmount(intent, payment.amount());

        AttemptRow attempt = lockAttempt(paymentId);

        if (!"REQUIRES_RECONCILIATION".equals(
                attempt.status()
        )) {
            throw new IllegalStateException(
                    "Processing attempt is not eligible for recovery: "
                            + paymentId
            );
        }

        validateStoredIntentId(attempt, intent);

        ensureNoExistingResultEvent(paymentId);

        int updated = jdbcTemplate.update(
                """
                UPDATE PAY_PROCESSING_ATTEMPTS
                SET
                    STATUS = 'COMPLETED',
                    UPDATED_AT = SYSTIMESTAMP,
                    LAST_ERROR = NULL
                WHERE PAYMENT_ID = ?
                  AND STATUS = 'REQUIRES_RECONCILIATION'
                  AND STRIPE_PAYMENT_INTENT_ID = ?
                """,
                paymentId,
                intent.getId()
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Reconciliation status update failed: "
                            + paymentId
            );
        }

        Long eventId = createCompletionEvent(
                paymentId,
                intent.getId(),
                payment.amount()
        );

        return new ResultOutboxEntry(
                paymentId,
                eventId,
                "payment.completed"
        );
    }

    /*
     * NEW: Record a confirmed terminal Stripe failure.
     *
     * Only an existing, verified, canceled PaymentIntent
     * is eligible.
     *
     * A timeout, network exception, or unknown status
     * must never be treated as a confirmed failure.
     *
     * This method does not contact Stripe.
     * The caller must retrieve the existing PaymentIntent.
     */
    @Transactional
    public ResultOutboxEntry recordFailed(
            Long paymentId,
            PaymentIntent intent
    ) {

        validateIntentIdentity(paymentId, intent);

        if (!"canceled".equalsIgnoreCase(
                intent.getStatus()
        )) {
            throw new IllegalStateException(
                    "Stripe failure is not confirmed. Status: "
                            + intent.getStatus()
            );
        }

        PaymentRow payment = lockPayment(paymentId);

        validateProcessingPayment(paymentId, payment);

        validateStripeAmount(intent, payment.amount());

        /*
         * Lock the processing attempt after locking
         * the payment, maintaining a consistent order.
         */
        AttemptRow attempt = lockAttempt(paymentId);

        if (!"REQUIRES_RECONCILIATION".equals(
                attempt.status()
        )) {
            throw new IllegalStateException(
                    "Only reconciled attempts can be marked FAILED: "
                            + paymentId
            );
        }

        validateStoredIntentId(attempt, intent);

        /*
         * Never publish a failure if a completion
         * or another result event already exists.
         */
        ensureNoExistingResultEvent(paymentId);

        String reason =
                "Stripe PaymentIntent canceled: " + intent.getId();

        int updated = jdbcTemplate.update(
                """
                UPDATE PAY_PROCESSING_ATTEMPTS
                SET
                    STATUS = 'FAILED',
                    UPDATED_AT = SYSTIMESTAMP,
                    LAST_ERROR = ?
                WHERE PAYMENT_ID = ?
                  AND STATUS = 'REQUIRES_RECONCILIATION'
                  AND STRIPE_PAYMENT_INTENT_ID = ?
                """,
                reason,
                paymentId,
                intent.getId()
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Unable to record confirmed failure: "
                            + paymentId
            );
        }

        String eventJson = objectMapper.writeValueAsString(
                new PaymentFailedEvent(
                        paymentId,
                        payment.amount(),
                        reason,
                        LocalDateTime.now()
                )
        );

        Long outboxEventId = insertEvent(
                paymentId,
                "payment.failed",
                eventJson
        );

        return new ResultOutboxEntry(
                paymentId,
                outboxEventId,
                "payment.failed"
        );
    }

    /*
     * Mark an uncertain Stripe outcome for reconciliation.
     */
    @Transactional
    public void recordRequiresReconciliation(
            Long paymentId,
            String stripePaymentIntentId,
            String reason
    ) {

        if (paymentId == null || paymentId <= 0) {
            throw new IllegalArgumentException(
                    "Valid payment ID is required"
            );
        }

        String safeReason = reason == null
                ? "Stripe outcome requires reconciliation"
                : reason.substring(
                        0,
                        Math.min(reason.length(), 1000)
                );

        int updated = jdbcTemplate.update(
                """
                UPDATE PAY_PROCESSING_ATTEMPTS
                SET
                    STATUS = 'REQUIRES_RECONCILIATION',
                    STRIPE_PAYMENT_INTENT_ID =
                        COALESCE(?, STRIPE_PAYMENT_INTENT_ID),
                    UPDATED_AT = SYSTIMESTAMP,
                    LAST_ERROR = ?
                WHERE PAYMENT_ID = ?
                  AND STATUS = 'PROCESSING'
                """,
                stripePaymentIntentId,
                safeReason,
                paymentId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Cannot mark attempt for reconciliation: "
                            + paymentId
            );
        }
    }

    private void validateCompletionInputs(
            Long paymentId,
            String stripePaymentIntentId,
            BigDecimal paymentAmount
    ) {

        if (paymentId == null || paymentId <= 0
                || stripePaymentIntentId == null
                || stripePaymentIntentId.isBlank()
                || paymentAmount == null
                || paymentAmount.signum() <= 0) {

            throw new IllegalArgumentException(
                    "Valid payment ID, Stripe ID and amount are required"
            );
        }
    }

    private void validateSuccessfulIntent(
            Long paymentId,
            PaymentIntent intent
    ) {

        validateIntentIdentity(paymentId, intent);

        if (!"succeeded".equalsIgnoreCase(
                intent.getStatus()
        )) {
            throw new IllegalStateException(
                    "Stripe payment has not succeeded"
            );
        }
    }

    private void validateIntentIdentity(
            Long paymentId,
            PaymentIntent intent
    ) {

        if (paymentId == null || paymentId <= 0) {
            throw new IllegalArgumentException(
                    "Valid payment ID is required"
            );
        }

        if (intent == null
                || intent.getId() == null
                || intent.getId().isBlank()) {
            throw new IllegalArgumentException(
                    "Existing Stripe PaymentIntent is required"
            );
        }

        if (intent.getCurrency() == null
                || !stripeCurrency.equalsIgnoreCase(
                        intent.getCurrency()
                )) {
            throw new IllegalStateException(
                    "Stripe currency does not match configuration"
            );
        }

        Map<String, String> metadata = intent.getMetadata();

        if (metadata == null
                || !paymentId.toString().equals(
                        metadata.get("paymentId")
                )) {
            throw new IllegalStateException(
                    "Stripe payment identity mismatch"
            );
        }
    }

    private PaymentRow lockPayment(Long paymentId) {

        List<PaymentRow> payments = jdbcTemplate.query(
                """
                SELECT PAYMENT_AMOUNT, STATUS
                FROM PAY_PAYMENT_REQUESTS
                WHERE PAYMENT_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new PaymentRow(
                        rs.getBigDecimal("PAYMENT_AMOUNT"),
                        rs.getString("STATUS")
                ),
                paymentId
        );

        if (payments.size() != 1) {
            throw new IllegalStateException(
                    "Oracle payment not found: " + paymentId
            );
        }

        return payments.get(0);
    }

    private void validateProcessingPayment(
            Long paymentId,
            PaymentRow payment
    ) {

        if (!"PROCESSING".equals(payment.status())) {
            throw new IllegalStateException(
                    "Payment is not awaiting completion: "
                            + paymentId
            );
        }

        if (payment.amount() == null
                || payment.amount().signum() <= 0) {
            throw new IllegalStateException(
                    "Invalid Oracle payment amount"
            );
        }
    }

    private void validateStripeAmount(
            PaymentIntent intent,
            BigDecimal oracleAmount
    ) {

        long expectedMinorUnits = oracleAmount
                .movePointRight(2)
                .longValueExact();

        if (intent.getAmount() == null
                || intent.getAmount() != expectedMinorUnits) {
            throw new IllegalStateException(
                    "Stripe amount does not match Oracle payment"
            );
        }

        /*
         * Only successful payments must have received
         * the complete expected amount.
         */
        if ("succeeded".equalsIgnoreCase(
                intent.getStatus()
        )) {

            if (intent.getAmountReceived() == null
                    || intent.getAmountReceived()
                            != expectedMinorUnits) {
                throw new IllegalStateException(
                        "Stripe received amount does not match Oracle"
                );
            }
        }

        /*
         * For a canceled intent, require no received funds.
         */
        if ("canceled".equalsIgnoreCase(
                intent.getStatus()
        )) {

            if (intent.getAmountReceived() == null
                    || intent.getAmountReceived() != 0L) {
                throw new IllegalStateException(
                        "Canceled Stripe intent has a nonzero "
                                + "or unknown received amount"
                );
            }
        }
    }

    private AttemptRow lockAttempt(Long paymentId) {

        List<AttemptRow> attempts = jdbcTemplate.query(
                """
                SELECT STATUS, STRIPE_PAYMENT_INTENT_ID
                FROM PAY_PROCESSING_ATTEMPTS
                WHERE PAYMENT_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new AttemptRow(
                        rs.getString("STATUS"),
                        rs.getString("STRIPE_PAYMENT_INTENT_ID")
                ),
                paymentId
        );

        if (attempts.size() != 1) {
            throw new IllegalStateException(
                    "Processing attempt not found: " + paymentId
            );
        }

        return attempts.get(0);
    }

    private void validateStoredIntentId(
            AttemptRow attempt,
            PaymentIntent intent
    ) {

        if (attempt.stripePaymentIntentId() == null
                || !intent.getId().equals(
                        attempt.stripePaymentIntentId()
                )) {
            throw new IllegalStateException(
                    "Stripe PaymentIntent ID does not match Oracle"
            );
        }
    }

    private void ensureNoExistingResultEvent(
            Long paymentId
    ) {

        Integer existingEvents = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM PAY_PROCESSING_RESULT_OUTBOX
                WHERE PAYMENT_ID = ?
                  AND EVENT_TYPE IN (
                      'payment.completed',
                      'payment.failed'
                  )
                """,
                Integer.class,
                paymentId
        );

        if (existingEvents == null || existingEvents != 0) {
            throw new IllegalStateException(
                    "Payment result event already exists for payment "
                            + paymentId
            );
        }
    }

    private Long createCompletionEvent(
            Long paymentId,
            String stripePaymentIntentId,
            BigDecimal paymentAmount
    ) {

        String eventJson = objectMapper.writeValueAsString(
                new PaymentCompletedEvent(
                        paymentId,
                        paymentAmount,
                        stripePaymentIntentId,
                        LocalDateTime.now()
                )
        );

        return insertEvent(
                paymentId,
                "payment.completed",
                eventJson
        );
    }

    private Long insertEvent(
            Long paymentId,
            String eventType,
            String eventJson
    ) {

        Long outboxEventId = jdbcTemplate.execute(
                (ConnectionCallback<Long>) connection ->
                        insertResultOutbox(
                                connection,
                                paymentId,
                                eventType,
                                eventJson
                        )
        );

        if (outboxEventId == null || outboxEventId <= 0) {
            throw new IllegalStateException(
                    "Failed to create payment result outbox record"
            );
        }

        return outboxEventId;
    }

    private Long insertResultOutbox(
            Connection connection,
            Long paymentId,
            String eventType,
            String eventJson
    ) throws SQLException {

        String sql = """
                BEGIN
                    INSERT INTO PAY_PROCESSING_RESULT_OUTBOX (
                        PAYMENT_ID,
                        EVENT_TYPE,
                        PAYLOAD,
                        STATUS,
                        ATTEMPT_COUNT
                    )
                    VALUES (?, ?, ?, 'PENDING', 0)
                    RETURNING EVENT_ID INTO ?;
                END;
                """;

        try (CallableStatement statement =
                     connection.prepareCall(sql)) {

            statement.setLong(1, paymentId);
            statement.setString(2, eventType);
            statement.setString(3, eventJson);

            statement.registerOutParameter(
                    4,
                    java.sql.Types.NUMERIC
            );

            statement.execute();

            long id = statement.getLong(4);

            if (statement.wasNull()) {
                throw new SQLException(
                        "Oracle did not return an outbox ID"
                );
            }

            return id;
        }
    }
}

