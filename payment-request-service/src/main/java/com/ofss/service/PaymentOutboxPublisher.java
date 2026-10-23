
package com.ofss.service;

import com.ofss.entity.PaymentRequest;
import com.ofss.entity.PaymentStatus;
import com.ofss.event.PaymentApprovedEvent;
import com.ofss.kafka.PaymentEventProducer;
import com.ofss.repository.PaymentRequestRepository;

import java.sql.Clob;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentOutboxPublisher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PaymentOutboxPublisher.class);

    private final JdbcTemplate jdbcTemplate;
    private final PaymentRequestRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final ObjectMapper objectMapper;

    public PaymentOutboxPublisher(
            JdbcTemplate jdbcTemplate,
            PaymentRequestRepository paymentRepository,
            PaymentEventProducer paymentEventProducer,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.paymentRepository = paymentRepository;
        this.paymentEventProducer = paymentEventProducer;
        this.objectMapper = objectMapper;
    }

    public record PublicationResult(
            Long eventId,
            Long paymentId,
            String outboxStatus,
            boolean alreadyPublished
    ) {
    }

    private record OutboxRecord(
            Long eventId,
            Long paymentId,
            String eventType,
            String payload,
            String status
    ) {
    }

    /*
     * Not scheduled or exposed through an API.
     *
     * A controlled dispatcher will invoke this method
     * after we complete processing safety checks.
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
         * Lock this outbox record so two publishers
         * cannot publish it concurrently.
         */
        List<OutboxRecord> records = jdbcTemplate.query(
                """
                SELECT
                    EVENT_ID,
                    PAYMENT_ID,
                    EVENT_TYPE,
                    PAYLOAD,
                    STATUS
                FROM PAY_PAYMENT_OUTBOX
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

        if (records.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Outbox event not found"
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
                    "Outbox event is not pending"
            );
        }

        if (!"payment.approved".equals(record.eventType())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unsupported outbox event type"
            );
        }

        /*
         * Verify that the payment is currently
         * in PROCESSING before publishing.
         */
        PaymentRequest payment =
                paymentRepository.findById(record.paymentId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Payment not found"
                                )
                        );

        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment must be PROCESSING before publication"
            );
        }

        PaymentApprovedEvent event =
                objectMapper.readValue(
                        record.payload(),
                        PaymentApprovedEvent.class
                );

        /*
         * Reject a payload that does not match
         * the trusted Oracle payment record.
         */
        if (!record.paymentId().equals(event.getPaymentId())
                || !payment.getInvoiceId().equals(event.getInvoiceId())
                || !payment.getSupplierId().equals(event.getSupplierId())
                || payment.getPaymentAmount().compareTo(
                        event.getPaymentAmount()
                   ) != 0
                || !payment.getApprovedAt().equals(
                        event.getApprovedAt()
                   )
                || event.getApprovedBy() == null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Outbox payload does not match the payment"
            );
        }

        /*
         * Wait for a Kafka broker acknowledgment.
         * If publishing fails, the transaction rolls
         * back and the event remains PENDING.
         */
        try {
            paymentEventProducer
                    .publishPaymentApproved(event)
                    .get(15, TimeUnit.SECONDS);

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
         * Mark the event published only after Kafka
         * acknowledges the message.
         */
        int updated = jdbcTemplate.update(
                """
                UPDATE PAY_PAYMENT_OUTBOX
                SET
                    STATUS = 'PUBLISHED',
                    PUBLISHED_AT = SYSTIMESTAMP,
                    ATTEMPT_COUNT = ATTEMPT_COUNT + 1,
                    LAST_ERROR = NULL
                WHERE EVENT_ID = ?
                  AND STATUS = 'PENDING'
                """,
                eventId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Unable to mark outbox event as published"
            );
        }

        LOGGER.info(
                "Published outbox event ID {} for payment ID {}",
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
