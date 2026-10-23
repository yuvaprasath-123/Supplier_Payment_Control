package com.ofss.dto;

import com.ofss.entity.SupplierStatus;

import java.time.LocalDateTime;

public record SupplierStatusHistoryResponse(

        Long statusHistoryId,

        Long supplierId,

        SupplierStatus previousStatus,

        SupplierStatus newStatus,

        LocalDateTime changedAt,

        Long changedBy

) {
}