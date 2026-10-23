package com.ofss.service;

import com.ofss.client.InvoiceClientResponse;
import com.ofss.client.SupplierClientResponse;
import com.ofss.client.SupplierInvoiceClient;
import com.ofss.dto.*;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentRequestService {

    private final PaymentRequestRepository paymentRequestRepository;

    private final PaymentStatusHistoryRepository paymentStatusHistoryRepository;

    private final SupplierInvoiceClient supplierInvoiceClient;

    private final AuthUtil authUtil;

    private final PaymentEventProducer paymentEventProducer;

    private final PaymentAuditOutboxService paymentAuditOutboxService;

    public PaymentRequestService(
            PaymentRequestRepository paymentRequestRepository,
            PaymentStatusHistoryRepository paymentStatusHistoryRepository,
            SupplierInvoiceClient supplierInvoiceClient,
            AuthUtil authUtil,
            PaymentEventProducer paymentEventProducer,
            PaymentAuditOutboxService paymentAuditOutboxService
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.paymentStatusHistoryRepository = paymentStatusHistoryRepository;
        this.supplierInvoiceClient = supplierInvoiceClient;
        this.authUtil = authUtil;
        this.paymentEventProducer = paymentEventProducer;
        this.paymentAuditOutboxService = paymentAuditOutboxService;
    }

    /*
     * Create payment request.
     *
     * All three operations share the same Oracle transaction:
     *
     * 1. Save the payment request.
     * 2. Save payment status history.
     * 3. Save PAYMENT_REQUEST_CREATED audit outbox event.
     *
     * No HTTP or Kafka audit publishing occurs here.
     */
    @Transactional
    public PaymentRequestResponse createPayment(
            CreatePaymentRequest request
    ) {

        Long requesterId = authUtil.getCurrentUserId();

        InvoiceClientResponse invoice =
                supplierInvoiceClient.getInvoice(
                        request.invoiceId()
                );

        SupplierClientResponse supplier =
                supplierInvoiceClient.getSupplier(
                        invoice.supplierId()
                );

        if (!"ACTIVE".equalsIgnoreCase(supplier.status())) {
            throw new BadRequestException(
                    "Payment request cannot be created for an inactive supplier"
            );
        }

        if (paymentRequestRepository.existsByInvoiceId(
                request.invoiceId()
        )) {
            throw new BadRequestException(
                    "A payment request already exists for this invoice"
            );
        }

        PaymentRequest payment = new PaymentRequest();

        payment.setInvoiceId(invoice.invoiceId());
        payment.setSupplierId(invoice.supplierId());
        payment.setPaymentAmount(request.paymentAmount());
        payment.setRequesterId(requesterId);
        payment.setStatus(PaymentStatus.DRAFT);

        payment = paymentRequestRepository.save(payment);

        saveStatusHistory(
                payment.getId(),
                null,
                PaymentStatus.DRAFT,
                requesterId,
                "Payment request created"
        );

        /*
         * Persist the audit event in PAY_AUDIT_OUTBOX.
         *
         * PaymentAuditOutboxService uses:
         *
         * @Transactional(propagation = Propagation.MANDATORY)
         *
         * Therefore, this insert participates in the
         * existing payment-creation transaction.
         *
         * If the insert fails, payment creation rolls back.
         */
        paymentAuditOutboxService.recordPaymentCreated(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getSupplierId(),
                payment.getPaymentAmount(),
                requesterId
        );

        return mapToResponse(payment);
    }

    /*
     * Read one payment.
     *
     * PAYMENT_REQUESTER:
     * Can read only their own payment.
     *
     * FINANCE_MANAGER:
     * Can read any payment for audit purposes.
     */
    @Transactional(readOnly = true)
    public PaymentRequestResponse getPayment(
            Long paymentId
    ) {

        PaymentRequest payment =
                getPaymentEntity(paymentId);

        Long currentUserId =
                authUtil.getCurrentUserId();

        if (
                !isFinanceManager() &&
                !payment.getRequesterId().equals(currentUserId)
        ) {
            throw new AccessDeniedException(
                    "You are not authorized to view this payment request"
            );
        }

        return mapToResponse(payment);
    }

    /*
     * List payments.
     *
     * PAYMENT_REQUESTER:
     * Existing behavior is preserved. Only payments created
     * by the current requester are returned.
     *
     * FINANCE_MANAGER:
     * Can inspect all payments for audit purposes and apply
     * supplier/status filters.
     *
     * For the current project volume the manager-side audit
     * filtering is performed after findAll().
     */
    @Transactional(readOnly = true)
    public List<PaymentRequestResponse> getPayments(
            Long supplierId,
            PaymentStatus status
    ) {

        if (isFinanceManager()) {

            return paymentRequestRepository
                    .findAll()
                    .stream()
                    .filter(payment ->
                            supplierId == null ||
                            supplierId.equals(
                                    payment.getSupplierId()
                            )
                    )
                    .filter(payment ->
                            status == null ||
                            status == payment.getStatus()
                    )
                    .map(this::mapToResponse)
                    .toList();
        }

        Long currentRequesterId =
                authUtil.getCurrentUserId();

        List<PaymentRequest> payments;

        if (
                supplierId != null &&
                status != null
        ) {

            payments =
                    paymentRequestRepository
                            .findByRequesterIdAndSupplierIdAndStatus(
                                    currentRequesterId,
                                    supplierId,
                                    status
                            );

        } else if (supplierId != null) {

            payments =
                    paymentRequestRepository
                            .findByRequesterIdAndSupplierId(
                                    currentRequesterId,
                                    supplierId
                            );

        } else if (status != null) {

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
                .map(this::mapToResponse)
                .toList();
    }

    /*
     * Update payment.
     *
     * Write authorization remains unchanged.
     *
     * Only the requester who owns the payment may
     * update a DRAFT payment.
     *
     * FINANCE_MANAGER audit permission does not grant
     * modification rights.
     */
    @Transactional
    public PaymentRequestResponse updateDraftPayment(
            Long paymentId,
            UpdatePaymentRequest request
    ) {

        PaymentRequest payment =
                getPaymentEntity(paymentId);

        Long currentUserId =
                authUtil.getCurrentUserId();

        if (
                !payment.getRequesterId().equals(
                        currentUserId
                )
        ) {
            throw new AccessDeniedException(
                    "You are not authorized to modify this payment request"
            );
        }

        if (
                payment.getStatus() !=
                PaymentStatus.DRAFT
        ) {
            throw new BadRequestException(
                    "Only DRAFT payment requests can be updated"
            );
        }

        payment.setPaymentAmount(
                request.paymentAmount()
        );

        payment =
                paymentRequestRepository.save(
                        payment
                );

        return mapToResponse(payment);
    }

    /*
     * Submit payment.
     *
     * Write authorization remains unchanged.
     *
     * Only the requester who owns the payment can
     * submit it.
     */
    @Transactional
    public SubmitPaymentResponse submitPayment(
            Long paymentId
    ) {

        PaymentRequest payment =
                getPaymentEntity(paymentId);

        Long currentUserId =
                authUtil.getCurrentUserId();

        if (
                !payment.getRequesterId().equals(
                        currentUserId
                )
        ) {
            throw new AccessDeniedException(
                    "You are not authorized to submit this payment request"
            );
        }

        if (
                payment.getStatus() !=
                PaymentStatus.DRAFT
        ) {
            throw new BadRequestException(
                    "Only DRAFT payment requests can be submitted"
            );
        }

        InvoiceClientResponse invoice =
                supplierInvoiceClient.getInvoice(
                        payment.getInvoiceId()
                );

        SupplierClientResponse supplier =
                supplierInvoiceClient.getSupplier(
                        invoice.supplierId()
                );

        if (
                !"ACTIVE".equalsIgnoreCase(
                        supplier.status()
                )
        ) {
            throw new BadRequestException(
                    "Supplier is inactive. Payment cannot be submitted."
            );
        }

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
                paymentRequestRepository.save(
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
                ">>> ABOUT TO PUBLISH KAFKA EVENT FOR PAYMENT ID = "
                        + payment.getId()
        );

        paymentEventProducer.publishPaymentSubmitted(
                event
        );

        System.out.println(
                ">>> RETURNED FROM KAFKA PRODUCER FOR PAYMENT ID = "
                        + payment.getId()
        );

        return new SubmitPaymentResponse(
                paymentId,
                payment.getStatus(),
                "Payment submitted successfully"
        );
    }

    /*
     * Payment lifecycle timeline.
     *
     * PAYMENT_REQUESTER:
     * Can read timeline only for their own payment.
     *
     * FINANCE_MANAGER:
     * Can read any payment timeline for audit purposes.
     */
    @Transactional(readOnly = true)
    public List<PaymentStatusHistoryResponse> getTimeline(
            Long paymentId
    ) {

        PaymentRequest payment =
                getPaymentEntity(paymentId);

        Long currentUserId =
                authUtil.getCurrentUserId();

        if (
                !isFinanceManager() &&
                !payment.getRequesterId().equals(
                        currentUserId
                )
        ) {
            throw new AccessDeniedException(
                    "You are not authorized to view this payment request history"
            );
        }

        return paymentStatusHistoryRepository
                .findByPaymentIdOrderByChangedAtAsc(
                        paymentId
                )
                .stream()
                .map(history ->
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

    public PaymentRequest getPaymentEntity(
            Long paymentId
    ) {

        return paymentRequestRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Payment request",
                                String.valueOf(
                                        paymentId
                                )
                        )
                );
    }

    /*
     * Checks the authenticated Spring Security authorities.
     *
     * Spring Security stores hasRole("FINANCE_MANAGER")
     * as authority ROLE_FINANCE_MANAGER.
     */
    private boolean isFinanceManager() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (
                authentication == null ||
                !authentication.isAuthenticated()
        ) {
            return false;
        }

        return authentication
                .getAuthorities()
                .stream()
                .map(
                        GrantedAuthority::getAuthority
                )
                .anyMatch(
                        "ROLE_FINANCE_MANAGER"::equals
                );
    }

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

        paymentStatusHistoryRepository.save(
                history
        );
    }

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