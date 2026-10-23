
package com.ofss.service;

import com.ofss.entity.PaymentRequest;
import com.ofss.entity.PaymentStatus;
import com.ofss.entity.PaymentStatusHistory;
import com.ofss.event.PaymentCompletedEvent;
import com.ofss.event.PaymentFailedEvent;
import com.ofss.repository.PaymentRequestRepository;
import com.ofss.repository.PaymentStatusHistoryRepository;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentResultService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PaymentResultService.class
            );

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final PaymentAuditOutboxService auditOutboxService;

    public PaymentResultService(
            PaymentRequestRepository paymentRequestRepository,
            PaymentStatusHistoryRepository historyRepository,
            PaymentAuditOutboxService auditOutboxService
    ) {
        this.paymentRequestRepository =
                paymentRequestRepository;
        this.historyRepository =
                historyRepository;
        this.auditOutboxService =
                auditOutboxService;
    }

    /*
     * Confirm successful Stripe payment.
     *
     * Payment update, history creation, and audit
     * outbox insertion share one Oracle transaction.
     */
    @Transactional
    public void handleCompleted(
            PaymentCompletedEvent event
    ) {

        if (event == null
                || event.getPaymentId() == null) {

            throw new IllegalArgumentException(
                    "Payment completion event must have a payment ID"
            );
        }

        PaymentRequest payment = paymentRequestRepository
                .findByIdForUpdate(event.getPaymentId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: "
                                        + event.getPaymentId()
                        )
                );

        validateAmount(
                payment.getPaymentAmount(),
                event.getPaymentAmount()
        );

        if (event.getTransactionReference() == null
                || event.getTransactionReference().isBlank()
                || event.getCompletedAt() == null) {

            throw new IllegalArgumentException(
                    "Completion event is missing required data"
            );
        }

        /*
         * Repeated Kafka delivery must not produce
         * another status history or audit event.
         */
        if (payment.getStatus() == PaymentStatus.COMPLETE) {

            if (!event.getTransactionReference().equals(
                    payment.getTransactionReference()
            )) {

                throw new IllegalStateException(
                        "Conflicting completion event for payment "
                                + payment.getId()
                );
            }

            LOGGER.info(
                    "Ignoring duplicate completion event "
                            + "for payment {}",
                    payment.getId()
            );

            return;
        }

        if (payment.getStatus()
                != PaymentStatus.PROCESSING) {

            throw new IllegalStateException(
                    "Payment " + payment.getId()
                            + " must be PROCESSING, but is "
                            + payment.getStatus()
            );
        }

        /*
         * First, update the authoritative payment.
         */
        payment.setStatus(PaymentStatus.COMPLETE);

        payment.setTransactionReference(
                event.getTransactionReference()
        );

        payment.setCompletedAt(
                event.getCompletedAt()
        );

        payment.setFailedAt(null);
        payment.setFailureReason(null);

        paymentRequestRepository.saveAndFlush(payment);

        /*
         * Second, record status history.
         */
        recordHistory(
                payment.getId(),
                PaymentStatus.PROCESSING,
                PaymentStatus.COMPLETE,
                "Stripe payment completed: "
                        + event.getTransactionReference()
        );

        /*
         * NEW: Create PAYMENT_COMPLETED audit event
         * in the SAME transaction.
         *
         * If this fails, the payment update and
         * status history will also roll back.
         */
        auditOutboxService.recordPaymentCompleted(
                payment.getId(),
                payment.getPaymentAmount(),
                event.getTransactionReference(),
                event.getCompletedAt()
        );

        LOGGER.info(
                "Payment {} transitioned "
                        + "PROCESSING -> COMPLETE; "
                        + "completion audit event queued",
                payment.getId()
        );
    }

    /*
     * Confirm payment failure.
     *
     * Existing failure behavior is preserved.
     */
    @Transactional
    public void handleFailed(
            PaymentFailedEvent event
    ) {

        if (event == null
                || event.getPaymentId() == null) {

            throw new IllegalArgumentException(
                    "Payment failure event must have a payment ID"
            );
        }

        PaymentRequest payment = paymentRequestRepository
                .findByIdForUpdate(event.getPaymentId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: "
                                        + event.getPaymentId()
                        )
                );

        validateAmount(
                payment.getPaymentAmount(),
                event.getPaymentAmount()
        );

        if (event.getReason() == null
                || event.getReason().isBlank()
                || event.getFailedAt() == null) {

            throw new IllegalArgumentException(
                    "Failure event is missing required data"
            );
        }

        if (payment.getStatus()
                == PaymentStatus.FAILED) {

            LOGGER.info(
                    "Ignoring duplicate failure event "
                            + "for payment {}",
                    payment.getId()
            );

            return;
        }

        if (payment.getStatus()
                != PaymentStatus.PROCESSING) {

            throw new IllegalStateException(
                    "Payment " + payment.getId()
                            + " must be PROCESSING, but is "
                            + payment.getStatus()
            );
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailedAt(event.getFailedAt());
        payment.setFailureReason(event.getReason());

        paymentRequestRepository.saveAndFlush(payment);

        recordHistory(
                payment.getId(),
                PaymentStatus.PROCESSING,
                PaymentStatus.FAILED,
                "Stripe payment failed: "
                        + event.getReason()
        );

        LOGGER.info(
                "Payment {} transitioned "
                        + "PROCESSING -> FAILED",
                payment.getId()
        );
    }

    private void validateAmount(
            BigDecimal storedAmount,
            BigDecimal eventAmount
    ) {

        if (storedAmount == null
                || eventAmount == null
                || storedAmount.compareTo(eventAmount) != 0) {

            throw new IllegalArgumentException(
                    "Payment event amount does not match Oracle"
            );
        }
    }

    private void recordHistory(
            Long paymentId,
            PaymentStatus previousStatus,
            PaymentStatus newStatus,
            String reason
    ) {

        PaymentStatusHistory history =
                new PaymentStatusHistory();

        history.setPaymentId(paymentId);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setReason(reason);

        // System-generated Kafka processing event.
        history.setChangedBy(null);

        historyRepository.save(history);
    }
}

