
package com.ofss.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentApprovedEvent {

    private Long paymentId;
    private Long invoiceId;
    private Long supplierId;
    private BigDecimal paymentAmount;
    private Long approvedBy;
    private LocalDateTime approvedAt;

    public PaymentApprovedEvent() {
    }

    public PaymentApprovedEvent(
            Long paymentId,
            Long invoiceId,
            Long supplierId,
            BigDecimal paymentAmount,
            Long approvedBy,
            LocalDateTime approvedAt
    ) {
        this.paymentId = paymentId;
        this.invoiceId = invoiceId;
        this.supplierId = supplierId;
        this.paymentAmount = paymentAmount;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
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

    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }
}
