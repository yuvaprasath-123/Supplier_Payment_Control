package com.ofss.dto;

import com.ofss.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRequestResponse(

        Long paymentId,

        Long invoiceId,

        Long supplierId,

        BigDecimal paymentAmount,

        Long requesterId,

        PaymentStatus status,

        Integer riskScore,

        String riskBand,

        String requiredApprovalLevel,

        LocalDateTime createdAt,

        LocalDateTime submittedAt,

        LocalDateTime approvedAt,

        LocalDateTime completedAt,

        LocalDateTime updatedAt

) {
}