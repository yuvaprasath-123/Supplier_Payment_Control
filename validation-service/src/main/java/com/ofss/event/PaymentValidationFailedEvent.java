package com.ofss.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentValidationFailedEvent {

    private Long paymentId;

    private Long invoiceId;

    private BigDecimal paymentAmount;

    private String reason;

    private LocalDateTime failedAt;

    public PaymentValidationFailedEvent() {
    }

    public PaymentValidationFailedEvent(
            Long paymentId,
            Long invoiceId,
            BigDecimal paymentAmount,
            String reason,
            LocalDateTime failedAt) {

        this.paymentId =
                paymentId;

        this.invoiceId =
                invoiceId;

        this.paymentAmount =
                paymentAmount;

        this.reason =
                reason;

        this.failedAt =
                failedAt;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(
            Long paymentId) {

        this.paymentId =
                paymentId;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(
            Long invoiceId) {

        this.invoiceId =
                invoiceId;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(
            BigDecimal paymentAmount) {

        this.paymentAmount =
                paymentAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(
            String reason) {

        this.reason =
                reason;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(
            LocalDateTime failedAt) {

        this.failedAt =
                failedAt;
    }
}