package com.ofss.service;

import com.ofss.dto.CreateInvoiceRequest;
import com.ofss.dto.DuplicateCheckResponse;
import com.ofss.dto.InvoiceResponse;
import com.ofss.dto.UpdateInvoiceRequest;

import com.ofss.entity.Invoice;
import com.ofss.entity.Supplier;
import com.ofss.entity.SupplierStatus;

import com.ofss.exception.BadRequestException;
import com.ofss.exception.ConflictException;
import com.ofss.exception.ResourceNotFoundException;

import com.ofss.repository.InvoiceRepository;
import com.ofss.repository.SupplierRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class InvoiceService {

    private final InvoiceRepository
            invoiceRepository;

    private final SupplierRepository
            supplierRepository;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            SupplierRepository supplierRepository
    ) {
        this.invoiceRepository =
                invoiceRepository;

        this.supplierRepository =
                supplierRepository;
    }

    @Transactional
    public InvoiceResponse createInvoice(
            CreateInvoiceRequest request
    ) {

        Supplier supplier =
                supplierRepository
                        .findById(
                                request.supplierId()
                        )
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Supplier",
                                                String.valueOf(
                                                        request.supplierId()
                                                )
                                        )
                        );

        if (
                supplier.getStatus()
                        != SupplierStatus.ACTIVE
        ) {

            throw new BadRequestException(
                    "Invoice cannot be created for an inactive supplier"
            );
        }

        boolean duplicate =
                invoiceRepository
                        .existsBySupplierIdAndInvoiceNumber(
                                request.supplierId(),
                                request.invoiceNumber()
                        );

        if (duplicate) {

            throw new BadRequestException(
                    "Duplicate invoice: Supplier ID + Invoice Number already exists"
            );
        }

        if (
                request.dueDate()
                        .isBefore(
                                request.invoiceDate()
                        )
        ) {

            throw new BadRequestException(
                    "Due date cannot be before invoice date"
            );
        }

        Invoice invoice =
                new Invoice();

        invoice.setInvoiceNumber(
                request.invoiceNumber()
        );

        invoice.setSupplierId(
                request.supplierId()
        );

        invoice.setInvoiceAmount(
                request.invoiceAmount()
        );

        invoice.setInvoiceDate(
                request.invoiceDate()
        );

        invoice.setDueDate(
                request.dueDate()
        );

        try {

            invoice =
                    invoiceRepository
                            .saveAndFlush(
                                    invoice
                            );

        } catch (
                DataIntegrityViolationException ex
        ) {

            throw new ConflictException(
                    "Duplicate invoice: Supplier ID + Invoice Number already exists"
            );
        }

        return mapToResponse(
                invoice,
                supplier
        );
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(
            Long invoiceId
    ) {

        Invoice invoice =
                getInvoiceEntity(
                        invoiceId
                );

        Supplier supplier =
                getSupplier(
                        invoice.getSupplierId()
                );

        return mapToResponse(
                invoice,
                supplier
        );
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse>
    getInvoices(
            Long supplierId
    ) {

        List<Invoice> invoices;

        if (supplierId == null) {

            invoices =
                    invoiceRepository.findAll();

        } else {

            getSupplier(
                    supplierId
            );

            invoices =
                    invoiceRepository
                            .findBySupplierId(
                                    supplierId
                            );
        }

        return invoices
                .stream()
                .map(
                        invoice -> {

                            Supplier supplier =
                                    getSupplier(
                                            invoice.getSupplierId()
                                    );

                            return mapToResponse(
                                    invoice,
                                    supplier
                            );
                        }
                )
                .toList();
    }

    @Transactional
    public InvoiceResponse updateInvoice(
            Long invoiceId,
            UpdateInvoiceRequest request
    ) {

        Invoice invoice =
                getInvoiceEntity(
                        invoiceId
                );

        if (
                request.dueDate()
                        .isBefore(
                                request.invoiceDate()
                        )
        ) {

            throw new BadRequestException(
                    "Due date cannot be before invoice date"
            );
        }

        invoice.setInvoiceAmount(
                request.invoiceAmount()
        );

        invoice.setInvoiceDate(
                request.invoiceDate()
        );

        invoice.setDueDate(
                request.dueDate()
        );

        invoice =
                invoiceRepository.save(
                        invoice
                );

        Supplier supplier =
                getSupplier(
                        invoice.getSupplierId()
                );

        return mapToResponse(
                invoice,
                supplier
        );
    }

    @Transactional(readOnly = true)
    public DuplicateCheckResponse
    checkExactDuplicate(
            Long supplierId,
            String invoiceNumber
    ) {

        getSupplier(
                supplierId
        );

        boolean duplicate =
                invoiceRepository
                        .existsBySupplierIdAndInvoiceNumber(
                                supplierId,
                                invoiceNumber
                        );

        if (duplicate) {

            return new DuplicateCheckResponse(
                    true,
                    "Exact duplicate invoice found"
            );
        }

        return new DuplicateCheckResponse(
                false,
                "No exact duplicate invoice found"
        );
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse>
    findNearDuplicates(
            Long supplierId,
            BigDecimal amount
    ) {

        getSupplier(
                supplierId
        );

        return invoiceRepository
                .findBySupplierIdAndInvoiceAmount(
                        supplierId,
                        amount
                )
                .stream()
                .map(
                        invoice -> {

                            Supplier supplier =
                                    getSupplier(
                                            invoice.getSupplierId()
                                    );

                            return mapToResponse(
                                    invoice,
                                    supplier
                            );
                        }
                )
                .toList();
    }

    private Invoice getInvoiceEntity(
            Long invoiceId
    ) {

        return invoiceRepository
                .findById(
                        invoiceId
                )
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Invoice",
                                        String.valueOf(
                                                invoiceId
                                        )
                                )
                );
    }

    private Supplier getSupplier(
            Long supplierId
    ) {

        return supplierRepository
                .findById(
                        supplierId
                )
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Supplier",
                                        String.valueOf(
                                                supplierId
                                        )
                                )
                );
    }

    private InvoiceResponse mapToResponse(
            Invoice invoice,
            Supplier supplier
    ) {

        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getSupplierId(),
                supplier.getSupplierName(),
                invoice.getInvoiceAmount(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}