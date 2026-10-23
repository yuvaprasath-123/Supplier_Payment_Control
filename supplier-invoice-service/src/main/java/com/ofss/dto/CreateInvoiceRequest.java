package com.ofss.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateInvoiceRequest(

        @NotBlank(
                message = "Invoice number is required"
        )
        @Size(
                max = 100,
                message = "Invoice number cannot exceed 100 characters"
        )
        String invoiceNumber,

        @NotNull(
                message = "Supplier ID is required"
        )
        Long supplierId,

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