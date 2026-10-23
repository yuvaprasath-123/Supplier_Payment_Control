package com.ofss.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentCompletedEvent {

    private Long paymentId;
    private BigDecimal paymentAmount;
    private String transactionReference;
    private LocalDateTime completedAt;

    public PaymentCompletedEvent() {
    }

    public PaymentCompletedEvent(
            Long paymentId,
            BigDecimal paymentAmount,
            String transactionReference,
            LocalDateTime completedAt
    ) {
        this.paymentId = paymentId;
        this.paymentAmount = paymentAmount;
        this.transactionReference = transactionReference;
        this.completedAt = completedAt;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}