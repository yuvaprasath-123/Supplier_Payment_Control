package com.ofss.controller;

import com.ofss.dto.CreateInvoiceRequest;
import com.ofss.dto.DuplicateCheckResponse;
import com.ofss.dto.InvoiceResponse;
import com.ofss.dto.UpdateInvoiceRequest;

import com.ofss.service.InvoiceService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService
            invoiceService;

    public InvoiceController(
            InvoiceService invoiceService
    ) {
        this.invoiceService =
                invoiceService;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse>
    createInvoice(
            @Valid
            @RequestBody
            CreateInvoiceRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        invoiceService
                                .createInvoice(
                                        request
                                )
                );
    }

    @GetMapping("/{invoiceId}")
    public ResponseEntity<InvoiceResponse>
    getInvoice(
            @PathVariable
            Long invoiceId
    ) {

        return ResponseEntity.ok(
                invoiceService
                        .getInvoice(
                                invoiceId
                        )
        );
    }

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>>
    getInvoices(
            @RequestParam(
                    required = false
            )
            Long supplierId
    ) {

        return ResponseEntity.ok(
                invoiceService
                        .getInvoices(
                                supplierId
                        )
        );
    }

    @PutMapping("/{invoiceId}")
    public ResponseEntity<InvoiceResponse>
    updateInvoice(
            @PathVariable
            Long invoiceId,

            @Valid
            @RequestBody
            UpdateInvoiceRequest request
    ) {

        return ResponseEntity.ok(
                invoiceService
                        .updateInvoice(
                                invoiceId,
                                request
                        )
        );
    }

    @GetMapping("/duplicates/exact")
    public ResponseEntity<DuplicateCheckResponse>
    checkExactDuplicate(

            @RequestParam
            Long supplierId,

            @RequestParam
            String invoiceNumber
    ) {

        return ResponseEntity.ok(
                invoiceService
                        .checkExactDuplicate(
                                supplierId,
                                invoiceNumber
                        )
        );
    }

    @GetMapping("/duplicates/near")
    public ResponseEntity<List<InvoiceResponse>>
    findNearDuplicates(

            @RequestParam
            Long supplierId,

            @RequestParam
            BigDecimal amount
    ) {

        return ResponseEntity.ok(
                invoiceService
                        .findNearDuplicates(
                                supplierId,
                                amount
                        )
        );
    }
}