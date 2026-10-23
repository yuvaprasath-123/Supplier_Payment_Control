package com.ofss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import com.ofss.entity.PaymentRequest;
import com.ofss.entity.PaymentStatus;
import com.ofss.event.PaymentApprovedEvent;
import com.ofss.kafka.PaymentEventProducer;
import com.ofss.repository.PaymentRequestRepository;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxPublisherTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PaymentRequestRepository paymentRepository;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    private JsonMapper objectMapper;

    private PaymentOutboxPublisher publisher;

    @BeforeEach
    void setUp() {

        objectMapper =
                JsonMapper.builder()
                        .build();

        publisher =
                new PaymentOutboxPublisher(
                        jdbcTemplate,
                        paymentRepository,
                        paymentEventProducer,
                        objectMapper
                );
    }

    @Test
    void publishEventShouldRejectNullEventId() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> publisher.publishEvent(null)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "Valid outbox event ID is required",
                exception.getReason()
        );

        verifyNoInteractions(
                jdbcTemplate,
                paymentRepository,
                paymentEventProducer
        );
    }

    @Test
    void publishEventShouldRejectNonPositiveEventId() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> publisher.publishEvent(0L)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "Valid outbox event ID is required",
                exception.getReason()
        );

        verifyNoInteractions(
                jdbcTemplate,
                paymentRepository,
                paymentEventProducer
        );
    }

    @Test
    void publishEventShouldReturnAlreadyPublishedResult()
            throws Exception {

        Long eventId = 100L;
        Long paymentId = 500L;

        mockOutboxRecord(
                eventId,
                paymentId,
                "payment.approved",
                createPayload(
                        paymentId,
                        10L,
                        20L,
                        new BigDecimal("60000.00"),
                        LocalDateTime.of(
                                2026,
                                10,
                                21,
                                10,
                                0
                        ),
                        30L
                ),
                "PUBLISHED"
        );

        PaymentOutboxPublisher.PublicationResult result =
                publisher.publishEvent(eventId);

        assertEquals(
                eventId,
                result.eventId()
        );

        assertEquals(
                paymentId,
                result.paymentId()
        );

        assertEquals(
                "PUBLISHED",
                result.outboxStatus()
        );

        assertTrue(
                result.alreadyPublished()
        );

        verifyNoInteractions(
                paymentRepository,
                paymentEventProducer
        );

        verify(
                jdbcTemplate,
                never()
        ).update(
                anyString(),
                any(Object[].class)
        );
    }

    @Test
    void publishEventShouldReturnNotFoundWhenOutboxEventDoesNotExist() {

        Long eventId = 101L;

        when(
                jdbcTemplate.query(
                        anyString(),
                        any(RowMapper.class),
                        eq(eventId)
                )
        ).thenReturn(
                List.of()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                "Outbox event not found",
                exception.getReason()
        );

        verifyNoInteractions(
                paymentRepository,
                paymentEventProducer
        );
    }

    @Test
    void publishEventShouldRejectNonPendingOutboxStatus()
            throws Exception {

        Long eventId = 102L;

        mockOutboxRecord(
                eventId,
                502L,
                "payment.approved",
                "{}",
                "FAILED"
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "Outbox event is not pending",
                exception.getReason()
        );

        verifyNoInteractions(
                paymentRepository,
                paymentEventProducer
        );
    }

    @Test
    void publishEventShouldRejectUnsupportedEventType()
            throws Exception {

        Long eventId = 103L;

        mockOutboxRecord(
                eventId,
                503L,
                "payment.rejected",
                "{}",
                "PENDING"
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "Unsupported outbox event type",
                exception.getReason()
        );

        verifyNoInteractions(
                paymentRepository,
                paymentEventProducer
        );
    }

    @Test
    void publishEventShouldReturnPaymentNotFound()
            throws Exception {

        Long eventId = 104L;
        Long paymentId = 504L;

        mockOutboxRecord(
                eventId,
                paymentId,
                "payment.approved",
                "{}",
                "PENDING"
        );

        when(
                paymentRepository.findById(paymentId)
        ).thenReturn(
                Optional.empty()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                "Payment not found",
                exception.getReason()
        );

        verifyNoInteractions(
                paymentEventProducer
        );
    }

    @Test
    void publishEventShouldRejectPaymentNotInProcessingStatus()
            throws Exception {

        Long eventId = 105L;
        Long paymentId = 505L;

        mockOutboxRecord(
                eventId,
                paymentId,
                "payment.approved",
                "{}",
                "PENDING"
        );

        PaymentRequest payment =
                mock(PaymentRequest.class);

        when(
                payment.getStatus()
        ).thenReturn(
                PaymentStatus.APPROVED
        );

        when(
                paymentRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "Payment must be PROCESSING before publication",
                exception.getReason()
        );

        verifyNoInteractions(
                paymentEventProducer
        );
    }

    @Test
    void publishEventShouldRejectPayloadMismatch()
            throws Exception {

        Long eventId = 106L;
        Long paymentId = 506L;

        LocalDateTime approvedAt =
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        11,
                        0
                );

        PaymentRequest payment =
                mock(PaymentRequest.class);

        /*
         * Only stub methods that this test actually reaches.
         *
         * The payload invoice ID is deliberately different.
         * Because production validation uses OR conditions,
         * evaluation stops at the invoice mismatch.
         */
        when(
                payment.getStatus()
        ).thenReturn(
                PaymentStatus.PROCESSING
        );

        when(
                payment.getInvoiceId()
        ).thenReturn(
                11L
        );

        when(
                paymentRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        String payload =
                createPayload(
                        paymentId,
                        999L,
                        21L,
                        new BigDecimal("50000.00"),
                        approvedAt,
                        31L
                );

        mockOutboxRecord(
                eventId,
                paymentId,
                "payment.approved",
                payload,
                "PENDING"
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "Outbox payload does not match the payment",
                exception.getReason()
        );

        verify(
                payment
        ).getStatus();

        verify(
                payment
        ).getInvoiceId();

        verifyNoInteractions(
                paymentEventProducer
        );

        verify(
                jdbcTemplate,
                never()
        ).update(
                anyString(),
                any(Object[].class)
        );
    }

    @Test
    void publishEventShouldPublishAndMarkOutboxPublished()
            throws Exception {

        Long eventId = 107L;
        Long paymentId = 507L;
        Long invoiceId = 12L;
        Long supplierId = 22L;
        Long approvedBy = 32L;

        BigDecimal amount =
                new BigDecimal("75000.00");

        LocalDateTime approvedAt =
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        12,
                        0
                );

        PaymentRequest payment =
                mockProcessingPayment(
                        invoiceId,
                        supplierId,
                        amount,
                        approvedAt
                );

        when(
                paymentRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        String payload =
                createPayload(
                        paymentId,
                        invoiceId,
                        supplierId,
                        amount,
                        approvedAt,
                        approvedBy
                );

        mockOutboxRecord(
                eventId,
                paymentId,
                "payment.approved",
                payload,
                "PENDING"
        );

        when(
                paymentEventProducer.publishPaymentApproved(
                        any(PaymentApprovedEvent.class)
                )
        ).thenReturn(
                CompletableFuture.completedFuture(null)
        );

        when(
                jdbcTemplate.update(
                        anyString(),
                        eq(eventId)
                )
        ).thenReturn(1);

        PaymentOutboxPublisher.PublicationResult result =
                publisher.publishEvent(eventId);

        assertEquals(
                eventId,
                result.eventId()
        );

        assertEquals(
                paymentId,
                result.paymentId()
        );

        assertEquals(
                "PUBLISHED",
                result.outboxStatus()
        );

        assertFalse(
                result.alreadyPublished()
        );

        verify(
                paymentEventProducer
        ).publishPaymentApproved(
                any(PaymentApprovedEvent.class)
        );

        verify(
                jdbcTemplate
        ).update(
                anyString(),
                eq(eventId)
        );
    }

    @Test
    void publishEventShouldFailWhenPublishedStatusCannotBeUpdated()
            throws Exception {

        Long eventId = 108L;
        Long paymentId = 508L;
        Long invoiceId = 13L;
        Long supplierId = 23L;
        Long approvedBy = 33L;

        BigDecimal amount =
                new BigDecimal("85000.00");

        LocalDateTime approvedAt =
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        13,
                        0
                );

        PaymentRequest payment =
                mockProcessingPayment(
                        invoiceId,
                        supplierId,
                        amount,
                        approvedAt
                );

        when(
                paymentRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        mockOutboxRecord(
                eventId,
                paymentId,
                "payment.approved",
                createPayload(
                        paymentId,
                        invoiceId,
                        supplierId,
                        amount,
                        approvedAt,
                        approvedBy
                ),
                "PENDING"
        );

        when(
                paymentEventProducer.publishPaymentApproved(
                        any(PaymentApprovedEvent.class)
                )
        ).thenReturn(
                CompletableFuture.completedFuture(null)
        );

        when(
                jdbcTemplate.update(
                        anyString(),
                        eq(eventId)
                )
        ).thenReturn(0);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                "Unable to mark outbox event as published",
                exception.getMessage()
        );

        verify(
                paymentEventProducer
        ).publishPaymentApproved(
                any(PaymentApprovedEvent.class)
        );

        verify(
                jdbcTemplate
        ).update(
                anyString(),
                eq(eventId)
        );
    }

    @SuppressWarnings({
            "rawtypes",
            "unchecked"
    })
    private void mockOutboxRecord(
            Long eventId,
            Long paymentId,
            String eventType,
            String payload,
            String status
    ) throws Exception {

        doAnswer(
                invocation -> {

                    RowMapper rowMapper =
                            invocation.getArgument(1);

                    ResultSet resultSet =
                            mock(ResultSet.class);

                    Clob clob =
                            mock(Clob.class);

                    when(
                            resultSet.getClob("PAYLOAD")
                    ).thenReturn(clob);

                    when(
                            clob.length()
                    ).thenReturn(
                            (long) payload.length()
                    );

                    when(
                            clob.getSubString(
                                    1,
                                    payload.length()
                            )
                    ).thenReturn(payload);

                    when(
                            resultSet.getLong("EVENT_ID")
                    ).thenReturn(eventId);

                    when(
                            resultSet.getLong("PAYMENT_ID")
                    ).thenReturn(paymentId);

                    when(
                            resultSet.getString("EVENT_TYPE")
                    ).thenReturn(eventType);

                    when(
                            resultSet.getString("STATUS")
                    ).thenReturn(status);

                    Object mapped =
                            rowMapper.mapRow(
                                    resultSet,
                                    0
                            );

                    return List.of(mapped);
                }
        ).when(
                jdbcTemplate
        ).query(
                anyString(),
                any(RowMapper.class),
                eq(eventId)
        );
    }

    private PaymentRequest mockProcessingPayment(
            Long invoiceId,
            Long supplierId,
            BigDecimal amount,
            LocalDateTime approvedAt
    ) {

        PaymentRequest payment =
                mock(PaymentRequest.class);

        when(
                payment.getStatus()
        ).thenReturn(
                PaymentStatus.PROCESSING
        );

        when(
                payment.getInvoiceId()
        ).thenReturn(invoiceId);

        when(
                payment.getSupplierId()
        ).thenReturn(supplierId);

        when(
                payment.getPaymentAmount()
        ).thenReturn(amount);

        when(
                payment.getApprovedAt()
        ).thenReturn(approvedAt);

        return payment;
    }

    private String createPayload(
            Long paymentId,
            Long invoiceId,
            Long supplierId,
            BigDecimal amount,
            LocalDateTime approvedAt,
            Long approvedBy
    ) {

        return """
                {
                  "paymentId": %d,
                  "invoiceId": %d,
                  "supplierId": %d,
                  "paymentAmount": %s,
                  "approvedAt": "%s",
                  "approvedBy": %d
                }
                """.formatted(
                paymentId,
                invoiceId,
                supplierId,
                amount.toPlainString(),
                approvedAt,
                approvedBy
        );
    }
}