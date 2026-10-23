package com.ofss.exception;

import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.persistence.OptimisticLockException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequestException(
            BadRequestException ex
    ) {

        ApiError apiError =
                new ApiError(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage()
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(apiError);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFoundException(
            ResourceNotFoundException ex
    ) {

        ApiError apiError =
                new ApiError(
                        HttpStatus.NOT_FOUND,
                        ex.getMessage()
                );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(apiError);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(
            AccessDeniedException ex
    ) {

        ApiError apiError =
                new ApiError(
                        HttpStatus.FORBIDDEN,
                        "You are not authorized to perform this action"
                );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(apiError);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiError> handleAuthenticationCredentialsNotFound(
            AuthenticationCredentialsNotFoundException ex
    ) {

        ApiError apiError =
                new ApiError(
                        HttpStatus.UNAUTHORIZED,
                        "Authentication is required"
                );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException ex
    ) {

        List<ApiFieldError> errors =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(
                                error ->
                                        new ApiFieldError(
                                                error.getField(),
                                                error.getDefaultMessage()
                                        )
                        )
                        .toList();

        ApiError apiError =
                new ApiError(
                        HttpStatus.BAD_REQUEST,
                        "Input validation failed",
                        errors
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(apiError);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException ex
    ) {

        ApiError apiError =
                new ApiError(
                        HttpStatus.BAD_REQUEST,
                        "Database constraint violation"
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(apiError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(
            Exception ex
    ) {

        ex.printStackTrace();

        ApiError apiError =
                new ApiError(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unexpected server error"
                );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(apiError);
    }
    
    @ExceptionHandler({
        ObjectOptimisticLockingFailureException.class,
        OptimisticLockException.class
})
public ResponseEntity<ApiError>
handleOptimisticLockException(
        Exception ex
) {

    ApiError apiError =
            new ApiError(
                    HttpStatus.CONFLICT,
                    "This record was modified by another user. Refresh and try again."
            );

    return ResponseEntity
            .status(
                    HttpStatus.CONFLICT
            )
            .body(
                    apiError
            );
}
    
}