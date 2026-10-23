package com.ofss.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InvoiceClientResponse(

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