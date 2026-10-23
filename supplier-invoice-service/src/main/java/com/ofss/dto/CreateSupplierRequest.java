package com.ofss.dto;

import com.ofss.entity.SupplierStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(

        @NotBlank(
                message = "Supplier name is required"
        )
        @Size(
                max = 150,
                message = "Supplier name cannot exceed 150 characters"
        )
        String supplierName,

        @NotNull(
                message = "Supplier status is required"
        )
        SupplierStatus status

) {
}