package com.ofss.service;

public record ValidationResult(
        boolean valid,
        String reason
) {

    public static ValidationResult success() {

        return new ValidationResult(
                true,
                null
        );
    }

    public static ValidationResult failure(
            String reason) {

        return new ValidationResult(
                false,
                reason
        );
    }
}