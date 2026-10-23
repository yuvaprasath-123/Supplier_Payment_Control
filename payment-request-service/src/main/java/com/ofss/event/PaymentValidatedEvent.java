package com.ofss.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentValidatedEvent {

    private Long paymentId;

    private Long invoiceId;

    private BigDecimal paymentAmount;

    private Long submittedBy;

    private LocalDateTime submittedAt;

    private LocalDateTime validatedAt;

    public PaymentValidatedEvent() {
    }

    public PaymentValidatedEvent(
            Long paymentId,
            Long invoiceId,
            BigDecimal paymentAmount,
            Long submittedBy,
            LocalDateTime submittedAt,
            LocalDateTime validatedAt) {

        this.paymentId =
                paymentId;

        this.invoiceId =
                invoiceId;

        this.paymentAmount =
                paymentAmount;

        this.submittedBy =
                submittedBy;

        this.submittedAt =
                submittedAt;

        this.validatedAt =
                validatedAt;
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

    public LocalDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(
            LocalDateTime validatedAt) {

        this.validatedAt =
                validatedAt;
    }
}