package com.ofss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.dto.CreateInvoiceRequest;
import com.ofss.entity.Invoice;
import com.ofss.entity.Supplier;
import com.ofss.entity.SupplierStatus;
import com.ofss.exception.BadRequestException;
import com.ofss.repository.InvoiceRepository;
import com.ofss.repository.SupplierRepository;

@ExtendWith(MockitoExtension.class)
public class InvoiceServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    @Test
    void duplicateInvoiceShouldBeRejected() {

        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus(SupplierStatus.ACTIVE);

        when(
                supplierRepository.findById(1L)
        ).thenReturn(
                Optional.of(supplier)
        );

        when(
                invoiceRepository.existsBySupplierIdAndInvoiceNumber(
                        1L,
                        "INV-100"
                )
        ).thenReturn(true);

        CreateInvoiceRequest request =
                new CreateInvoiceRequest(
                        "INV-100",
                        1L,
                        new BigDecimal("50000"),
                        LocalDate.now(),
                        LocalDate.now().plusDays(30)
                );

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> invoiceService.createInvoice(request)
                );

        assertEquals(
                "Duplicate invoice: Supplier ID + Invoice Number already exists",
                exception.getMessage()
        );

        verify(
                supplierRepository
        ).findById(1L);

        verify(
                invoiceRepository
        ).existsBySupplierIdAndInvoiceNumber(
                1L,
                "INV-100"
        );

        verify(
                invoiceRepository,
                never()
        ).saveAndFlush(
                org.mockito.ArgumentMatchers.any(Invoice.class)
        );
    }
}