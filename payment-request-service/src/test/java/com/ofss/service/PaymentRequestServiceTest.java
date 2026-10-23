package com.ofss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.access.AccessDeniedException;

import com.ofss.client.InvoiceClientResponse;
import com.ofss.client.SupplierClientResponse;
import com.ofss.client.SupplierInvoiceClient;

import com.ofss.dto.CreatePaymentRequest;
import com.ofss.dto.PaymentRequestResponse;
import com.ofss.dto.SubmitPaymentResponse;
import com.ofss.dto.UpdatePaymentRequest;

import com.ofss.entity.PaymentRequest;
import com.ofss.entity.PaymentStatus;
import com.ofss.entity.PaymentStatusHistory;

import com.ofss.event.PaymentSubmittedEvent;

import com.ofss.exception.BadRequestException;

import com.ofss.kafka.PaymentEventProducer;

import com.ofss.repository.PaymentRequestRepository;
import com.ofss.repository.PaymentStatusHistoryRepository;

import com.ofss.security.AuthUtil;

@ExtendWith(MockitoExtension.class)
class PaymentRequestServiceTest {

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private PaymentStatusHistoryRepository paymentStatusHistoryRepository;

    @Mock
    private SupplierInvoiceClient supplierInvoiceClient;

    @Mock
    private AuthUtil authUtil;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Mock
    private PaymentAuditOutboxService paymentAuditOutboxService;

    private PaymentRequestService service;

    @BeforeEach
    void setUp() {

        service =
                new PaymentRequestService(
                        paymentRequestRepository,
                        paymentStatusHistoryRepository,
                        supplierInvoiceClient,
                        authUtil,
                        paymentEventProducer,
                        paymentAuditOutboxService
                );
    }

    @Test
    void createPaymentShouldCreateDraftAndAuditOutbox() {

        Long paymentId = 1L;
        Long requesterId = 10L;
        Long invoiceId = 100L;
        Long supplierId = 200L;

        BigDecimal amount =
                new BigDecimal("60000.00");

        CreatePaymentRequest request =
                mock(CreatePaymentRequest.class);

        InvoiceClientResponse invoice =
                mock(InvoiceClientResponse.class);

        SupplierClientResponse supplier =
                mock(SupplierClientResponse.class);

        when(
                request.invoiceId()
        ).thenReturn(
                invoiceId
        );

        when(
                request.paymentAmount()
        ).thenReturn(
                amount
        );

        when(
                authUtil.getCurrentUserId()
        ).thenReturn(
                requesterId
        );

        when(
                supplierInvoiceClient.getInvoice(invoiceId)
        ).thenReturn(
                invoice
        );

        when(
                invoice.invoiceId()
        ).thenReturn(
                invoiceId
        );

        when(
                invoice.supplierId()
        ).thenReturn(
                supplierId
        );

        when(
                supplierInvoiceClient.getSupplier(supplierId)
        ).thenReturn(
                supplier
        );

        when(
                supplier.status()
        ).thenReturn(
                "ACTIVE"
        );

        when(
                paymentRequestRepository.existsByInvoiceId(invoiceId)
        ).thenReturn(
                false
        );

        when(
                paymentRequestRepository.save(
                        any(PaymentRequest.class)
                )
        ).thenAnswer(
                invocation -> {

                    PaymentRequest payment =
                            invocation.getArgument(0);

                    payment.setId(paymentId);

                    return payment;
                }
        );

        PaymentRequestResponse result =
                service.createPayment(request);

        assertNotNull(result);

        verify(
                paymentRequestRepository
        ).save(
                any(PaymentRequest.class)
        );

        verify(
                paymentStatusHistoryRepository
        ).save(
                any(PaymentStatusHistory.class)
        );

        verify(
                paymentAuditOutboxService
        ).recordPaymentCreated(
                eq(paymentId),
                eq(invoiceId),
                eq(supplierId),
                eq(amount),
                eq(requesterId)
        );

        verifyNoInteractions(
                paymentEventProducer
        );
    }

    @Test
    void createPaymentShouldRejectInactiveSupplier() {

        Long requesterId = 11L;
        Long invoiceId = 101L;
        Long supplierId = 201L;

        CreatePaymentRequest request =
                mock(CreatePaymentRequest.class);

        InvoiceClientResponse invoice =
                mock(InvoiceClientResponse.class);

        SupplierClientResponse supplier =
                mock(SupplierClientResponse.class);

        when(
                request.invoiceId()
        ).thenReturn(
                invoiceId
        );

        when(
                authUtil.getCurrentUserId()
        ).thenReturn(
                requesterId
        );

        when(
                supplierInvoiceClient.getInvoice(invoiceId)
        ).thenReturn(
                invoice
        );

        when(
                invoice.supplierId()
        ).thenReturn(
                supplierId
        );

        when(
                supplierInvoiceClient.getSupplier(supplierId)
        ).thenReturn(
                supplier
        );

        when(
                supplier.status()
        ).thenReturn(
                "INACTIVE"
        );

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.createPayment(request)
                );

        assertEquals(
                "Payment request cannot be created for an inactive supplier",
                exception.getMessage()
        );

        verify(
                paymentRequestRepository,
                never()
        ).save(any());

        verifyNoInteractions(
                paymentStatusHistoryRepository,
                paymentAuditOutboxService,
                paymentEventProducer
        );
    }

    @Test
    void createPaymentShouldRejectDuplicateInvoicePayment() {

        Long requesterId = 12L;
        Long invoiceId = 102L;
        Long supplierId = 202L;

        CreatePaymentRequest request =
                mock(CreatePaymentRequest.class);

        InvoiceClientResponse invoice =
                mock(InvoiceClientResponse.class);

        SupplierClientResponse supplier =
                mock(SupplierClientResponse.class);

        when(
                request.invoiceId()
        ).thenReturn(
                invoiceId
        );

        when(
                authUtil.getCurrentUserId()
        ).thenReturn(
                requesterId
        );

        when(
                supplierInvoiceClient.getInvoice(invoiceId)
        ).thenReturn(
                invoice
        );

        when(
                invoice.supplierId()
        ).thenReturn(
                supplierId
        );

        when(
                supplierInvoiceClient.getSupplier(supplierId)
        ).thenReturn(
                supplier
        );

        when(
                supplier.status()
        ).thenReturn(
                "ACTIVE"
        );

        when(
                paymentRequestRepository.existsByInvoiceId(invoiceId)
        ).thenReturn(
                true
        );

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.createPayment(request)
                );

        assertEquals(
                "A payment request already exists for this invoice",
                exception.getMessage()
        );

        verify(
                paymentRequestRepository,
                never()
        ).save(any());

        verifyNoInteractions(
                paymentStatusHistoryRepository,
                paymentAuditOutboxService,
                paymentEventProducer
        );
    }

    @Test
    void getPaymentShouldRejectDifferentRequester() {

        Long paymentId = 2L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setRequesterId(20L);

        when(
                paymentRequestRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                authUtil.getCurrentUserId()
        ).thenReturn(
                99L
        );

        AccessDeniedException exception =
                assertThrows(
                        AccessDeniedException.class,
                        () -> service.getPayment(paymentId)
                );

        assertEquals(
                "You are not authorized to view this payment request",
                exception.getMessage()
        );
    }

    @Test
    void updateDraftPaymentShouldRejectDifferentRequester() {

        Long paymentId = 3L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setRequesterId(30L);

        UpdatePaymentRequest request =
                mock(UpdatePaymentRequest.class);

        when(
                paymentRequestRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                authUtil.getCurrentUserId()
        ).thenReturn(
                99L
        );

        AccessDeniedException exception =
                assertThrows(
                        AccessDeniedException.class,
                        () ->
                                service.updateDraftPayment(
                                        paymentId,
                                        request
                                )
                );

        assertEquals(
                "You are not authorized to modify this payment request",
                exception.getMessage()
        );

        verify(
                paymentRequestRepository,
                never()
        ).save(any());
    }

    @Test
    void updateDraftPaymentShouldRejectNonDraftPayment() {

        Long paymentId = 4L;
        Long requesterId = 40L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setRequesterId(requesterId);
        payment.setStatus(PaymentStatus.PENDING_APPROVAL);

        UpdatePaymentRequest request =
                mock(UpdatePaymentRequest.class);

        when(
                paymentRequestRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                authUtil.getCurrentUserId()
        ).thenReturn(
                requesterId
        );

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () ->
                                service.updateDraftPayment(
                                        paymentId,
                                        request
                                )
                );

        assertEquals(
                "Only DRAFT payment requests can be updated",
                exception.getMessage()
        );

        verify(
                paymentRequestRepository,
                never()
        ).save(any());
    }

    @Test
    void updateDraftPaymentShouldUpdateAmountForOwner() {

        Long paymentId = 5L;
        Long requesterId = 50L;

        BigDecimal newAmount =
                new BigDecimal("75000.00");

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setInvoiceId(105L);
        payment.setSupplierId(205L);
        payment.setRequesterId(requesterId);
        payment.setPaymentAmount(
                new BigDecimal("60000.00")
        );
        payment.setStatus(PaymentStatus.DRAFT);

        UpdatePaymentRequest request =
                mock(UpdatePaymentRequest.class);

        when(
                request.paymentAmount()
        ).thenReturn(
                newAmount
        );

        when(
                paymentRequestRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                authUtil.getCurrentUserId()
        ).thenReturn(
                requesterId
        );

        when(
                paymentRequestRepository.save(payment)
        ).thenReturn(
                payment
        );

        PaymentRequestResponse result =
                service.updateDraftPayment(
                        paymentId,
                        request
                );

        assertNotNull(result);

        assertEquals(
                newAmount,
                payment.getPaymentAmount()
        );

        verify(
                paymentRequestRepository
        ).save(payment);
    }

    @Test
    void submitPaymentShouldMoveDraftToPendingValidationAndPublishKafka() {

        Long paymentId = 6L;
        Long requesterId = 60L;
        Long invoiceId = 106L;
        Long supplierId = 206L;

        BigDecimal amount =
                new BigDecimal("90000.00");

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setRequesterId(requesterId);
        payment.setInvoiceId(invoiceId);
        payment.setSupplierId(supplierId);
        payment.setPaymentAmount(amount);
        payment.setStatus(PaymentStatus.DRAFT);

        InvoiceClientResponse invoice =
                mock(InvoiceClientResponse.class);

        SupplierClientResponse supplier =
                mock(SupplierClientResponse.class);

        when(
                paymentRequestRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                authUtil.getCurrentUserId()
        ).thenReturn(
                requesterId
        );

        when(
                supplierInvoiceClient.getInvoice(invoiceId)
        ).thenReturn(
                invoice
        );

        when(
                invoice.supplierId()
        ).thenReturn(
                supplierId
        );

        when(
                supplierInvoiceClient.getSupplier(supplierId)
        ).thenReturn(
                supplier
        );

        when(
                supplier.status()
        ).thenReturn(
                "ACTIVE"
        );

        when(
                paymentRequestRepository.save(payment)
        ).thenReturn(
                payment
        );

        SubmitPaymentResponse result =
                service.submitPayment(paymentId);

        assertNotNull(result);

        assertEquals(
                paymentId,
                result.paymentId()
        );

        assertEquals(
                PaymentStatus.PENDING_VALIDATION,
                result.status()
        );

        assertEquals(
                PaymentStatus.PENDING_VALIDATION,
                payment.getStatus()
        );

        assertNotNull(
                payment.getSubmittedAt()
        );

        verify(
                paymentStatusHistoryRepository
        ).save(
                any(PaymentStatusHistory.class)
        );

        verify(
                paymentEventProducer
        ).publishPaymentSubmitted(
                any(PaymentSubmittedEvent.class)
        );
    }

    @Test
    void submitPaymentShouldRejectInactiveSupplier() {

        Long paymentId = 7L;
        Long requesterId = 70L;
        Long invoiceId = 107L;
        Long supplierId = 207L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setRequesterId(requesterId);
        payment.setInvoiceId(invoiceId);
        payment.setStatus(PaymentStatus.DRAFT);

        InvoiceClientResponse invoice =
                mock(InvoiceClientResponse.class);

        SupplierClientResponse supplier =
                mock(SupplierClientResponse.class);

        when(
                paymentRequestRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                authUtil.getCurrentUserId()
        ).thenReturn(
                requesterId
        );

        when(
                supplierInvoiceClient.getInvoice(invoiceId)
        ).thenReturn(
                invoice
        );

        when(
                invoice.supplierId()
        ).thenReturn(
                supplierId
        );

        when(
                supplierInvoiceClient.getSupplier(supplierId)
        ).thenReturn(
                supplier
        );

        when(
                supplier.status()
        ).thenReturn(
                "INACTIVE"
        );

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.submitPayment(paymentId)
                );

        assertEquals(
                "Supplier is inactive. Payment cannot be submitted.",
                exception.getMessage()
        );

        assertEquals(
                PaymentStatus.DRAFT,
                payment.getStatus()
        );

        verify(
                paymentRequestRepository,
                never()
        ).save(any());

        verifyNoInteractions(
                paymentStatusHistoryRepository,
                paymentEventProducer
        );
    }

    @Test
    void getPaymentsShouldUseRequesterSupplierAndStatusFilter() {

        Long requesterId = 80L;
        Long supplierId = 208L;

        when(
                authUtil.getCurrentUserId()
        ).thenReturn(
                requesterId
        );

        when(
                paymentRequestRepository
                        .findByRequesterIdAndSupplierIdAndStatus(
                                requesterId,
                                supplierId,
                                PaymentStatus.DRAFT
                        )
        ).thenReturn(
                List.of()
        );

        List<PaymentRequestResponse> result =
                service.getPayments(
                        supplierId,
                        PaymentStatus.DRAFT
                );

        assertNotNull(result);

        assertEquals(
                0,
                result.size()
        );

        verify(
                paymentRequestRepository
        ).findByRequesterIdAndSupplierIdAndStatus(
                requesterId,
                supplierId,
                PaymentStatus.DRAFT
        );
    }

    @Test
    void getTimelineShouldRejectDifferentRequester() {

        Long paymentId = 8L;

        PaymentRequest payment =
                new PaymentRequest();

        payment.setId(paymentId);
        payment.setRequesterId(81L);

        when(
                paymentRequestRepository.findById(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                authUtil.getCurrentUserId()
        ).thenReturn(
                82L
        );

        AccessDeniedException exception =
                assertThrows(
                        AccessDeniedException.class,
                        () -> service.getTimeline(paymentId)
                );

        assertEquals(
                "You are not authorized to view this payment request history",
                exception.getMessage()
        );

        verifyNoInteractions(
                paymentStatusHistoryRepository
        );
    }
}