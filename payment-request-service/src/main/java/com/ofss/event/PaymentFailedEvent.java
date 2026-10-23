package com.ofss.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentFailedEvent {

    private Long paymentId;
    private BigDecimal paymentAmount;
    private String reason;
    private LocalDateTime failedAt;

    public PaymentFailedEvent() {
    }

    public PaymentFailedEvent(
            Long paymentId,
            BigDecimal paymentAmount,
            String reason,
            LocalDateTime failedAt
    ) {
        this.paymentId = paymentId;
        this.paymentAmount = paymentAmount;
        this.reason = reason;
        this.failedAt = failedAt;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }
}