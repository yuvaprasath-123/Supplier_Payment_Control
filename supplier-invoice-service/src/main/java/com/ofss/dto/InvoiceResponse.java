package com.ofss.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InvoiceResponse(

        Long invoiceId,

        String invoiceNumber,

        Long supplierId,

        String supplierName,

        BigDecimal invoiceAmount,

        LocalDate invoiceDate,

        LocalDate dueDate,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}