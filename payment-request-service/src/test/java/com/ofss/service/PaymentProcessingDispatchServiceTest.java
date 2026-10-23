package com.ofss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import com.ofss.entity.PaymentRequest;
import com.ofss.entity.PaymentStatus;
import com.ofss.entity.PaymentStatusHistory;
import com.ofss.repository.PaymentRequestRepository;
import com.ofss.repository.PaymentStatusHistoryRepository;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingDispatchServiceTest {

    @Mock
    private PaymentRequestRepository paymentRepository;

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PaymentProcessingDispatchService service;

    @BeforeEach
    void setUp() {

        service =
                new PaymentProcessingDispatchService(
                        paymentRepository,
                        historyRepository,
                        jdbcTemplate,
                        JsonMapper.builder().build()
                );
    }

    @Test
    void prepareForProcessingShouldRejectNullPaymentId() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.prepareForProcessing(null)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "Valid payment ID is required",
                exception.getReason()
        );

        verifyNoInteractions(
                paymentRepository,
                historyRepository,
                jdbcTemplate
        );
    }

    @Test
    void prepareForProcessingShouldRejectZeroPaymentId() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.prepareForProcessing(0L)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "Valid payment ID is required",
                exception.getReason()
        );

        verifyNoInteractions(
                paymentRepository,
                historyRepository,
                jdbcTemplate
        );
    }

    @Test
    void prepareForProcessingShouldReturnNotFoundWhenPaymentDoesNotExist() {

        Long paymentId = 1L;

        when(
                paymentRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.empty()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.prepareForProcessing(paymentId)
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                "Payment not found",
                exception.getReason()
        );

        verify(
                paymentRepository
        ).findByIdForUpdate(paymentId);

        verifyNoInteractions(
                historyRepository,
                jdbcTemplate
        );
    }

    @Test
    void prepareForProcessingShouldRejectNonApprovedPayment() {

        Long paymentId = 2L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.DRAFT);

        when(
                paymentRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.prepareForProcessing(paymentId)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "Only APPROVED payments can enter processing",
                exception.getReason()
        );

        verify(
                paymentRepository,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(
                historyRepository,
                jdbcTemplate
        );
    }

    @Test
    void prepareForProcessingShouldRejectMissingApprovedTimestamp() {

        Long paymentId = 3L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.APPROVED);

        when(
                paymentRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.prepareForProcessing(paymentId)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "Approved payment timestamp is missing",
                exception.getReason()
        );

        verify(
                paymentRepository,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(
                historyRepository,
                jdbcTemplate
        );
    }

    @Test
    void prepareForProcessingShouldRejectMissingProcessingInformation() {

        Long paymentId = 4L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setApprovedAt(
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        10,
                        0
                )
        );

        /*
         * Payment amount intentionally left null.
         */
        payment.setInvoiceId(100L);
        payment.setSupplierId(200L);

        when(
                paymentRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.prepareForProcessing(paymentId)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "Payment is missing required processing information",
                exception.getReason()
        );

        verify(
                paymentRepository,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(
                historyRepository,
                jdbcTemplate
        );
    }

    @Test
    void prepareForProcessingShouldRejectWhenVerifiedApproverIsMissing() {

        Long paymentId = 5L;

        PaymentRequest payment =
                createValidApprovedPayment(paymentId);

        when(
                paymentRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                jdbcTemplate.query(
                        anyString(),
                        any(RowMapper.class),
                        eq(paymentId)
                )
        ).thenReturn(
                List.of()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.prepareForProcessing(paymentId)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "Expected exactly one verified approval decision",
                exception.getReason()
        );

        verify(
                paymentRepository,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(
                historyRepository
        );
    }

    @Test
    void prepareForProcessingShouldRejectMultipleVerifiedApprovers() {

        Long paymentId = 6L;

        PaymentRequest payment =
                createValidApprovedPayment(paymentId);

        when(
                paymentRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                jdbcTemplate.query(
                        anyString(),
                        any(RowMapper.class),
                        eq(paymentId)
                )
        ).thenReturn(
                List.of(
                        501L,
                        502L
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.prepareForProcessing(paymentId)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "Expected exactly one verified approval decision",
                exception.getReason()
        );

        verify(
                paymentRepository,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(
                historyRepository
        );
    }

    @Test
    void prepareForProcessingShouldMovePaymentToProcessingAndCreateOutbox()
            throws Exception {

        Long paymentId = 7L;
        Long approverId = 900L;
        Long outboxEventId = 700L;

        PaymentRequest payment =
                createValidApprovedPayment(paymentId);

        when(
                paymentRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                jdbcTemplate.query(
                        anyString(),
                        any(RowMapper.class),
                        eq(paymentId)
                )
        ).thenReturn(
                List.of(approverId)
        );

        when(
                jdbcTemplate.execute(
                        org.mockito.ArgumentMatchers
                                .<ConnectionCallback<Long>>any()
                )
        ).thenReturn(
                outboxEventId
        );

        PaymentProcessingDispatchService.DispatchResult result =
                service.prepareForProcessing(paymentId);

        assertEquals(
                paymentId,
                result.paymentId()
        );

        assertEquals(
                PaymentStatus.PROCESSING.name(),
                result.paymentStatus()
        );

        assertEquals(
                outboxEventId,
                result.outboxEventId()
        );

        assertEquals(
                PaymentStatus.PROCESSING,
                payment.getStatus()
        );

        verify(
                paymentRepository
        ).saveAndFlush(payment);

        verify(
                historyRepository
        ).save(
                any(PaymentStatusHistory.class)
        );

        verify(
                jdbcTemplate
        ).execute(
                org.mockito.ArgumentMatchers
                        .<ConnectionCallback<Long>>any()
        );
    }

    @Test
    void prepareForProcessingShouldFailWhenOutboxEventIdIsNull()
            throws Exception {

        Long paymentId = 8L;

        PaymentRequest payment =
                createValidApprovedPayment(paymentId);

        when(
                paymentRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                jdbcTemplate.query(
                        anyString(),
                        any(RowMapper.class),
                        eq(paymentId)
                )
        ).thenReturn(
                List.of(901L)
        );

        when(
                jdbcTemplate.execute(
                        org.mockito.ArgumentMatchers
                                .<ConnectionCallback<Long>>any()
                )
        ).thenReturn(
                null
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.prepareForProcessing(paymentId)
                );

        assertEquals(
                "Unable to create payment outbox event",
                exception.getMessage()
        );

        assertEquals(
                PaymentStatus.PROCESSING,
                payment.getStatus()
        );

        verify(
                paymentRepository
        ).saveAndFlush(payment);

        verify(
                historyRepository
        ).save(
                any(PaymentStatusHistory.class)
        );
    }

    private PaymentRequest createValidApprovedPayment(
            Long paymentId
    ) {

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setInvoiceId(100L);
        payment.setSupplierId(200L);

        payment.setPaymentAmount(
                new BigDecimal("60000.00")
        );

        payment.setStatus(
                PaymentStatus.APPROVED
        );

        payment.setApprovedAt(
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        10,
                        0
                )
        );

        return payment;
    }
}