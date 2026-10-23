package com.ofss.repository;

import com.ofss.entity.Invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository
        extends JpaRepository<Invoice, Long> {

    List<Invoice> findBySupplierId(
            Long supplierId
    );

    Optional<Invoice>
    findBySupplierIdAndInvoiceNumber(
            Long supplierId,
            String invoiceNumber
    );

    boolean existsBySupplierIdAndInvoiceNumber(
            Long supplierId,
            String invoiceNumber
    );

    List<Invoice>
    findBySupplierIdAndInvoiceAmount(
            Long supplierId,
            BigDecimal invoiceAmount
    );
}