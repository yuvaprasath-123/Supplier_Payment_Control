package com.ofss.exception;

public record ApiFieldError(
        String field,
        String message
) {}