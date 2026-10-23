package com.ofss.controller;

import com.ofss.dto.*;
import com.ofss.entity.PaymentStatus;
import com.ofss.service.PaymentProcessingDispatchService;
import com.ofss.service.PaymentRequestService;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment-requests")
public class PaymentRequestController {

    private final PaymentRequestService paymentRequestService;
    private final PaymentProcessingDispatchService processingDispatchService;

    public PaymentRequestController(
            PaymentRequestService paymentRequestService,
            PaymentProcessingDispatchService processingDispatchService
    ) {
        this.paymentRequestService = paymentRequestService;
        this.processingDispatchService = processingDispatchService;
    }

    /*
     * CREATE PAYMENT
     *
     * Only PAYMENT_REQUESTER can create a payment request.
     */
    @PostMapping
    @PreAuthorize("hasRole('PAYMENT_REQUESTER')")
    public ResponseEntity<PaymentRequestResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        paymentRequestService.createPayment(
                                request
                        )
                );
    }

    /*
     * READ PAYMENT
     *
     * PAYMENT_REQUESTER:
     * Used for normal payment-request workflow.
     *
     * FINANCE_MANAGER:
     * Read-only access for audit and control review.
     *
     * No mutation is performed by this endpoint.
     */
    @GetMapping("/{paymentId}")
    @PreAuthorize(
            "hasAnyRole('PAYMENT_REQUESTER', 'FINANCE_MANAGER')"
    )
    public ResponseEntity<PaymentRequestResponse> getPayment(
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(
                paymentRequestService.getPayment(
                        paymentId
                )
        );
    }

    /*
     * LIST PAYMENTS
     *
     * PAYMENT_REQUESTER:
     * Existing payment-request browsing.
     *
     * FINANCE_MANAGER:
     * Read-only payment audit browsing.
     */
    @GetMapping
    @PreAuthorize(
            "hasAnyRole('PAYMENT_REQUESTER', 'FINANCE_MANAGER')"
    )
    public ResponseEntity<List<PaymentRequestResponse>> getPayments(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PaymentStatus status
    ) {
        return ResponseEntity.ok(
                paymentRequestService.getPayments(
                        supplierId,
                        status
                )
        );
    }

    /*
     * UPDATE PAYMENT
     *
     * Only PAYMENT_REQUESTER can modify a draft payment.
     *
     * FINANCE_MANAGER intentionally does not receive
     * write access through the audit permission.
     */
    @PutMapping("/{paymentId}")
    @PreAuthorize("hasRole('PAYMENT_REQUESTER')")
    public ResponseEntity<PaymentRequestResponse> updatePayment(
            @PathVariable Long paymentId,
            @Valid @RequestBody UpdatePaymentRequest request
    ) {
        return ResponseEntity.ok(
                paymentRequestService.updateDraftPayment(
                        paymentId,
                        request
                )
        );
    }

    /*
     * SUBMIT PAYMENT
     *
     * Only PAYMENT_REQUESTER can submit a payment.
     *
     * Audit access must never allow a Finance Manager
     * to submit on behalf of the requester.
     */
    @PostMapping("/{paymentId}/submit")
    @PreAuthorize("hasRole('PAYMENT_REQUESTER')")
    public ResponseEntity<SubmitPaymentResponse> submitPayment(
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(
                paymentRequestService.submitPayment(
                        paymentId
                )
        );
    }

    /*
     * PAYMENT TIMELINE
     *
     * PAYMENT_REQUESTER:
     * Can inspect the lifecycle of the payment.
     *
     * FINANCE_MANAGER:
     * Can inspect the same lifecycle as part of
     * read-only payment auditing.
     */
    @GetMapping("/{paymentId}/timeline")
    @PreAuthorize(
            "hasAnyRole('PAYMENT_REQUESTER', 'FINANCE_MANAGER')"
    )
    public ResponseEntity<List<PaymentStatusHistoryResponse>> getTimeline(
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(
                paymentRequestService.getTimeline(
                        paymentId
                )
        );
    }

    /*
     * RECOVERY API
     *
     * Restricted to FINANCE_MANAGER.
     *
     * Reuses the existing transactional dispatch service.
     *
     * The service:
     * 1. Locks the payment row.
     * 2. Requires APPROVED status.
     * 3. Verifies the persisted approval decision.
     * 4. Changes status to PROCESSING.
     * 5. Creates payment.approved in Oracle outbox.
     *
     * A repeated request after successful dispatch
     * is rejected because the payment is no longer APPROVED.
     *
     * This endpoint does not call Stripe directly.
     */
    @PostMapping("/{paymentId}/recover-processing")
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ResponseEntity<
            PaymentProcessingDispatchService.DispatchResult
    > recoverApprovedPayment(
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(
                processingDispatchService.prepareForProcessing(
                        paymentId
                )
        );
    }
}
