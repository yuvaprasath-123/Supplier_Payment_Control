package com.ofss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(

        @NotBlank(
                message = "Supplier name is required"
        )
        @Size(
                max = 150,
                message = "Supplier name cannot exceed 150 characters"
        )
        String supplierName

) {
}