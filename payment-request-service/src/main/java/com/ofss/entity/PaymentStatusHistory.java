package com.ofss.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "PAY_PAYMENT_STATUS_HISTORY")
public class PaymentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HISTORY_ID")
    private Long id;

    @Column(
            name = "PAYMENT_ID",
            nullable = false
    )
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "PREVIOUS_STATUS")
    private PaymentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "NEW_STATUS",
            nullable = false
    )
    private PaymentStatus newStatus;

    @Column(name = "CHANGED_BY")
    private Long changedBy;

    @Column(
            name = "CHANGED_AT",
            nullable = false
    )
    private LocalDateTime changedAt;

    @Column(
            name = "REASON",
            length = 500
    )
    private String reason;

    public PaymentStatusHistory() {
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

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public PaymentStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(
            PaymentStatus previousStatus
    ) {
        this.previousStatus = previousStatus;
    }

    public PaymentStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(
            PaymentStatus newStatus
    ) {
        this.newStatus = newStatus;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Long changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(
            LocalDateTime changedAt
    ) {
        this.changedAt = changedAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}