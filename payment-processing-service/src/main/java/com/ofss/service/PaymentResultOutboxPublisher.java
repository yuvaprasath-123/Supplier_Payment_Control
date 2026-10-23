
package com.ofss.service;

import com.ofss.event.PaymentCompletedEvent;
import com.ofss.event.PaymentFailedEvent;

import java.math.BigDecimal;
import java.sql.Clob;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentResultOutboxPublisher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PaymentResultOutboxPublisher.class
            );

    private static final String COMPLETED_TOPIC =
            "payment.completed";

    private static final String FAILED_TOPIC =
            "payment.failed";

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentResultOutboxPublisher(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public record PublicationResult(
            Long eventId,
            Long paymentId,
            String status,
            boolean alreadyPublished
    ) {}

    private record OutboxRecord(
            Long eventId,
            Long paymentId,
            String eventType,
            String payload,
            String status
    ) {}

    private record ProcessingAttempt(
            String status,
            String stripePaymentIntentId
    ) {}

    /*
     * Publish one existing payment-result outbox event.
     *
     * Supported topics:
     * - payment.completed
     * - payment.failed
     *
     * This method does not call Stripe.
     */
    @Transactional
    public PublicationResult publishEvent(Long eventId) {

        if (eventId == null || eventId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid outbox event ID is required"
            );
        }

        /*
         * Lock the outbox row so concurrent dispatchers
         * cannot publish the same row simultaneously.
         */
        List<OutboxRecord> records = jdbcTemplate.query(
                """
                SELECT
                    EVENT_ID,
                    PAYMENT_ID,
                    EVENT_TYPE,
                    PAYLOAD,
                    STATUS
                FROM PAY_PROCESSING_RESULT_OUTBOX
                WHERE EVENT_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> {

                    Clob clob = rs.getClob("PAYLOAD");

                    if (clob == null) {
                        throw new IllegalStateException(
                                "Outbox payload is missing"
                        );
                    }

                    String payload = clob.getSubString(
                            1,
                            Math.toIntExact(clob.length())
                    );

                    return new OutboxRecord(
                            rs.getLong("EVENT_ID"),
                            rs.getLong("PAYMENT_ID"),
                            rs.getString("EVENT_TYPE"),
                            payload,
                            rs.getString("STATUS")
                    );
                },
                eventId
        );

        if (records.size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Processing result outbox event not found"
            );
        }

        OutboxRecord record = records.get(0);

        if ("PUBLISHED".equals(record.status())) {
            return new PublicationResult(
                    record.eventId(),
                    record.paymentId(),
                    "PUBLISHED",
                    true
            );
        }

        if (!"PENDING".equals(record.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Processing result outbox event is not pending"
            );
        }

        String eventType = record.eventType();

        if (!COMPLETED_TOPIC.equals(eventType)
                && !FAILED_TOPIC.equals(eventType)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unsupported processing result event type"
            );
        }

        /*
         * Read the actual processing result.
         * Never trust the outbox payload alone.
         */
        List<ProcessingAttempt> attempts = jdbcTemplate.query(
                """
                SELECT
                    STATUS,
                    STRIPE_PAYMENT_INTENT_ID
                FROM PAY_PROCESSING_ATTEMPTS
                WHERE PAYMENT_ID = ?
                """,
                (rs, rowNum) -> new ProcessingAttempt(
                        rs.getString("STATUS"),
                        rs.getString(
                                "STRIPE_PAYMENT_INTENT_ID"
                        )
                ),
                record.paymentId()
        );

        if (attempts.size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Exactly one processing attempt is required"
            );
        }

        ProcessingAttempt attempt = attempts.get(0);

        /*
         * Read the trusted Oracle payment amount.
         */
        List<BigDecimal> amounts = jdbcTemplate.query(
                """
                SELECT PAYMENT_AMOUNT
                FROM PAY_PAYMENT_REQUESTS
                WHERE PAYMENT_ID = ?
                """,
                (rs, rowNum) ->
                        rs.getBigDecimal("PAYMENT_AMOUNT"),
                record.paymentId()
        );

        if (amounts.size() != 1
                || amounts.get(0) == null
                || amounts.get(0).signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Valid Oracle payment amount is required"
            );
        }

        BigDecimal trustedAmount = amounts.get(0);

        Object kafkaEvent;

        /*
         * Successful payment.
         */
        if (COMPLETED_TOPIC.equals(eventType)) {

            if (!"COMPLETED".equals(attempt.status())
                    || attempt.stripePaymentIntentId() == null
                    || attempt.stripePaymentIntentId().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A completed Stripe attempt is required"
                );
            }

            PaymentCompletedEvent completedEvent =
                    objectMapper.readValue(
                            record.payload(),
                            PaymentCompletedEvent.class
                    );

            if (completedEvent.getPaymentId() == null
                    || !record.paymentId().equals(
                            completedEvent.getPaymentId()
                    )
                    || completedEvent.getPaymentAmount() == null
                    || trustedAmount.compareTo(
                            completedEvent.getPaymentAmount()
                    ) != 0
                    || completedEvent.getCompletedAt() == null
                    || !attempt.stripePaymentIntentId().equals(
                            completedEvent.getTransactionReference()
                    )) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Completion event does not match Oracle"
                );
            }

            kafkaEvent = completedEvent;

        } else {

            /*
             * Confirmed failed payment.
             *
             * A failure event is only publishable if
             * Oracle already records the attempt FAILED.
             *
             * Reconciliation must have confirmed
             * the Stripe cancellation beforehand.
             */
            if (!"FAILED".equals(attempt.status())
                    || attempt.stripePaymentIntentId() == null
                    || attempt.stripePaymentIntentId().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A confirmed failed attempt is required"
                );
            }

            PaymentFailedEvent failedEvent =
                    objectMapper.readValue(
                            record.payload(),
                            PaymentFailedEvent.class
                    );

            if (failedEvent.getPaymentId() == null
                    || !record.paymentId().equals(
                            failedEvent.getPaymentId()
                    )
                    || failedEvent.getPaymentAmount() == null
                    || trustedAmount.compareTo(
                            failedEvent.getPaymentAmount()
                    ) != 0
                    || failedEvent.getReason() == null
                    || failedEvent.getReason().isBlank()
                    || failedEvent.getFailedAt() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Failure event does not match Oracle"
                );
            }

            kafkaEvent = failedEvent;
        }

        /*
         * Wait for Kafka broker acknowledgment before
         * marking the outbox record PUBLISHED.
         *
         * A Kafka acknowledgment and Oracle commit
         * are separate operations. A retry may deliver
         * the event again, so consumers must remain
         * idempotent.
         */
        try {

            kafkaTemplate.send(
                    eventType,
                    String.valueOf(record.paymentId()),
                    kafkaEvent
            ).get(15, TimeUnit.SECONDS);

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Kafka publication interrupted",
                    exception
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Kafka publication failed",
                    exception
            );
        }

        /*
         * Mark published only after Kafka confirms
         * that it accepted the event.
         */
        int updated = jdbcTemplate.update(
                """
                UPDATE PAY_PROCESSING_RESULT_OUTBOX
                SET
                    STATUS = 'PUBLISHED',
                    PUBLISHED_AT = SYSTIMESTAMP,
                    ATTEMPT_COUNT = ATTEMPT_COUNT + 1,
                    LAST_ERROR = NULL
                WHERE EVENT_ID = ?
                  AND STATUS = 'PENDING'
                """,
                record.eventId()
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Unable to mark processing result as published"
            );
        }

        LOGGER.info(
                "Published {}: eventId={}, paymentId={}",
                eventType,
                record.eventId(),
                record.paymentId()
        );

        return new PublicationResult(
                record.eventId(),
                record.paymentId(),
                "PUBLISHED",
                false
        );
    }
}

