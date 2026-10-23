package com.ofss.service;

import com.ofss.event.PaymentSubmittedEvent;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PaymentValidationService {

    public ValidationResult validate(
            PaymentSubmittedEvent event) {

        if (event == null) {

            return ValidationResult.failure(
                    "Payment event must not be null"
            );
        }

        if (
                event.getPaymentId() == null
                        ||
                        event.getPaymentId() <= 0
        ) {

            return ValidationResult.failure(
                    "Payment ID must be present and greater than zero"
            );
        }

        if (
                event.getInvoiceId() == null
                        ||
                        event.getInvoiceId() <= 0
        ) {

            return ValidationResult.failure(
                    "Invoice ID must be present and greater than zero"
            );
        }

        if (
                event.getPaymentAmount() == null
                        ||
                        event.getPaymentAmount()
                                .compareTo(
                                        BigDecimal.ZERO
                                )
                                <= 0
        ) {

            return ValidationResult.failure(
                    "Payment amount must be greater than zero"
            );
        }

        if (
                event.getStatus() == null
                        ||
                        !"PENDING_VALIDATION"
                                .equals(
                                        event.getStatus()
                                )
        ) {

            return ValidationResult.failure(
                    "Payment must be in PENDING_VALIDATION status"
            );
        }

        if (
                event.getSubmittedBy() == null
                        ||
                        event.getSubmittedBy() <= 0
        ) {

            return ValidationResult.failure(
                    "Submitted by user ID must be present"
            );
        }

        if (
                event.getSubmittedAt() == null
        ) {

            return ValidationResult.failure(
                    "Submitted time must be present"
            );
        }

        return ValidationResult.success();
    }
}