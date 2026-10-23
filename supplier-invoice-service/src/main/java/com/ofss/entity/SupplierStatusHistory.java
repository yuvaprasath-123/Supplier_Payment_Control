package com.ofss.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "SUP_SUPPLIER_STATUS_HISTORY")
public class SupplierStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STATUS_HISTORY_ID")
    private Long id;

    @Column(
            name = "SUPPLIER_ID",
            nullable = false
    )
    private Long supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "PREVIOUS_STATUS")
    private SupplierStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "NEW_STATUS",
            nullable = false
    )
    private SupplierStatus newStatus;

    @Column(
            name = "CHANGED_AT",
            nullable = false
    )
    private LocalDateTime changedAt;

    @Column(name = "CHANGED_BY")
    private Long changedBy;

    public SupplierStatusHistory() {
    }

    public SupplierStatusHistory(
            Long id,
            Long supplierId,
            SupplierStatus previousStatus,
            SupplierStatus newStatus,
            LocalDateTime changedAt,
            Long changedBy
    ) {
        this.id = id;
        this.supplierId = supplierId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    @PrePersist
    public void prePersist() {

        if (this.changedAt == null) {
            this.changedAt =
                    LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public SupplierStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(
            SupplierStatus previousStatus
    ) {
        this.previousStatus = previousStatus;
    }

    public SupplierStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(
            SupplierStatus newStatus
    ) {
        this.newStatus = newStatus;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(
            LocalDateTime changedAt
    ) {
        this.changedAt = changedAt;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(
            Long changedBy
    ) {
        this.changedBy = changedBy;
    }
}