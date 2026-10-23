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

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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
import com.ofss.entity.PaymentStatusHistory;
import com.ofss.repository.PaymentRequestRepository;
import com.ofss.repository.PaymentStatusHistoryRepository;

@ExtendWith(MockitoExtension.class)
class ApprovalResultSynchronizationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PaymentRequestRepository payments;

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    @Mock
    private PaymentProcessingDispatchService processingDispatchService;

    @Mock
    private PaymentAuditOutboxService auditOutboxService;

    private ApprovalResultSynchronizationService service;

    @BeforeEach
    void setUp() {

        service =
                new ApprovalResultSynchronizationService(
                        jdbcTemplate,
                        payments,
                        historyRepository,
                        processingDispatchService,
                        auditOutboxService
                );
    }

    @Test
    void synchronizeShouldRejectNullPaymentId() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.synchronize(
                                null,
                                100L
                        )
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
                jdbcTemplate,
                payments,
                historyRepository,
                processingDispatchService,
                auditOutboxService
        );
    }

    @Test
    void synchronizeShouldRejectInvalidDecisionId() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.synchronize(
                                1L,
                                0L
                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "Valid approval decision ID is required",
                exception.getReason()
        );

        verifyNoInteractions(
                jdbcTemplate,
                payments,
                historyRepository,
                processingDispatchService,
                auditOutboxService
        );
    }

    @Test
    void synchronizeShouldReturnNotFoundWhenPaymentDoesNotExist() {

        when(
                payments.findByIdForUpdate(1L)
        ).thenReturn(
                Optional.empty()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.synchronize(
                                1L,
                                100L
                        )
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
                payments
        ).findByIdForUpdate(1L);

        verifyNoInteractions(
                jdbcTemplate,
                historyRepository,
                processingDispatchService,
                auditOutboxService
        );
    }

    @Test
    void synchronizeShouldRejectWhenDecisionIdDoesNotMatch()
            throws Exception {

        Long paymentId = 1L;
        Long expectedDecisionId = 100L;

        PaymentRequest payment =
                mock(PaymentRequest.class);

        when(
                payments.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        mockVerifiedDecision(
                paymentId,
                10L,
                200L,
                300L,
                "APPROVED",
                null,
                OffsetDateTime.of(
                        2026,
                        10,
                        21,
                        10,
                        0,
                        0,
                        0,
                        ZoneOffset.UTC
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.synchronize(
                                paymentId,
                                expectedDecisionId
                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                "Kafka decision ID does not match "
                        + "the Oracle approval decision",
                exception.getReason()
        );

        verify(
                payments
        ).findByIdForUpdate(paymentId);

        verify(
                payments,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(
                historyRepository,
                processingDispatchService,
                auditOutboxService
        );
    }

    @Test
    void synchronizeShouldApproveAndPreparePaymentForProcessing()
            throws Exception {

        Long paymentId = 1L;
        Long taskId = 10L;
        Long decisionId = 100L;
        Long approverId = 500L;

        OffsetDateTime decidedAt =
                OffsetDateTime.of(
                        2026,
                        10,
                        21,
                        10,
                        30,
                        0,
                        0,
                        ZoneOffset.UTC
                );

        PaymentRequest payment =
                mock(PaymentRequest.class);

        when(
                payment.getStatus()
        ).thenReturn(
                PaymentStatus.PENDING_APPROVAL
        );

        when(
                payments.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        mockVerifiedDecision(
                paymentId,
                taskId,
                decisionId,
                approverId,
                "APPROVED",
                null,
                decidedAt
        );

        PaymentProcessingDispatchService.DispatchResult dispatchResult =
                mock(
                        PaymentProcessingDispatchService.DispatchResult.class
                );

        when(
                dispatchResult.paymentStatus()
        ).thenReturn(
                "PROCESSING"
        );

        when(
                processingDispatchService
                        .prepareForProcessing(paymentId)
        ).thenReturn(
                dispatchResult
        );

        ApprovalResultSynchronizationService.SynchronizationResult result =
                service.synchronize(
                        paymentId,
                        decisionId
                );

        assertEquals(
                paymentId,
                result.paymentId()
        );

        assertEquals(
                "PROCESSING",
                result.paymentStatus()
        );

        assertEquals(
                taskId,
                result.approvalTaskId()
        );

        assertEquals(
                decisionId,
                result.approvalDecisionId()
        );

        assertFalse(
                result.alreadySynchronized()
        );

        verify(
                payment
        ).setStatus(
                PaymentStatus.APPROVED
        );

        verify(
                payment
        ).setApprovedAt(
                any(LocalDateTime.class)
        );

        verify(
                payments
        ).saveAndFlush(payment);

        verify(
                historyRepository
        ).save(
                any(PaymentStatusHistory.class)
        );

        verify(
                processingDispatchService
        ).prepareForProcessing(paymentId);

        verify(
                auditOutboxService,
                never()
        ).recordPaymentRejected(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void synchronizeShouldRejectPaymentAndWriteAuditOutbox()
            throws Exception {

        Long paymentId = 2L;
        Long taskId = 20L;
        Long decisionId = 200L;
        Long approverId = 600L;

        String rejectionReason =
                "Invoice amount does not match supporting documentation";

        OffsetDateTime decidedAt =
                OffsetDateTime.of(
                        2026,
                        10,
                        21,
                        11,
                        0,
                        0,
                        0,
                        ZoneOffset.UTC
                );

        PaymentRequest payment =
                mock(PaymentRequest.class);

        when(
                payment.getStatus()
        ).thenReturn(
                PaymentStatus.PENDING_APPROVAL
        );

        when(
                payments.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        mockVerifiedDecision(
                paymentId,
                taskId,
                decisionId,
                approverId,
                "REJECTED",
                rejectionReason,
                decidedAt
        );

        ApprovalResultSynchronizationService.SynchronizationResult result =
                service.synchronize(
                        paymentId,
                        decisionId
                );

        assertEquals(
                paymentId,
                result.paymentId()
        );

        assertEquals(
                "REJECTED",
                result.paymentStatus()
        );

        assertEquals(
                taskId,
                result.approvalTaskId()
        );

        assertEquals(
                decisionId,
                result.approvalDecisionId()
        );

        assertFalse(
                result.alreadySynchronized()
        );

        verify(
                payment
        ).setStatus(
                PaymentStatus.REJECTED
        );

        verify(
                payments
        ).saveAndFlush(payment);

        verify(
                historyRepository
        ).save(
                any(PaymentStatusHistory.class)
        );

        verify(
                auditOutboxService
        ).recordPaymentRejected(
                eq(paymentId),
                eq(taskId),
                eq(decisionId),
                eq(approverId),
                eq(rejectionReason),
                any(LocalDateTime.class)
        );

        verify(
                processingDispatchService,
                never()
        ).prepareForProcessing(any());
    }

    @Test
    void synchronizeShouldRejectVerifiedRejectionWithoutReason()
            throws Exception {

        Long paymentId = 3L;
        Long decisionId = 300L;

        PaymentRequest payment =
                mock(PaymentRequest.class);

        when(
                payment.getStatus()
        ).thenReturn(
                PaymentStatus.PENDING_APPROVAL
        );

        when(
                payments.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        mockVerifiedDecision(
                paymentId,
                30L,
                decisionId,
                700L,
                "REJECTED",
                "   ",
                OffsetDateTime.now()
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.synchronize(
                                paymentId,
                                decisionId
                        )
                );

        assertEquals(
                "Verified rejection reason is missing "
                        + "for approval decision "
                        + decisionId,
                exception.getMessage()
        );

        verify(
                payment,
                never()
        ).setStatus(any());

        verify(
                payments,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(
                historyRepository,
                processingDispatchService,
                auditOutboxService
        );
    }

    @Test
    void synchronizeShouldNotRedispatchPaymentAlreadyProcessing()
            throws Exception {

        Long paymentId = 4L;
        Long decisionId = 400L;

        PaymentRequest payment =
                mock(PaymentRequest.class);

        when(
                payment.getStatus()
        ).thenReturn(
                PaymentStatus.PROCESSING
        );

        when(
                payments.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        mockVerifiedDecision(
                paymentId,
                40L,
                decisionId,
                800L,
                "APPROVED",
                null,
                OffsetDateTime.now()
        );

        ApprovalResultSynchronizationService.SynchronizationResult result =
                service.synchronize(
                        paymentId,
                        decisionId
                );

        assertEquals(
                paymentId,
                result.paymentId()
        );

        assertEquals(
                "PROCESSING",
                result.paymentStatus()
        );

        assertTrue(
                result.alreadySynchronized()
        );

        verify(
                payments,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(
                historyRepository,
                processingDispatchService,
                auditOutboxService
        );
    }

    @SuppressWarnings({
            "rawtypes",
            "unchecked"
    })
    private void mockVerifiedDecision(
            Long paymentId,
            Long taskId,
            Long decisionId,
            Long approverId,
            String decision,
            String remark,
            OffsetDateTime decidedAt
    ) throws Exception {

        doAnswer(
                invocation -> {

                    RowMapper rowMapper =
                            invocation.getArgument(1);

                    ResultSet resultSet =
                            mock(ResultSet.class);

                    when(
                            resultSet.getLong("TASK_ID")
                    ).thenReturn(taskId);

                    when(
                            resultSet.getLong("DECISION_ID")
                    ).thenReturn(decisionId);

                    when(
                            resultSet.getLong("APPROVER_ID")
                    ).thenReturn(approverId);

                    when(
                            resultSet.getString("DECISION")
                    ).thenReturn(decision);

                    when(
                            resultSet.getString("REMARK")
                    ).thenReturn(remark);

                    when(
                            resultSet.getObject(
                                    "DECIDED_AT",
                                    OffsetDateTime.class
                            )
                    ).thenReturn(decidedAt);

                    Object mappedDecision =
                            rowMapper.mapRow(
                                    resultSet,
                                    0
                            );

                    return List.of(
                            mappedDecision
                    );
                }
        ).when(
                jdbcTemplate
        ).query(
                anyString(),
                any(RowMapper.class),
                eq(paymentId)
        );
    }
}