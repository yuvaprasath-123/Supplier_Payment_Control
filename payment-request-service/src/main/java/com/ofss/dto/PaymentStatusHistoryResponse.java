package com.ofss.dto;

import com.ofss.entity.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentStatusHistoryResponse(

        Long historyId,

        Long paymentId,

        PaymentStatus previousStatus,

        PaymentStatus newStatus,

        Long changedBy,

        LocalDateTime changedAt,

        String reason

) {
}