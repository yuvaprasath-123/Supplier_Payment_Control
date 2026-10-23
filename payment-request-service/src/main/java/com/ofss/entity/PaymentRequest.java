package com.ofss.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "PAY_PAYMENT_REQUESTS",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UK_PAY_PAYMENT_INVOICE",
            columnNames = {
                "INVOICE_ID"
            }
        )
    }
)
public class PaymentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PAYMENT_ID")
    private Long id;

    @Column(
            name = "INVOICE_ID",
            nullable = false
    )
    private Long invoiceId;

    @Column(
            name = "SUPPLIER_ID",
            nullable = false
    )
    private Long supplierId;

    @Column(
            name = "PAYMENT_AMOUNT",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal paymentAmount;

    @Column(
            name = "REQUESTER_ID",
            nullable = false
    )
    private Long requesterId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            length = 30
    )
    private PaymentStatus status;

    @Column(name = "RISK_SCORE")
    private Integer riskScore;

    @Column(
            name = "RISK_BAND",
            length = 20
    )
    private String riskBand;

    @Column(
            name = "REQUIRED_APPROVAL_LEVEL",
            length = 10
    )
    private String requiredApprovalLevel;

    @Column(
            name = "CREATED_AT",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(name = "SUBMITTED_AT")
    private LocalDateTime submittedAt;

    @Column(name = "APPROVED_AT")
    private LocalDateTime approvedAt;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

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
    
    @Column(name = "TRANSACTION_REFERENCE")
    private String transactionReference;

    @Column(name = "FAILED_AT")
    private LocalDateTime failedAt;

    @Column(name = "FAILURE_REASON")
    private String failureReason;

    public PaymentRequest() {
    }

    @PrePersist
    public void prePersist() {

        LocalDateTime now =
                LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;

        if (this.status == null) {
            this.status = PaymentStatus.DRAFT;
        }
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

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(
            BigDecimal paymentAmount
    ) {
        this.paymentAmount = paymentAmount;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(
            PaymentStatus status
    ) {
        this.status = status;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskBand() {
        return riskBand;
    }

    public void setRiskBand(String riskBand) {
        this.riskBand = riskBand;
    }

    public String getRequiredApprovalLevel() {
        return requiredApprovalLevel;
    }

    public void setRequiredApprovalLevel(
            String requiredApprovalLevel
    ) {
        this.requiredApprovalLevel =
                requiredApprovalLevel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(
            LocalDateTime createdAt
    ) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(
            LocalDateTime submittedAt
    ) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(
            LocalDateTime approvedAt
    ) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(
            LocalDateTime completedAt
    ) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(
            LocalDateTime updatedAt
    ) {
        this.updatedAt = updatedAt;
    }
    
    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }
    
    
    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}