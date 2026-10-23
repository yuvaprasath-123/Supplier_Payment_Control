
package com.ofss.controller;

import com.ofss.service.PaymentProcessingDispatchService;
import com.ofss.service.PaymentProcessingDispatchService.DispatchResult;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-requests")
public class PaymentProcessingDispatchController {

    private final PaymentProcessingDispatchService dispatchService;

    public PaymentProcessingDispatchController(
            PaymentProcessingDispatchService dispatchService
    ) {
        this.dispatchService = dispatchService;
    }

    /*
     * Only authenticated Finance Managers may
     * prepare an approved payment for processing.
     *
     * This operation does not publish Kafka events
     * and does not invoke Stripe.
     */
    @PostMapping("/{paymentId}/prepare-processing")
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ResponseEntity<DispatchResult> prepareProcessing(
            @PathVariable Long paymentId
    ) {
        DispatchResult result =
                dispatchService.prepareForProcessing(paymentId);

        return ResponseEntity.ok(result);
    }
}
