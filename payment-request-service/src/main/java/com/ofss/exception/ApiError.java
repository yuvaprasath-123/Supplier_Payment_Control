package com.ofss.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

public record ApiError(

        HttpStatus status,

        String message,

        Instant timeStamp,

        List<ApiFieldError> errors

) {

    public ApiError(
            HttpStatus status,
            String message
    ) {
        this(
                status,
                message,
                Instant.now(),
                null
        );
    }

    public ApiError(
            HttpStatus status,
            String message,
            List<ApiFieldError> errors
    ) {
        this(
                status,
                message,
                Instant.now(),
                errors
        );
    }
}
