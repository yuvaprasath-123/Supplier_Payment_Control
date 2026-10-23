package com.ofss.dto;

import com.ofss.entity.PaymentStatus;

public record SubmitPaymentResponse(

        Long paymentId,

        PaymentStatus status,

        String message

) {
}