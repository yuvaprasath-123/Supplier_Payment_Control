package com.ofss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(

        @NotBlank(message = "Challenge ID is required")
        String challengeId,

        @NotBlank(message = "OTP is required")
        @Pattern(
                regexp = "\\d{6}",
                message = "OTP must contain exactly 6 digits"
        )
        String otp

) {
}