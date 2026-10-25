package com.ofss.service;

import com.ofss.client.InvoiceClientResponse;
import com.ofss.client.SupplierClientResponse;
import com.ofss.client.SupplierInvoiceClient;

import com.ofss.dto.CreatePaymentRequest;
import com.ofss.dto.PaymentRequestResponse;
import com.ofss.dto.PaymentStatusHistoryResponse;
import com.ofss.dto.SubmitPaymentResponse;
import com.ofss.dto.UpdatePaymentRequest;

import com.ofss.entity.PaymentRequest;
import com.ofss.entity.PaymentStatus;
import com.ofss.entity.PaymentStatusHistory;

import com.ofss.event.PaymentSubmittedEvent;

import com.ofss.exception.BadRequestException;
import com.ofss.exception.ResourceNotFoundException;

import com.ofss.kafka.PaymentEventProducer;

import com.ofss.repository.PaymentRequestRepository;
import com.ofss.repository.PaymentStatusHistoryRepository;

import com.ofss.security.AuthUtil;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentRequestService {

    private final PaymentRequestRepository
            paymentRequestRepository;

    private final PaymentStatusHistoryRepository
            paymentStatusHistoryRepository;

    private final SupplierInvoiceClient
            supplierInvoiceClient;

    private final AuthUtil
            authUtil;

    private final PaymentEventProducer
            paymentEventProducer;

    private final PaymentAuditOutboxService
            paymentAuditOutboxService;


    public PaymentRequestService(
            PaymentRequestRepository paymentRequestRepository,
            PaymentStatusHistoryRepository paymentStatusHistoryRepository,
            SupplierInvoiceClient supplierInvoiceClient,
            AuthUtil authUtil,
            PaymentEventProducer paymentEventProducer,
            PaymentAuditOutboxService paymentAuditOutboxService
    ) {

        this.paymentRequestRepository =
                paymentRequestRepository;

        this.paymentStatusHistoryRepository =
                paymentStatusHistoryRepository;

        this.supplierInvoiceClient =
                supplierInvoiceClient;

        this.authUtil =
                authUtil;

        this.paymentEventProducer =
                paymentEventProducer;

        this.paymentAuditOutboxService =
                paymentAuditOutboxService;
    }


    /*
     * =========================================================
     * CREATE PAYMENT
     * =========================================================
     *
     * supplier-invoice-service is contacted here because
     * payment-request-service already validates the invoice.
     *
     * The invoice due date is persisted as a snapshot in
     * PAY_PAYMENT_REQUESTS.INVOICE_DUE_DATE.
     *
     * Approval workflow therefore does not need another
     * supplier-invoice HTTP call later.
     */
    @Transactional
    public PaymentRequestResponse createPayment(
            CreatePaymentRequest request
    ) {

        Long requesterId =
                authUtil.getCurrentUserId();


        InvoiceClientResponse invoice =
                supplierInvoiceClient
                        .getInvoice(
                                request.invoiceId()
                        );


        if (
                invoice == null
                ||
                invoice.invoiceId() == null
        ) {

            throw new BadRequestException(
                    "Invoice could not be loaded"
            );
        }


        if (
                invoice.dueDate() == null
        ) {

            throw new BadRequestException(
                    "Invoice due date is required"
            );
        }


        SupplierClientResponse supplier =
                supplierInvoiceClient
                        .getSupplier(
                                invoice.supplierId()
                        );


        if (
                !"ACTIVE"
                        .equalsIgnoreCase(
                                supplier.status()
                        )
        ) {

            throw new BadRequestException(
                    "Payment request cannot be created "
                            + "for an inactive supplier"
            );
        }


        if (
                paymentRequestRepository
                        .existsByInvoiceId(
                                request.invoiceId()
                        )
        ) {

            throw new BadRequestException(
                    "A payment request already exists "
                            + "for this invoice"
            );
        }


        PaymentRequest payment =
                new PaymentRequest();


        payment.setInvoiceId(
                invoice.invoiceId()
        );


        /*
         * Persist authoritative due-date snapshot.
         */
        payment.setInvoiceDueDate(
                invoice.dueDate()
        );


        payment.setSupplierId(
                invoice.supplierId()
        );


        payment.setPaymentAmount(
                request.paymentAmount()
        );


        payment.setRequesterId(
                requesterId
        );


        payment.setStatus(
                PaymentStatus.DRAFT
        );


        payment =
                paymentRequestRepository
                        .save(
                                payment
                        );


        saveStatusHistory(
                payment.getId(),
                null,
                PaymentStatus.DRAFT,
                requesterId,
                "Payment request created"
        );


        paymentAuditOutboxService
                .recordPaymentCreated(
                        payment.getId(),
                        payment.getInvoiceId(),
                        payment.getSupplierId(),
                        payment.getPaymentAmount(),
                        requesterId
                );


        return mapToResponse(
                payment
        );
    }


    /*
     * =========================================================
     * GET PAYMENT
     * =========================================================
     */
    @Transactional(readOnly = true)
    public PaymentRequestResponse getPayment(
            Long paymentId
    ) {

        PaymentRequest payment =
                getPaymentEntity(
                        paymentId
                );


        Long currentRequesterId =
                authUtil
                        .getCurrentUserId();


        if (
                !payment
                        .getRequesterId()
                        .equals(
                                currentRequesterId
                        )
        ) {

            throw new AccessDeniedException(
                    "You are not authorized "
                            + "to view this payment request"
            );
        }


        return mapToResponse(
                payment
        );
    }


    /*
     * =========================================================
     * GET PAYMENTS
     * =========================================================
     */
    @Transactional(readOnly = true)
    public List<PaymentRequestResponse> getPayments(
            Long supplierId,
            PaymentStatus status
    ) {

        Long currentRequesterId =
                authUtil
                        .getCurrentUserId();


        List<PaymentRequest> payments;


        if (
                supplierId != null
                &&
                status != null
        ) {

            payments =
                    paymentRequestRepository
                            .findByRequesterIdAndSupplierIdAndStatus(
                                    currentRequesterId,
                                    supplierId,
                                    status
                            );


        } else if (
                supplierId != null
        ) {

            payments =
                    paymentRequestRepository
                            .findByRequesterIdAndSupplierId(
                                    currentRequesterId,
                                    supplierId
                            );


        } else if (
                status != null
        ) {

            payments =
                    paymentRequestRepository
                            .findByRequesterIdAndStatus(
                                    currentRequesterId,
                                    status
                            );


        } else {

            payments =
                    paymentRequestRepository
                            .findByRequesterId(
                                    currentRequesterId
                            );
        }


        return payments
                .stream()
                .map(
                        this::mapToResponse
                )
                .toList();
    }


    /*
     * =========================================================
     * UPDATE DRAFT PAYMENT
     * =========================================================
     */
    @Transactional
    public PaymentRequestResponse updateDraftPayment(
            Long paymentId,
            UpdatePaymentRequest request
    ) {

        PaymentRequest payment =
                getPaymentEntity(
                        paymentId
                );


        Long currentUserId =
                authUtil
                        .getCurrentUserId();


        if (
                !payment
                        .getRequesterId()
                        .equals(
                                currentUserId
                        )
        ) {

            throw new AccessDeniedException(
                    "You are not authorized "
                            + "to modify this payment request"
            );
        }


        if (
                payment.getStatus()
                        != PaymentStatus.DRAFT
        ) {

            throw new BadRequestException(
                    "Only DRAFT payment requests "
                            + "can be updated"
            );
        }


        payment.setPaymentAmount(
                request.paymentAmount()
        );


        payment =
                paymentRequestRepository
                        .save(
                                payment
                        );


        return mapToResponse(
                payment
        );
    }


    /*
     * =========================================================
     * SUBMIT PAYMENT
     * =========================================================
     */
    @Transactional
    public SubmitPaymentResponse submitPayment(
            Long paymentId
    ) {

        PaymentRequest payment =
                getPaymentEntity(
                        paymentId
                );


        Long currentUserId =
                authUtil
                        .getCurrentUserId();


        if (
                !payment
                        .getRequesterId()
                        .equals(
                                currentUserId
                        )
        ) {

            throw new AccessDeniedException(
                    "You are not authorized "
                            + "to submit this payment request"
            );
        }


        if (
                payment.getStatus()
                        != PaymentStatus.DRAFT
        ) {

            throw new BadRequestException(
                    "Only DRAFT payment requests "
                            + "can be submitted"
            );
        }


        /*
         * Refresh the invoice before submission.
         *
         * This ensures that if the invoice due date was
         * legitimately changed while the payment remained
         * DRAFT, the submitted payment keeps the latest
         * authoritative due date.
         */
        InvoiceClientResponse invoice =
                supplierInvoiceClient
                        .getInvoice(
                                payment.getInvoiceId()
                        );


        if (
                invoice == null
                ||
                invoice.dueDate() == null
        ) {

            throw new BadRequestException(
                    "Invoice due date is required"
            );
        }


        SupplierClientResponse supplier =
                supplierInvoiceClient
                        .getSupplier(
                                invoice.supplierId()
                        );


        if (
                !"ACTIVE"
                        .equalsIgnoreCase(
                                supplier.status()
                        )
        ) {

            throw new BadRequestException(
                    "Supplier is inactive. "
                            + "Payment cannot be submitted."
            );
        }


        /*
         * Refresh the persisted due-date snapshot.
         */
        payment.setInvoiceDueDate(
                invoice.dueDate()
        );


        PaymentStatus previousStatus =
                payment.getStatus();


        LocalDateTime submittedAt =
                LocalDateTime.now();


        payment.setStatus(
                PaymentStatus.PENDING_VALIDATION
        );


        payment.setSubmittedAt(
                submittedAt
        );


        payment =
                paymentRequestRepository
                        .save(
                                payment
                        );


        saveStatusHistory(
                paymentId,
                previousStatus,
                PaymentStatus.PENDING_VALIDATION,
                currentUserId,
                "Payment submitted for validation"
        );


        PaymentSubmittedEvent event =
                new PaymentSubmittedEvent(
                        payment.getId(),
                        payment.getInvoiceId(),
                        payment.getPaymentAmount(),
                        payment.getStatus().name(),
                        currentUserId,
                        submittedAt
                );


        System.out.println(
                ">>> ABOUT TO PUBLISH KAFKA EVENT "
                        + "FOR PAYMENT ID = "
                        + payment.getId()
        );


        paymentEventProducer
                .publishPaymentSubmitted(
                        event
                );


        System.out.println(
                ">>> RETURNED FROM KAFKA PRODUCER "
                        + "FOR PAYMENT ID = "
                        + payment.getId()
        );


        return new SubmitPaymentResponse(
                paymentId,
                payment.getStatus(),
                "Payment submitted successfully"
        );
    }


    /*
     * =========================================================
     * PAYMENT TIMELINE
     * =========================================================
     */
    @Transactional(readOnly = true)
    public List<PaymentStatusHistoryResponse> getTimeline(
            Long paymentId
    ) {

        PaymentRequest payment =
                getPaymentEntity(
                        paymentId
                );


        Long currentRequesterId =
                authUtil
                        .getCurrentUserId();


        if (
                !payment
                        .getRequesterId()
                        .equals(
                                currentRequesterId
                        )
        ) {

            throw new AccessDeniedException(
                    "You are not authorized "
                            + "to view this payment request history"
            );
        }


        return paymentStatusHistoryRepository
                .findByPaymentIdOrderByChangedAtAsc(
                        paymentId
                )
                .stream()
                .map(
                        history ->
                                new PaymentStatusHistoryResponse(
                                        history.getId(),
                                        history.getPaymentId(),
                                        history.getPreviousStatus(),
                                        history.getNewStatus(),
                                        history.getChangedBy(),
                                        history.getChangedAt(),
                                        history.getReason()
                                )
                )
                .toList();
    }


    /*
     * =========================================================
     * PAYMENT ENTITY
     * =========================================================
     */
    public PaymentRequest getPaymentEntity(
            Long paymentId
    ) {

        return paymentRequestRepository
                .findById(
                        paymentId
                )
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Payment request",
                                        String.valueOf(
                                                paymentId
                                        )
                                )
                );
    }


    /*
     * =========================================================
     * STATUS HISTORY
     * =========================================================
     */
    private void saveStatusHistory(
            Long paymentId,
            PaymentStatus previousStatus,
            PaymentStatus newStatus,
            Long changedBy,
            String reason
    ) {

        PaymentStatusHistory history =
                new PaymentStatusHistory();


        history.setPaymentId(
                paymentId
        );


        history.setPreviousStatus(
                previousStatus
        );


        history.setNewStatus(
                newStatus
        );


        history.setChangedBy(
                changedBy
        );


        history.setReason(
                reason
        );


        paymentStatusHistoryRepository
                .save(
                        history
                );
    }


    /*
     * =========================================================
     * RESPONSE
     * =========================================================
     *
     * No API response contract change is required here.
     *
     * invoiceDueDate is an internal approval-control
     * snapshot and therefore does not need to be added to
     * PaymentRequestResponse for this feature.
     */
    private PaymentRequestResponse mapToResponse(
            PaymentRequest payment
    ) {

        return new PaymentRequestResponse(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getSupplierId(),
                payment.getPaymentAmount(),
                payment.getRequesterId(),
                payment.getStatus(),
                payment.getRiskScore(),
                payment.getRiskBand(),
                payment.getRequiredApprovalLevel(),
                payment.getCreatedAt(),
                payment.getSubmittedAt(),
                payment.getApprovedAt(),
                payment.getCompletedAt(),
                payment.getUpdatedAt()
        );
    }
}