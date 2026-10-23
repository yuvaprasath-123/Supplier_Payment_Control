
package com.ofss.service;

import com.ofss.entity.PaymentRequest;
import com.ofss.entity.PaymentStatus;
import com.ofss.entity.PaymentStatusHistory;
import com.ofss.event.PaymentApprovedEvent;
import com.ofss.repository.PaymentRequestRepository;
import com.ofss.repository.PaymentStatusHistoryRepository;

import java.sql.CallableStatement;
import java.sql.Connection;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentProcessingDispatchService {

    private final PaymentRequestRepository paymentRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PaymentProcessingDispatchService(
            PaymentRequestRepository paymentRepository,
            PaymentStatusHistoryRepository historyRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.historyRepository = historyRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public record DispatchResult(
            Long paymentId,
            String paymentStatus,
            Long outboxEventId
    ) {
    }

    @Transactional
    public DispatchResult prepareForProcessing(Long paymentId) {

        if (paymentId == null || paymentId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid payment ID is required"
            );
        }

        /*
         * Lock the payment record.
         * Prevent concurrent dispatch attempts.
         */
        PaymentRequest payment =
                paymentRepository.findByIdForUpdate(paymentId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Payment not found"
                                )
                        );

        /*
         * Only an APPROVED payment may
         * enter the PROCESSING state.
         */
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only APPROVED payments can enter processing"
            );
        }

        if (payment.getApprovedAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Approved payment timestamp is missing"
            );
        }

        if (payment.getPaymentAmount() == null
                || payment.getPaymentAmount().signum() <= 0
                || payment.getInvoiceId() == null
                || payment.getSupplierId() == null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment is missing required processing information"
            );
        }

        /*
         * Get the approver from verified
         * Oracle approval records.
         */
        List<Long> verifiedApprovers = jdbcTemplate.query(
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

        if (verifiedApprovers.size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Expected exactly one verified approval decision"
            );
        }

        Long approvedBy = verifiedApprovers.get(0);

        /*
         * Construct the existing Kafka event
         * using trusted database values.
         */
        PaymentApprovedEvent event =
                new PaymentApprovedEvent(
                        payment.getId(),
                        payment.getInvoiceId(),
                        payment.getSupplierId(),
                        payment.getPaymentAmount(),
                        approvedBy,
                        payment.getApprovedAt()
                );

        String eventJson =
                objectMapper.writeValueAsString(event);

        /*
         * Update payment status.
         */
        payment.setStatus(PaymentStatus.PROCESSING);

        paymentRepository.saveAndFlush(payment);

        /*
         * Record the status transition.
         */
        PaymentStatusHistory history =
                new PaymentStatusHistory();

        history.setPaymentId(paymentId);
        history.setPreviousStatus(PaymentStatus.APPROVED);
        history.setNewStatus(PaymentStatus.PROCESSING);
        history.setChangedBy(approvedBy);
        history.setReason(
                "Approved payment prepared for Kafka processing"
        );

        historyRepository.save(history);

        /*
         * Insert the outbox event into Oracle.
         *
         * Use JdbcTemplate.execute() with a
         * ConnectionCallback, not queryForObject().
         *
         * All writes remain in the same
         * Spring-managed transaction.
         */
        Long outboxEventId = jdbcTemplate.execute(
                (ConnectionCallback<Long>) connection ->
                        insertOutboxEvent(
                                connection,
                                paymentId,
                                eventJson
                        )
        );

        if (outboxEventId == null || outboxEventId <= 0) {
            throw new IllegalStateException(
                    "Unable to create payment outbox event"
            );
        }

        return new DispatchResult(
                paymentId,
                PaymentStatus.PROCESSING.name(),
                outboxEventId
        );
    }

    /*
     * Oracle identity column:
     * retrieve EVENT_ID with RETURNING INTO.
     */
    private Long insertOutboxEvent(
            Connection connection,
            Long paymentId,
            String eventJson
    ) throws java.sql.SQLException {

        String sql = """
                BEGIN
                    INSERT INTO PAY_PAYMENT_OUTBOX (
                        PAYMENT_ID,
                        EVENT_TYPE,
                        PAYLOAD,
                        STATUS,
                        ATTEMPT_COUNT
                    )
                    VALUES (
                        ?,
                        'payment.approved',
                        ?,
                        'PENDING',
                        0
                    )
                    RETURNING EVENT_ID INTO ?;
                END;
                """;

        try (CallableStatement statement =
                     connection.prepareCall(sql)) {

            statement.setLong(1, paymentId);

            statement.setString(2, eventJson);

            statement.registerOutParameter(
                    3,
                    java.sql.Types.NUMERIC
            );

            statement.execute();

            long eventId = statement.getLong(3);

            if (statement.wasNull()) {
                throw new java.sql.SQLException(
                        "Oracle did not return an outbox event ID"
                );
            }

            return eventId;
        }
    }
}
