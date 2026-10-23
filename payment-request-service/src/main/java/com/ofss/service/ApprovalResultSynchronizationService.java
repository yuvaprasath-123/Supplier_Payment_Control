
package com.ofss.service;

import com.ofss.entity.PaymentRequest;
import com.ofss.entity.PaymentStatus;
import com.ofss.entity.PaymentStatusHistory;
import com.ofss.repository.PaymentRequestRepository;
import com.ofss.repository.PaymentStatusHistoryRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApprovalResultSynchronizationService {

    private final JdbcTemplate jdbcTemplate;
    private final PaymentRequestRepository payments;
    private final PaymentStatusHistoryRepository historyRepository;
    private final PaymentProcessingDispatchService processingDispatchService;

    // NEW: Reuse the existing transactional audit outbox.
    private final PaymentAuditOutboxService auditOutboxService;

    public ApprovalResultSynchronizationService(
            JdbcTemplate jdbcTemplate,
            PaymentRequestRepository payments,
            PaymentStatusHistoryRepository historyRepository,
            PaymentProcessingDispatchService processingDispatchService,
            PaymentAuditOutboxService auditOutboxService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.payments = payments;
        this.historyRepository = historyRepository;
        this.processingDispatchService = processingDispatchService;
        this.auditOutboxService = auditOutboxService;
    }

    public record SynchronizationResult(
            Long paymentId,
            String paymentStatus,
            Long approvalTaskId,
            Long approvalDecisionId,
            boolean alreadySynchronized
    ) {}

    private record VerifiedDecision(
            Long taskId,
            Long decisionId,
            Long approverId,
            String decision,
            String remark,
            LocalDateTime decidedAt
    ) {}

    @Transactional
    public SynchronizationResult synchronize(
            Long paymentId,
            Long expectedDecisionId
    ) {

        if (paymentId == null || paymentId <= 0) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Valid payment ID is required"
            );
        }

        if (expectedDecisionId == null || expectedDecisionId <= 0) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Valid approval decision ID is required"
            );
        }

        /*
         * Serialize approval synchronization and
         * processing preparation for this payment.
         */
        PaymentRequest payment =
                payments.findByIdForUpdate(paymentId)
                        .orElseThrow(() -> error(
                                HttpStatus.NOT_FOUND,
                                "Payment not found"
                        ));

        /*
         * Verify the persisted approval decision.
         *
         * Never trust approval status, approver
         * information, or rejection remarks
         * supplied by Kafka.
         *
         * NEW:
         * Read the actual rejection remark directly
         * from APPROVAL_DECISIONS.REMARK.
         */
        List<VerifiedDecision> decisions = jdbcTemplate.query(
                """
                SELECT
                    T.ID AS TASK_ID,
                    D.ID AS DECISION_ID,
                    D.APPROVER_ID,
                    D.DECISION,
                    D.REMARK,
                    D.DECIDED_AT
                FROM APPROVAL_TASKS T
                JOIN APPROVAL_DECISIONS D
                  ON D.TASK_ID = T.ID
                WHERE T.PAYMENT_ID = ?
                  AND T.STATUS IN ('APPROVED', 'REJECTED')
                  AND T.DECIDED_BY = D.APPROVER_ID
                  AND (
                        (T.STATUS = 'APPROVED'
                         AND D.DECISION IN (
                             'APPROVED',
                             'OVERRIDE_APPROVED'
                         ))
                       OR
                        (T.STATUS = 'REJECTED'
                         AND D.DECISION = 'REJECTED')
                  )
                """,
                (rs, rowNum) -> {

                    OffsetDateTime decidedAt =
                            rs.getObject(
                                    "DECIDED_AT",
                                    OffsetDateTime.class
                            );

                    if (decidedAt == null) {
                        throw new IllegalStateException(
                                "Approval decision timestamp is missing"
                        );
                    }

                    LocalDateTime localDecidedAt =
                            decidedAt.atZoneSameInstant(
                                    ZoneId.systemDefault()
                            ).toLocalDateTime();

                    return new VerifiedDecision(
                            rs.getLong("TASK_ID"),
                            rs.getLong("DECISION_ID"),
                            rs.getLong("APPROVER_ID"),
                            rs.getString("DECISION"),
                            rs.getString("REMARK"),
                            localDecidedAt
                    );
                },
                paymentId
        );

        if (decisions.size() != 1) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Expected exactly one verified final approval decision"
            );
        }

        VerifiedDecision decision = decisions.get(0);

        /*
         * Prevent an unrelated or outdated
         * Kafka decision from being applied.
         */
        if (!expectedDecisionId.equals(decision.decisionId())) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Kafka decision ID does not match the Oracle approval decision"
            );
        }

        PaymentStatus targetStatus =
                "REJECTED".equals(decision.decision())
                        ? PaymentStatus.REJECTED
                        : PaymentStatus.APPROVED;

        /*
         * Recovery case:
         *
         * Approval was already synchronized, but
         * processing preparation never happened.
         *
         * PaymentProcessingDispatchService only
         * permits APPROVED -> PROCESSING and
         * creates the Kafka outbox atomically.
         *
         * A previously rejected payment returns
         * without inserting another audit event.
         */
        if (payment.getStatus() == targetStatus) {

            if (targetStatus == PaymentStatus.APPROVED) {

                PaymentProcessingDispatchService.DispatchResult dispatch =
                        processingDispatchService.prepareForProcessing(
                                paymentId
                        );

                return new SynchronizationResult(
                        paymentId,
                        dispatch.paymentStatus(),
                        decision.taskId(),
                        decision.decisionId(),
                        true
                );
            }

            return new SynchronizationResult(
                    paymentId,
                    targetStatus.name(),
                    decision.taskId(),
                    decision.decisionId(),
                    true
            );
        }

        /*
         * Do not restart processing or create
         * another outbox event when a payment
         * has already advanced beyond APPROVED.
         */
        if (payment.getStatus() == PaymentStatus.PROCESSING
                || payment.getStatus() == PaymentStatus.COMPLETE
                || payment.getStatus() == PaymentStatus.FAILED) {

            return new SynchronizationResult(
                    paymentId,
                    payment.getStatus().name(),
                    decision.taskId(),
                    decision.decisionId(),
                    true
            );
        }

        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Payment is not awaiting approval"
            );
        }

        /*
         * NEW:
         * A verified rejection must have a meaningful
         * persisted rejection reason.
         *
         * Reject invalid rejection evidence before
         * changing payment state.
         */
        if (targetStatus == PaymentStatus.REJECTED
                && (decision.remark() == null
                    || decision.remark().isBlank())) {

            throw new IllegalStateException(
                    "Verified rejection reason is missing "
                            + "for approval decision "
                            + decision.decisionId()
            );
        }

        /*
         * Save the verified approval result.
         */
        payment.setStatus(targetStatus);

        if (targetStatus == PaymentStatus.APPROVED) {
            payment.setApprovedAt(decision.decidedAt());
        }

        payments.saveAndFlush(payment);

        PaymentStatusHistory history = new PaymentStatusHistory();

        history.setPaymentId(paymentId);
        history.setPreviousStatus(PaymentStatus.PENDING_APPROVAL);
        history.setNewStatus(targetStatus);
        history.setChangedBy(decision.approverId());

        history.setReason(
                "Approval Workflow decision "
                        + decision.decision()
                        + " (task "
                        + decision.taskId()
                        + ", decision "
                        + decision.decisionId()
                        + ")"
        );

        historyRepository.save(history);

        /*
         * Approved payment:
         *
         * Prepare processing in the SAME
         * transaction. The dispatch service:
         *
         * 1. Verifies the Oracle approval.
         * 2. Moves APPROVED -> PROCESSING.
         * 3. Saves payment history.
         * 4. Inserts payment.approved into
         *    PAY_PAYMENT_OUTBOX.
         *
         * No Stripe API call happens here.
         *
         * Existing approved-payment behavior
         * remains unchanged.
         */
        if (targetStatus == PaymentStatus.APPROVED) {

            PaymentProcessingDispatchService.DispatchResult dispatch =
                    processingDispatchService.prepareForProcessing(
                            paymentId
                    );

            return new SynchronizationResult(
                    paymentId,
                    dispatch.paymentStatus(),
                    decision.taskId(),
                    decision.decisionId(),
                    false
            );
        }

        /*
         * NEW: Transactional rejection audit.
         *
         * At this point:
         *
         * 1. The Oracle approval decision is verified.
         * 2. The payment transitioned from
         *    PENDING_APPROVAL to REJECTED.
         * 3. Payment status history has been saved.
         *
         * Record PAYMENT_REJECTED using the existing
         * Oracle audit outbox.
         *
         * PaymentAuditOutboxService uses
         * Propagation.MANDATORY.
         *
         * Therefore, the payment status update,
         * status history, and audit outbox insertion
         * participate in the SAME Oracle transaction.
         *
         * If the audit insertion fails, the transaction
         * rolls back.
         *
         * Kafka retries cannot create another event
         * after this transition has committed, because
         * the payment will already be REJECTED.
         *
         * The audit dispatcher delivers the event
         * asynchronously after transaction commit.
         *
         * No Stripe processing is triggered.
         */
        auditOutboxService.recordPaymentRejected(
                paymentId,
                decision.taskId(),
                decision.decisionId(),
                decision.approverId(),
                decision.remark(),
                decision.decidedAt()
        );

        return new SynchronizationResult(
                paymentId,
                targetStatus.name(),
                decision.taskId(),
                decision.decisionId(),
                false
        );
    }

    private ResponseStatusException error(
            HttpStatus status,
            String message
    ) {
        return new ResponseStatusException(status, message);
    }
}

