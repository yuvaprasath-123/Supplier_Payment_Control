package com.ofss.client;

import java.time.LocalDateTime;

public record SupplierClientResponse(

        Long supplierId,

        String supplierName,

        String status,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}