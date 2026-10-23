
package com.ofss.kafka;

import com.ofss.event.PaymentApprovedEvent;
import com.ofss.service.PaymentProcessingAttemptService;
import com.ofss.service.PaymentProcessingAttemptResultService;
import com.ofss.service.StripePaymentService;

import com.stripe.model.PaymentIntent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentApprovedConsumerTest {

    private StripePaymentService stripeService;
    private PaymentProcessingAttemptService attemptService;
    private PaymentProcessingAttemptResultService resultService;
    private PaymentApprovedConsumer consumer;

    @BeforeEach
    void setUp() {
        stripeService = mock(StripePaymentService.class);
        attemptService =
                mock(PaymentProcessingAttemptService.class);
        resultService =
                mock(PaymentProcessingAttemptResultService.class);

        consumer = new PaymentApprovedConsumer(
                stripeService,
                attemptService,
                resultService
        );
    }

    private PaymentApprovedEvent validEvent() {
        return new PaymentApprovedEvent(
                3L,
                3L,
                1L,
                new BigDecimal("50000.00"),
                3L,
                LocalDateTime.of(
                        2026, 10, 18,
                        12, 1, 5, 677766000
                )
        );
    }

    @Test
    void skipsStripeWhenAlreadyProcessing() {
        PaymentApprovedEvent event = validEvent();

        when(attemptService.claim(event))
                .thenReturn(
                        PaymentProcessingAttemptService
                                .ClaimResult.ALREADY_PROCESSING
                );

        consumer.consume(event);

        verifyNoInteractions(stripeService, resultService);
    }

    @Test
    void skipsStripeWhenAlreadyCompleted() {
        PaymentApprovedEvent event = validEvent();

        when(attemptService.claim(event))
                .thenReturn(
                        PaymentProcessingAttemptService
                                .ClaimResult.ALREADY_COMPLETED
                );

        consumer.consume(event);

        verifyNoInteractions(stripeService, resultService);
    }

    @Test
    void skipsStripeWhenReconciliationRequired() {
        PaymentApprovedEvent event = validEvent();

        when(attemptService.claim(event))
                .thenReturn(
                        PaymentProcessingAttemptService
                                .ClaimResult.REQUIRES_RECONCILIATION
                );

        consumer.consume(event);

        verifyNoInteractions(stripeService, resultService);
    }

    @Test
    void recordsStripeSuccess() throws Exception {
        PaymentApprovedEvent event = validEvent();

        when(attemptService.claim(event))
                .thenReturn(
                        PaymentProcessingAttemptService
                                .ClaimResult.CLAIMED
                );

        PaymentIntent intent = mock(PaymentIntent.class);

        when(intent.getId()).thenReturn("pi_test_123");
        when(intent.getStatus()).thenReturn("succeeded");

        when(stripeService.createAndConfirmPayment(event))
                .thenReturn(intent);

        consumer.consume(event);

        verify(resultService).recordCompleted(
                eq(3L),
                eq("pi_test_123"),
                eq(new BigDecimal("50000.00"))
        );

        verify(resultService, never())
                .recordRequiresReconciliation(
                        any(),
                        any(),
                        any()
                );

        verify(stripeService, times(1))
                .createAndConfirmPayment(event);
    }

    @Test
    void reconcilesWhenStripeSuccessCannotBeSaved()
            throws Exception {
        PaymentApprovedEvent event = validEvent();

        when(attemptService.claim(event))
                .thenReturn(
                        PaymentProcessingAttemptService
                                .ClaimResult.CLAIMED
                );

        PaymentIntent intent = mock(PaymentIntent.class);

        when(intent.getId()).thenReturn("pi_test_456");
        when(intent.getStatus()).thenReturn("succeeded");

        when(stripeService.createAndConfirmPayment(event))
                .thenReturn(intent);

        doThrow(new IllegalStateException(
                "Simulated Oracle write failure"
        )).when(resultService).recordCompleted(
                eq(3L),
                eq("pi_test_456"),
                any(BigDecimal.class)
        );

        assertThrows(
                IllegalStateException.class,
                () -> consumer.consume(event)
        );

        verify(resultService).recordRequiresReconciliation(
                eq(3L),
                eq("pi_test_456"),
                contains("Oracle completion")
        );

        verify(stripeService, times(1))
                .createAndConfirmPayment(event);
    }

    @Test
    void reconcilesNonSuccessfulStripeStatus()
            throws Exception {
        PaymentApprovedEvent event = validEvent();

        when(attemptService.claim(event))
                .thenReturn(
                        PaymentProcessingAttemptService
                                .ClaimResult.CLAIMED
                );

        PaymentIntent intent = mock(PaymentIntent.class);

        when(intent.getId()).thenReturn("pi_test_789");
        when(intent.getStatus())
                .thenReturn("requires_action");

        when(stripeService.createAndConfirmPayment(event))
                .thenReturn(intent);

        consumer.consume(event);

        verify(resultService).recordRequiresReconciliation(
                eq(3L),
                eq("pi_test_789"),
                contains("requires_action")
        );

        verify(resultService, never())
                .recordCompleted(
                        any(),
                        any(),
                        any()
                );
    }
}
