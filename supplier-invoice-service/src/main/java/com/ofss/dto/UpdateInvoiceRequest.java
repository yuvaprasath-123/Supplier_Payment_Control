package com.ofss.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateInvoiceRequest(

        @NotNull(
                message = "Invoice amount is required"
        )
        @DecimalMin(
                value = "0.01",
                message = "Invoice amount must be greater than zero"
        )
        BigDecimal invoiceAmount,

        @NotNull(
                message = "Invoice date is required"
        )
        LocalDate invoiceDate,

        @NotNull(
                message = "Due date is required"
        )
        LocalDate dueDate

) {
}