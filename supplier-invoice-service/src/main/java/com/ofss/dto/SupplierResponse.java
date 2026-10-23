package com.ofss.dto;

import com.ofss.entity.SupplierStatus;

import java.time.LocalDateTime;

public record SupplierResponse(

        Long supplierId,

        String supplierName,

        SupplierStatus status,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}