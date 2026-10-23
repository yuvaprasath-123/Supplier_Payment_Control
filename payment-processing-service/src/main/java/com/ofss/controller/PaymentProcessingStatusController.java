
package com.ofss.controller;

import com.ofss.service.PaymentProcessingStatusService;
import com.ofss.service.PaymentProcessingStatusService.ProcessingStatusResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-processing")
public class PaymentProcessingStatusController {

    private final PaymentProcessingStatusService statusService;

    public PaymentProcessingStatusController(
            PaymentProcessingStatusService statusService
    ) {
        this.statusService = statusService;
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ProcessingStatusResponse> getStatus(
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(
                statusService.getStatus(paymentId)
        );
    }
}
