package com.ofss.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "SUP_INVOICES",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UK_SUP_INVOICE_NUMBER",
            columnNames = {
                "SUPPLIER_ID",
                "INVOICE_NUMBER"
            }
        )
    }
)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INVOICE_ID")
    private Long id;

    @Column(
            name = "INVOICE_NUMBER",
            nullable = false,
            length = 100
    )
    private String invoiceNumber;

    @Column(
            name = "SUPPLIER_ID",
            nullable = false
    )
    private Long supplierId;

    @Column(
            name = "INVOICE_AMOUNT",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal invoiceAmount;

    @Column(
            name = "INVOICE_DATE",
            nullable = false
    )
    private LocalDate invoiceDate;

    @Column(
            name = "DUE_DATE",
            nullable = false
    )
    private LocalDate dueDate;

    @Column(
            name = "CREATED_AT",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "UPDATED_AT",
            nullable = false
    )
    private LocalDateTime updatedAt;
    
    @Version
    @Column(
        name = "VERSION",
        nullable = false
    )
    private Long version;

    public Invoice() {
    }

    public Invoice(
            Long id,
            String invoiceNumber,
            Long supplierId,
            BigDecimal invoiceAmount,
            LocalDate invoiceDate,
            LocalDate dueDate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.supplierId = supplierId;
        this.invoiceAmount = invoiceAmount;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    public void prePersist() {

        LocalDateTime now =
                LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {

        this.updatedAt =
                LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(
            String invoiceNumber
    ) {
        this.invoiceNumber = invoiceNumber;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(
            Long supplierId
    ) {
        this.supplierId = supplierId;
    }

    public BigDecimal getInvoiceAmount() {
        return invoiceAmount;
    }

    public void setInvoiceAmount(
            BigDecimal invoiceAmount
    ) {
        this.invoiceAmount = invoiceAmount;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(
            LocalDate invoiceDate
    ) {
        this.invoiceDate = invoiceDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(
            LocalDate dueDate
    ) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(
            LocalDateTime createdAt
    ) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(
            LocalDateTime updatedAt
    ) {
        this.updatedAt = updatedAt;
    }
}