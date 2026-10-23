package com.ofss.configuration.error;

import java.time.Instant;

public record ApiError(
        int status,
        String code,
        String message,
        String timestamp,
        String path) {

    public static ApiError of(
            int status,
            String code,
            String message,
            String path) {

        return new ApiError(
                status,
                code,
                message,
                Instant.now().toString(),
                path
        );
    }
}