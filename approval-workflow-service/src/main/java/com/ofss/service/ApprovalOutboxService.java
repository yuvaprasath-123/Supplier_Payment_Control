
package com.ofss.service;

import com.ofss.event.ApprovalDecidedEvent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalOutboxService {

    private final JdbcTemplate jdbcTemplate;

    public ApprovalOutboxService(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Save an approval event to Oracle.
     *
     * Must execute inside the same transaction
     * that creates the approval decision.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            ApprovalDecidedEvent event
    ) {

        if (event == null
                || event.approvalDecisionId() == null
                || event.paymentId() == null
                || event.approvalDecisionId() <= 0
                || event.paymentId() <= 0) {

            throw new IllegalArgumentException(
                    "Valid approval decision and payment IDs are required"
            );
        }

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO PAY_APPROVAL_OUTBOX (
                    APPROVAL_DECISION_ID,
                    PAYMENT_ID,
                    STATUS,
                    ATTEMPT_COUNT
                )
                VALUES (?, ?, 'PENDING', 0)
                """,
                event.approvalDecisionId(),
                event.paymentId()
        );

        if (inserted != 1) {
            throw new IllegalStateException(
                    "Approval outbox insertion failed"
            );
        }
    }
}
