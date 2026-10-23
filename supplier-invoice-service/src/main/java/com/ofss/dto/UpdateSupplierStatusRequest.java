package com.ofss.dto;

import com.ofss.entity.SupplierStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateSupplierStatusRequest(

        @NotNull(
                message = "Supplier status is required"
        )
        SupplierStatus status,

        Long changedBy

) {
}