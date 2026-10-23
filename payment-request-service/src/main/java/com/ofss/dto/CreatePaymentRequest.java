package com.ofss.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaymentRequest(

        @NotNull(
                message = "Invoice ID is required"
        )
        Long invoiceId,

        @NotNull(
                message = "Payment amount is required"
        )
        @DecimalMin(
                value = "0.01",
                message = "Payment amount must be greater than zero"
        )
        BigDecimal paymentAmount

) {
}