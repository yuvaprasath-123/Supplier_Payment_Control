package com.ofss.service;

import com.ofss.event.ApprovalDecidedEvent;
import com.ofss.kafka.ApprovalDecisionProducer;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalOutboxPublisher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    ApprovalOutboxPublisher.class
            );

    private final JdbcTemplate jdbcTemplate;

    private final ApprovalDecisionProducer producer;


    public ApprovalOutboxPublisher(
            JdbcTemplate jdbcTemplate,
            ApprovalDecisionProducer producer
    ) {

        this.jdbcTemplate =
                jdbcTemplate;

        this.producer =
                producer;
    }


    /*
     * =========================================================
     * PUBLISH ONE OUTBOX EVENT
     * =========================================================
     *
     * Processing model:
     *
     * 1. Lock the outbox row.
     * 2. Verify that it is still PENDING.
     * 3. Load the persisted approval decision.
     * 4. Publish the trusted decision to Kafka.
     * 5. Mark the outbox row PUBLISHED.
     *
     * If publication throws an exception, the row remains
     * PENDING and can be retried by the dispatcher.
     *
     * Downstream consumers must remain idempotent using
     * approvalDecisionId.
     */
    @Transactional
    public void publish(
            Long eventId
    ) {

        if (eventId == null
                || eventId <= 0) {

            throw new IllegalArgumentException(
                    "Valid outbox event ID is required"
            );
        }


        /*
         * Lock exactly one pending outbox event.
         *
         * FOR UPDATE prevents two dispatcher executions
         * from publishing the same row concurrently.
         */
        List<OutboxEvent> events =
                jdbcTemplate.query(
                        """
                        SELECT
                            O.EVENT_ID,
                            O.APPROVAL_DECISION_ID,
                            O.PAYMENT_ID,
                            O.STATUS
                        FROM PAY_APPROVAL_OUTBOX O
                        WHERE O.EVENT_ID = ?
                        FOR UPDATE OF O.STATUS
                        """,

                        (rs, rowNum) ->
                                new OutboxEvent(
                                        rs.getLong(
                                                "EVENT_ID"
                                        ),
                                        rs.getLong(
                                                "APPROVAL_DECISION_ID"
                                        ),
                                        rs.getLong(
                                                "PAYMENT_ID"
                                        ),
                                        rs.getString(
                                                "STATUS"
                                        )
                                ),

                        eventId
                );


        /*
         * Event might have been removed already.
         */
        if (events.isEmpty()) {

            LOGGER.warn(
                    "Approval outbox event {} no longer exists",
                    eventId
            );

            return;
        }


        OutboxEvent outbox =
                events.get(0);


        /*
         * Idempotency:
         *
         * Already-published rows must not be
         * published again.
         */
        if (!"PENDING".equals(
                outbox.status()
        )) {

            LOGGER.debug(
                    "Skipping approval outbox event {} "
                    + "because status is {}",
                    eventId,
                    outbox.status()
            );

            return;
        }


        /*
         * =====================================================
         * LOAD TRUSTED APPROVAL DECISION
         * =====================================================
         *
         * Do not construct the Kafka event only from the
         * outbox row.
         *
         * Reload the persisted approval decision and verify
         * both decision ID and payment ID.
         */
        List<ApprovalDecidedEvent> decisions =
                jdbcTemplate.query(
                        """
                        SELECT
                            D.ID,
                            D.TASK_ID,
                            D.PAYMENT_ID,
                            D.APPROVER_ID,
                            D.DECISION,
                            D.DECIDED_AT
                        FROM APPROVAL_DECISIONS D
                        WHERE D.ID = ?
                          AND D.PAYMENT_ID = ?
                        """,

                        (rs, rowNum) -> {

                            OffsetDateTime decidedAt =
                                    rs.getObject(
                                            "DECIDED_AT",
                                            OffsetDateTime.class
                                    );


                            return new ApprovalDecidedEvent(
                                    rs.getLong(
                                            "PAYMENT_ID"
                                    ),

                                    rs.getLong(
                                            "TASK_ID"
                                    ),

                                    rs.getLong(
                                            "ID"
                                    ),

                                    rs.getLong(
                                            "APPROVER_ID"
                                    ),

                                    rs.getString(
                                            "DECISION"
                                    ),

                                    decidedAt
                            );
                        },

                        outbox.approvalDecisionId(),
                        outbox.paymentId()
                );


        /*
         * There must be exactly one trusted decision.
         */
        if (decisions.size() != 1) {

            recordFailure(
                    eventId,
                    "Expected exactly one matching approval decision"
            );


            LOGGER.error(
                    "Approval decision missing or mismatched "
                    + "for outbox event {}",
                    eventId
            );

            return;
        }


        ApprovalDecidedEvent event =
                decisions.get(0);


        /*
         * =====================================================
         * PUBLISH
         * =====================================================
         */
        try {

            /*
             * IMPORTANT:
             *
             * Do not call:
             *
             * producer.publish(event).get(...)
             *
             * because ApprovalDecisionProducer.publish(...)
             * may return void.
             *
             * This works with the existing producer contract.
             */
            producer.publish(
                    event
            );


            /*
             * Publication call completed without throwing.
             *
             * Mark this outbox event as published.
             */
            int updated =
                    jdbcTemplate.update(
                            """
                            UPDATE PAY_APPROVAL_OUTBOX
                            SET STATUS = 'PUBLISHED',
                                ATTEMPT_COUNT =
                                    ATTEMPT_COUNT + 1,
                                PUBLISHED_AT =
                                    SYSTIMESTAMP,
                                LAST_ERROR = NULL
                            WHERE EVENT_ID = ?
                              AND STATUS = 'PENDING'
                            """,

                            eventId
                    );


            /*
             * Exactly one pending row must be updated.
             */
            if (updated != 1) {

                throw new IllegalStateException(
                        "Outbox publication status update failed"
                );
            }


            LOGGER.info(
                    "Approval outbox event {} published; "
                    + "approvalDecisionId={}, paymentId={}",
                    eventId,
                    event.approvalDecisionId(),
                    event.paymentId()
            );


        } catch (Exception exception) {

            /*
             * Do NOT mark the row PUBLISHED.
             *
             * The dispatcher can retry the PENDING event.
             *
             * Only store the exception class name to avoid
             * persisting sensitive broker/configuration data.
             */
            String safeReason =
                    exception
                            .getClass()
                            .getSimpleName();


            recordFailure(
                    eventId,
                    safeReason
            );


            LOGGER.error(
                    "Kafka publication failed for "
                    + "approval outbox event {}; "
                    + "event remains pending",
                    eventId,
                    exception
            );
        }
    }


    /*
     * =========================================================
     * RECORD FAILED PUBLICATION ATTEMPT
     * =========================================================
     */

    private void recordFailure(
            Long eventId,
            String reason
    ) {

        String safeReason =
                reason == null
                        || reason.isBlank()

                        ? "Unknown publication failure"

                        : reason;


        /*
         * Keep database error text bounded.
         */
        if (safeReason.length() > 200) {

            safeReason =
                    safeReason.substring(
                            0,
                            200
                    );
        }


        jdbcTemplate.update(
                """
                UPDATE PAY_APPROVAL_OUTBOX
                SET ATTEMPT_COUNT =
                        ATTEMPT_COUNT + 1,
                    LAST_ERROR = ?
                WHERE EVENT_ID = ?
                  AND STATUS = 'PENDING'
                """,

                safeReason,
                eventId
        );
    }


    /*
     * =========================================================
     * INTERNAL OUTBOX PROJECTION
     * =========================================================
     */

    private record OutboxEvent(
            Long eventId,
            Long approvalDecisionId,
            Long paymentId,
            String status
    ) {
    }
}
