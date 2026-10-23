package com.ofss.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentSubmittedEvent {

    private Long paymentId;

    private Long invoiceId;

    private BigDecimal paymentAmount;

    private String status;

    private Long submittedBy;

    private LocalDateTime submittedAt;

    public PaymentSubmittedEvent() {
    }

    public PaymentSubmittedEvent(
            Long paymentId,
            Long invoiceId,
            BigDecimal paymentAmount,
            String status,
            Long submittedBy,
            LocalDateTime submittedAt) {

        this.paymentId =
                paymentId;

        this.invoiceId =
                invoiceId;

        this.paymentAmount =
                paymentAmount;

        this.status =
                status;

        this.submittedBy =
                submittedBy;

        this.submittedAt =
                submittedAt;
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

    public String getStatus() {

        return status;
    }

    public void setStatus(
            String status) {

        this.status =
                status;
    }

    public Long getSubmittedBy() {

        return submittedBy;
    }

    public void setSubmittedBy(
            Long submittedBy) {

        this.submittedBy =
                submittedBy;
    }

    public LocalDateTime getSubmittedAt() {

        return submittedAt;
    }

    public void setSubmittedAt(
            LocalDateTime submittedAt) {

        this.submittedAt =
                submittedAt;
    }
}