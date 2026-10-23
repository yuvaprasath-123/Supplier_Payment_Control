package com.ofss.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RiskAssessmentRequest(

        @NotNull
        @Positive
        Long paymentId,

        @NotBlank
        String validationReference

) {
}