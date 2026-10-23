package com.ofss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentAuditOutboxServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JsonMapper objectMapper;

    private PaymentAuditOutboxService service;

    @BeforeEach
    void setUp() {

        objectMapper =
                JsonMapper.builder()
                        .build();

        service =
                new PaymentAuditOutboxService(
                        jdbcTemplate,
                        objectMapper
                );
    }

    @Test
    void recordPaymentCreatedShouldCreatePendingAuditOutboxEvent() {

        Long paymentId = 100L;
        Long invoiceId = 200L;
        Long supplierId = 300L;
        Long requesterId = 400L;

        BigDecimal amount =
                new BigDecimal("60000.00");

        when(
                jdbcTemplate.update(
                        anyString(),
                        eq(paymentId),
                        eq("PAYMENT_REQUEST_CREATED"),
                        anyString()
                )
        ).thenReturn(1);

        service.recordPaymentCreated(
                paymentId,
                invoiceId,
                supplierId,
                amount,
                requesterId
        );

        ArgumentCaptor<String> payloadCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(
                jdbcTemplate
        ).update(
                anyString(),
                eq(paymentId),
                eq("PAYMENT_REQUEST_CREATED"),
                payloadCaptor.capture()
        );

        JsonNode payload =
                objectMapper.readTree(
                        payloadCaptor.getValue()
                );

        assertEquals(
                "PAYMENT_REQUEST_CREATED",
                payload.path("eventType").asText()
        );

        assertEquals(
                "PAYMENT",
                payload.path("resourceType").asText()
        );

        assertEquals(
                paymentId.toString(),
                payload.path("resourceId").asText()
        );

        assertEquals(
                paymentId.longValue(),
                payload.path("paymentId").asLong()
        );

        assertEquals(
                requesterId.toString(),
                payload.path("actorId").asText()
        );

        assertTrue(
                payload.hasNonNull("correlationId")
        );

        assertTrue(
                payload.hasNonNull("occurredAt")
        );

        JsonNode afterState =
                objectMapper.readTree(
                        payload.path("afterState").asText()
                );

        assertEquals(
                paymentId.longValue(),
                afterState.path("paymentId").asLong()
        );

        assertEquals(
                invoiceId.longValue(),
                afterState.path("invoiceId").asLong()
        );

        assertEquals(
                supplierId.longValue(),
                afterState.path("supplierId").asLong()
        );

        assertEquals(
                "DRAFT",
                afterState.path("status").asText()
        );

        JsonNode metadata =
                objectMapper.readTree(
                        payload.path("metadata").asText()
                );

        assertEquals(
                "payment-request-service",
                metadata.path("sourceService").asText()
        );

        assertEquals(
                "PAYMENT_REQUEST_CREATED",
                metadata.path("action").asText()
        );
    }

    @Test
    void recordPaymentCreatedShouldRejectInvalidInput() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.recordPaymentCreated(
                                        null,
                                        200L,
                                        300L,
                                        new BigDecimal("60000"),
                                        400L
                                )
                );

        assertEquals(
                "Valid payment, supplier, invoice, "
                        + "amount and requester are required",
                exception.getMessage()
        );

        verifyNoInteractions(
                jdbcTemplate
        );
    }

    @Test
    void recordPaymentCompletedShouldCreatePendingAuditOutboxEvent() {

        Long paymentId = 101L;

        BigDecimal amount =
                new BigDecimal("25000.00");

        String stripePaymentIntentId =
                "pi_test_123456";

        LocalDateTime completedAt =
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        10,
                        30,
                        0
                );

        when(
                jdbcTemplate.update(
                        anyString(),
                        eq(paymentId),
                        eq("PAYMENT_COMPLETED"),
                        anyString()
                )
        ).thenReturn(1);

        service.recordPaymentCompleted(
                paymentId,
                amount,
                stripePaymentIntentId,
                completedAt
        );

        ArgumentCaptor<String> payloadCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(
                jdbcTemplate
        ).update(
                anyString(),
                eq(paymentId),
                eq("PAYMENT_COMPLETED"),
                payloadCaptor.capture()
        );

        JsonNode payload =
                objectMapper.readTree(
                        payloadCaptor.getValue()
                );

        assertEquals(
                "PAYMENT_COMPLETED",
                payload.path("eventType").asText()
        );

        assertTrue(
                payload.path("actorId").isNull()
        );

        JsonNode beforeState =
                objectMapper.readTree(
                        payload.path("beforeState").asText()
                );

        assertEquals(
                "PROCESSING",
                beforeState.path("status").asText()
        );

        JsonNode afterState =
                objectMapper.readTree(
                        payload.path("afterState").asText()
                );

        assertEquals(
                "COMPLETE",
                afterState.path("status").asText()
        );

        assertEquals(
                stripePaymentIntentId,
                afterState.path("transactionReference").asText()
        );

        assertEquals(
                completedAt.toString(),
                afterState.path("completedAt").asText()
        );

        JsonNode metadata =
                objectMapper.readTree(
                        payload.path("metadata").asText()
                );

        assertEquals(
                "PAYMENT_COMPLETED",
                metadata.path("action").asText()
        );

        assertEquals(
                "STRIPE",
                metadata.path("paymentProvider").asText()
        );

        assertEquals(
                stripePaymentIntentId,
                metadata.path("stripePaymentIntentId").asText()
        );
    }

    @Test
    void recordPaymentCompletedShouldRejectInvalidInput() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.recordPaymentCompleted(
                                        101L,
                                        BigDecimal.ZERO,
                                        "pi_test_123456",
                                        LocalDateTime.now()
                                )
                );

        assertEquals(
                "Valid confirmed payment completion "
                        + "details are required",
                exception.getMessage()
        );

        verifyNoInteractions(
                jdbcTemplate
        );
    }

    @Test
    void recordPaymentRejectedShouldCreatePendingAuditOutboxEvent() {

        Long paymentId = 102L;
        Long approvalTaskId = 500L;
        Long approvalDecisionId = 600L;
        Long approverId = 700L;

        String rejectionReason =
                "Invoice supporting documents are invalid";

        LocalDateTime rejectedAt =
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        11,
                        0,
                        0
                );

        when(
                jdbcTemplate.update(
                        anyString(),
                        eq(paymentId),
                        eq("PAYMENT_REJECTED"),
                        anyString()
                )
        ).thenReturn(1);

        service.recordPaymentRejected(
                paymentId,
                approvalTaskId,
                approvalDecisionId,
                approverId,
                rejectionReason,
                rejectedAt
        );

        ArgumentCaptor<String> payloadCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(
                jdbcTemplate
        ).update(
                anyString(),
                eq(paymentId),
                eq("PAYMENT_REJECTED"),
                payloadCaptor.capture()
        );

        JsonNode payload =
                objectMapper.readTree(
                        payloadCaptor.getValue()
                );

        assertEquals(
                "PAYMENT_REJECTED",
                payload.path("eventType").asText()
        );

        assertEquals(
                approverId.toString(),
                payload.path("actorId").asText()
        );

        JsonNode beforeState =
                objectMapper.readTree(
                        payload.path("beforeState").asText()
                );

        assertEquals(
                "PENDING_APPROVAL",
                beforeState.path("status").asText()
        );

        JsonNode afterState =
                objectMapper.readTree(
                        payload.path("afterState").asText()
                );

        assertEquals(
                "REJECTED",
                afterState.path("status").asText()
        );

        assertEquals(
                approverId.longValue(),
                afterState.path("rejectedBy").asLong()
        );

        assertEquals(
                rejectionReason,
                afterState.path("rejectionReason").asText()
        );

        JsonNode metadata =
                objectMapper.readTree(
                        payload.path("metadata").asText()
                );

        assertEquals(
                approvalTaskId.longValue(),
                metadata.path("approvalTaskId").asLong()
        );

        assertEquals(
                approvalDecisionId.longValue(),
                metadata.path("approvalDecisionId").asLong()
        );

        assertEquals(
                approverId.longValue(),
                metadata.path("approverId").asLong()
        );

        assertEquals(
                "REJECTED",
                metadata.path("decision").asText()
        );

        assertEquals(
                rejectionReason,
                metadata.path("rejectionReason").asText()
        );
    }

    @Test
    void recordPaymentRejectedShouldRejectBlankReason() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.recordPaymentRejected(
                                        102L,
                                        500L,
                                        600L,
                                        700L,
                                        "   ",
                                        LocalDateTime.now()
                                )
                );

        assertEquals(
                "Valid verified payment rejection "
                        + "details are required",
                exception.getMessage()
        );

        verifyNoInteractions(
                jdbcTemplate
        );
    }

    @Test
    void recordPaymentCreatedShouldFailWhenOutboxInsertFails() {

        Long paymentId = 103L;

        when(
                jdbcTemplate.update(
                        anyString(),
                        eq(paymentId),
                        eq("PAYMENT_REQUEST_CREATED"),
                        anyString()
                )
        ).thenReturn(0);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                service.recordPaymentCreated(
                                        paymentId,
                                        203L,
                                        303L,
                                        new BigDecimal("10000"),
                                        403L
                                )
                );

        assertEquals(
                "Failed to create audit outbox event "
                        + "PAYMENT_REQUEST_CREATED "
                        + "for payment "
                        + paymentId,
                exception.getMessage()
        );
    }
}