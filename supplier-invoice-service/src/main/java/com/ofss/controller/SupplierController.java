package com.ofss.controller;

import com.ofss.dto.CreateSupplierRequest;
import com.ofss.dto.SupplierResponse;
import com.ofss.dto.SupplierStatusHistoryResponse;
import com.ofss.dto.UpdateSupplierRequest;
import com.ofss.dto.UpdateSupplierStatusRequest;

import com.ofss.entity.SupplierStatus;

import com.ofss.service.SupplierService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService
            supplierService;

    public SupplierController(
            SupplierService supplierService
    ) {
        this.supplierService =
                supplierService;
    }

    @PostMapping
    public ResponseEntity<SupplierResponse>
    createSupplier(
            @Valid
            @RequestBody
            CreateSupplierRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        supplierService
                                .createSupplier(
                                        request
                                )
                );
    }

    @GetMapping
    public ResponseEntity<List<SupplierResponse>>
    getAllSuppliers(
            @RequestParam(
                    required = false
            )
            SupplierStatus status
    ) {

        return ResponseEntity.ok(
                supplierService
                        .getAllSuppliers(
                                status
                        )
        );
    }

    @GetMapping("/{supplierId}")
    public ResponseEntity<SupplierResponse>
    getSupplier(
            @PathVariable
            Long supplierId
    ) {

        return ResponseEntity.ok(
                supplierService
                        .getSupplier(
                                supplierId
                        )
        );
    }

    @PutMapping("/{supplierId}")
    public ResponseEntity<SupplierResponse>
    updateSupplier(
            @PathVariable
            Long supplierId,

            @Valid
            @RequestBody
            UpdateSupplierRequest request
    ) {

        return ResponseEntity.ok(
                supplierService
                        .updateSupplier(
                                supplierId,
                                request
                        )
        );
    }

    @PatchMapping(
            "/{supplierId}/status"
    )
    public ResponseEntity<SupplierResponse>
    updateStatus(
            @PathVariable
            Long supplierId,

            @Valid
            @RequestBody
            UpdateSupplierStatusRequest request
    ) {

        return ResponseEntity.ok(
                supplierService
                        .updateSupplierStatus(
                                supplierId,
                                request
                        )
        );
    }

    @GetMapping(
            "/{supplierId}/status-history"
    )
    public ResponseEntity<
            List<SupplierStatusHistoryResponse>
            >
    getStatusHistory(
            @PathVariable
            Long supplierId
    ) {

        return ResponseEntity.ok(
                supplierService
                        .getStatusHistory(
                                supplierId
                        )
        );
    }
}