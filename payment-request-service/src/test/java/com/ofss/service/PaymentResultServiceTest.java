package com.ofss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.entity.PaymentRequest;
import com.ofss.entity.PaymentStatus;
import com.ofss.entity.PaymentStatusHistory;
import com.ofss.event.PaymentCompletedEvent;
import com.ofss.event.PaymentFailedEvent;
import com.ofss.repository.PaymentRequestRepository;
import com.ofss.repository.PaymentStatusHistoryRepository;

@ExtendWith(MockitoExtension.class)
class PaymentResultServiceTest {

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    @Mock
    private PaymentAuditOutboxService auditOutboxService;

    private PaymentResultService service;

    @BeforeEach
    void setUp() {

        service =
                new PaymentResultService(
                        paymentRequestRepository,
                        historyRepository,
                        auditOutboxService
                );
    }

    @Test
    void handleCompletedShouldRejectNullEvent() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.handleCompleted(null)
                );

        assertEquals(
                "Payment completion event must have a payment ID",
                exception.getMessage()
        );

        verifyNoInteractions(
                paymentRequestRepository,
                historyRepository,
                auditOutboxService
        );
    }

    @Test
    void handleCompletedShouldRejectMissingPaymentId() {

        PaymentCompletedEvent event =
                org.mockito.Mockito.mock(
                        PaymentCompletedEvent.class
                );

        when(
                event.getPaymentId()
        ).thenReturn(
                null
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.handleCompleted(event)
                );

        assertEquals(
                "Payment completion event must have a payment ID",
                exception.getMessage()
        );

        verifyNoInteractions(
                paymentRequestRepository,
                historyRepository,
                auditOutboxService
        );
    }

    @Test
    void handleCompletedShouldRejectMissingPayment() {

        Long paymentId = 1L;

        PaymentCompletedEvent event =
                org.mockito.Mockito.mock(
                        PaymentCompletedEvent.class
                );

        when(
                event.getPaymentId()
        ).thenReturn(
                paymentId
        );

        when(
                paymentRequestRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.empty()
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.handleCompleted(event)
                );

        assertEquals(
                "Payment not found: " + paymentId,
                exception.getMessage()
        );

        verifyNoInteractions(
                historyRepository,
                auditOutboxService
        );
    }

    @Test
    void handleCompletedShouldRejectAmountMismatch() {

        Long paymentId = 2L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setPaymentAmount(
                new BigDecimal("1000.00")
        );

        PaymentCompletedEvent event =
                org.mockito.Mockito.mock(
                        PaymentCompletedEvent.class
                );

        when(
                event.getPaymentId()
        ).thenReturn(
                paymentId
        );

        when(
                event.getPaymentAmount()
        ).thenReturn(
                new BigDecimal("999.00")
        );

        when(
                paymentRequestRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.handleCompleted(event)
                );

        assertEquals(
                "Payment event amount does not match Oracle",
                exception.getMessage()
        );

        verifyNoInteractions(
                historyRepository,
                auditOutboxService
        );
    }

    @Test
    void handleCompletedShouldRejectMissingRequiredData() {

        Long paymentId = 3L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setPaymentAmount(
                new BigDecimal("2000.00")
        );

        PaymentCompletedEvent event =
                org.mockito.Mockito.mock(
                        PaymentCompletedEvent.class
                );

        when(
                event.getPaymentId()
        ).thenReturn(
                paymentId
        );

        when(
                event.getPaymentAmount()
        ).thenReturn(
                new BigDecimal("2000.00")
        );

        when(
                event.getTransactionReference()
        ).thenReturn(
                "   "
        );

        when(
                paymentRequestRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.handleCompleted(event)
                );

        assertEquals(
                "Completion event is missing required data",
                exception.getMessage()
        );

        verifyNoInteractions(
                historyRepository,
                auditOutboxService
        );
    }

    @Test
    void handleCompletedShouldIgnoreDuplicateMatchingCompletion() {

        Long paymentId = 4L;
        String transactionReference = "pi_test_complete_1";

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.COMPLETE);
        payment.setPaymentAmount(
                new BigDecimal("3000.00")
        );
        payment.setTransactionReference(
                transactionReference
        );

        PaymentCompletedEvent event =
                org.mockito.Mockito.mock(
                        PaymentCompletedEvent.class
                );

        when(
                event.getPaymentId()
        ).thenReturn(
                paymentId
        );

        when(
                event.getPaymentAmount()
        ).thenReturn(
                new BigDecimal("3000.00")
        );

        when(
                event.getTransactionReference()
        ).thenReturn(
                transactionReference
        );

        when(
                event.getCompletedAt()
        ).thenReturn(
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        12,
                        0
                )
        );

        when(
                paymentRequestRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        service.handleCompleted(event);

        verify(
                paymentRequestRepository,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(
                historyRepository,
                auditOutboxService
        );
    }

    @Test
    void handleCompletedShouldRejectConflictingDuplicateCompletion() {

        Long paymentId = 5L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.COMPLETE);
        payment.setPaymentAmount(
                new BigDecimal("4000.00")
        );
        payment.setTransactionReference(
                "pi_old"
        );

        PaymentCompletedEvent event =
                org.mockito.Mockito.mock(
                        PaymentCompletedEvent.class
                );

        when(
                event.getPaymentId()
        ).thenReturn(
                paymentId
        );

        when(
                event.getPaymentAmount()
        ).thenReturn(
                new BigDecimal("4000.00")
        );

        when(
                event.getTransactionReference()
        ).thenReturn(
                "pi_new"
        );

        when(
                event.getCompletedAt()
        ).thenReturn(
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        12,
                        30
                )
        );

        when(
                paymentRequestRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.handleCompleted(event)
                );

        assertEquals(
                "Conflicting completion event for payment "
                        + paymentId,
                exception.getMessage()
        );

        verifyNoInteractions(
                historyRepository,
                auditOutboxService
        );
    }

    @Test
    void handleCompletedShouldRejectPaymentNotProcessing() {

        Long paymentId = 6L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setPaymentAmount(
                new BigDecimal("5000.00")
        );

        PaymentCompletedEvent event =
                org.mockito.Mockito.mock(
                        PaymentCompletedEvent.class
                );

        when(
                event.getPaymentId()
        ).thenReturn(
                paymentId
        );

        when(
                event.getPaymentAmount()
        ).thenReturn(
                new BigDecimal("5000.00")
        );

        when(
                event.getTransactionReference()
        ).thenReturn(
                "pi_not_processing"
        );

        when(
                event.getCompletedAt()
        ).thenReturn(
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        13,
                        0
                )
        );

        when(
                paymentRequestRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.handleCompleted(event)
                );

        assertEquals(
                "Payment " + paymentId
                        + " must be PROCESSING, but is APPROVED",
                exception.getMessage()
        );

        verifyNoInteractions(
                historyRepository,
                auditOutboxService
        );
    }

    @Test
    void handleCompletedShouldTransitionProcessingToCompleteAndAudit() {

        Long paymentId = 7L;

        BigDecimal amount =
                new BigDecimal("6000.00");

        String transactionReference =
                "pi_complete_7";

        LocalDateTime completedAt =
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        13,
                        30
                );

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setPaymentAmount(amount);
        payment.setFailedAt(
                LocalDateTime.of(
                        2026,
                        10,
                        20,
                        9,
                        0
                )
        );
        payment.setFailureReason(
                "old failure"
        );

        PaymentCompletedEvent event =
                org.mockito.Mockito.mock(
                        PaymentCompletedEvent.class
                );

        when(
                event.getPaymentId()
        ).thenReturn(
                paymentId
        );

        when(
                event.getPaymentAmount()
        ).thenReturn(
                amount
        );

        when(
                event.getTransactionReference()
        ).thenReturn(
                transactionReference
        );

        when(
                event.getCompletedAt()
        ).thenReturn(
                completedAt
        );

        when(
                paymentRequestRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        service.handleCompleted(event);

        assertEquals(
                PaymentStatus.COMPLETE,
                payment.getStatus()
        );

        assertEquals(
                transactionReference,
                payment.getTransactionReference()
        );

        assertEquals(
                completedAt,
                payment.getCompletedAt()
        );

        assertEquals(
                null,
                payment.getFailedAt()
        );

        assertEquals(
                null,
                payment.getFailureReason()
        );

        verify(
                paymentRequestRepository
        ).saveAndFlush(payment);

        verify(
                historyRepository
        ).save(
                any(PaymentStatusHistory.class)
        );

        verify(
                auditOutboxService
        ).recordPaymentCompleted(
                eq(paymentId),
                eq(amount),
                eq(transactionReference),
                eq(completedAt)
        );
    }

    @Test
    void handleFailedShouldRejectNullEvent() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.handleFailed(null)
                );

        assertEquals(
                "Payment failure event must have a payment ID",
                exception.getMessage()
        );

        verifyNoInteractions(
                paymentRequestRepository,
                historyRepository,
                auditOutboxService
        );
    }

    @Test
    void handleFailedShouldIgnoreDuplicateFailure() {

        Long paymentId = 8L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setPaymentAmount(
                new BigDecimal("7000.00")
        );

        PaymentFailedEvent event =
                org.mockito.Mockito.mock(
                        PaymentFailedEvent.class
                );

        when(
                event.getPaymentId()
        ).thenReturn(
                paymentId
        );

        when(
                event.getPaymentAmount()
        ).thenReturn(
                new BigDecimal("7000.00")
        );

        when(
                event.getReason()
        ).thenReturn(
                "Stripe declined"
        );

        when(
                event.getFailedAt()
        ).thenReturn(
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        14,
                        0
                )
        );

        when(
                paymentRequestRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        service.handleFailed(event);

        verify(
                paymentRequestRepository,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(
                historyRepository,
                auditOutboxService
        );
    }

    @Test
    void handleFailedShouldTransitionProcessingToFailed() {

        Long paymentId = 9L;

        BigDecimal amount =
                new BigDecimal("8000.00");

        String reason =
                "Stripe card declined";

        LocalDateTime failedAt =
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        14,
                        30
                );

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setPaymentAmount(amount);

        PaymentFailedEvent event =
                org.mockito.Mockito.mock(
                        PaymentFailedEvent.class
                );

        when(
                event.getPaymentId()
        ).thenReturn(
                paymentId
        );

        when(
                event.getPaymentAmount()
        ).thenReturn(
                amount
        );

        when(
                event.getReason()
        ).thenReturn(
                reason
        );

        when(
                event.getFailedAt()
        ).thenReturn(
                failedAt
        );

        when(
                paymentRequestRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        service.handleFailed(event);

        assertEquals(
                PaymentStatus.FAILED,
                payment.getStatus()
        );

        assertEquals(
                failedAt,
                payment.getFailedAt()
        );

        assertEquals(
                reason,
                payment.getFailureReason()
        );

        verify(
                paymentRequestRepository
        ).saveAndFlush(payment);

        verify(
                historyRepository
        ).save(
                any(PaymentStatusHistory.class)
        );

        verifyNoInteractions(
                auditOutboxService
        );
    }
}