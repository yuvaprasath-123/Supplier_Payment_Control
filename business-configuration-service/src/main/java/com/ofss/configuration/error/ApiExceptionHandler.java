package com.ofss.configuration.error;

import com.ofss.configuration.band.DraftBandConflictException;
import com.ofss.configuration.publication.AlreadyPublishedException;
import com.ofss.configuration.publication.PublicationNotReadyException;
import com.ofss.configuration.readiness.PublicationReadiness;
import com.ofss.configuration.rule.ConfigurationVersionNotFoundException;
import com.ofss.configuration.rule.DraftRuleConflictException;
import com.ofss.configuration.rule.InvalidConfigurationRequestException;
import com.ofss.configuration.sla.DraftSlaConflictException;
import com.ofss.configuration.threshold.DraftThresholdConflictException;
import com.ofss.configuration.version.InvalidDraftRequestException;
import com.ofss.configuration.version.VersionAllocationConflictException;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ApiExceptionHandler.class
            );

    @ExceptionHandler(
            com.ofss.configuration.activation
                    .ActivationFailure.class
    )
    public ResponseEntity<ApiError> activation(
            com.ofss.configuration.activation
                    .ActivationFailure exception,
            HttpServletRequest request) {

        int status =
                exception.reason()
                        == com.ofss.configuration.activation
                                .ActivationFailure.Reason
                                .NO_ACTIVE_CONFIGURATION
                        ? 404
                        : 409;

        return ResponseEntity
                .status(status)
                .body(
                        ApiError.of(
                                status,
                                exception.reason().name(),
                                "The active configuration request could not be completed",
                                request.getRequestURI()
                        )
                );
    }

    public record PublicationRejected(
            int status,
            String code,
            String message,
            String timestamp,
            String path,
            PublicationReadiness readiness) {
    }

    @ExceptionHandler(
            PublicationNotReadyException.class
    )
    public ResponseEntity<PublicationRejected> notReady(
            PublicationNotReadyException exception,
            HttpServletRequest request) {

        var error =
                ApiError.of(
                        409,
                        "CONFIGURATION_NOT_READY",
                        "Configuration is not ready for publication",
                        request.getRequestURI()
                );

        return ResponseEntity
                .status(409)
                .body(
                        new PublicationRejected(
                                error.status(),
                                error.code(),
                                error.message(),
                                error.timestamp(),
                                error.path(),
                                exception.readiness()
                        )
                );
    }

    @ExceptionHandler(
            AlreadyPublishedException.class
    )
    public ResponseEntity<ApiError> alreadyPublished(
            HttpServletRequest request) {

        return ResponseEntity
                .status(409)
                .body(
                        ApiError.of(
                                409,
                                "VERSION_ALREADY_PUBLISHED",
                                "Configuration version is already published",
                                request.getRequestURI()
                        )
                );
    }

    @ExceptionHandler(
            DraftSlaConflictException.class
    )
    public ResponseEntity<ApiError> slaConflict(
            HttpServletRequest request) {

        return ResponseEntity
                .status(409)
                .body(
                        ApiError.of(
                                409,
                                "SLA_LEVEL_CONFLICT",
                                "A conflicting approval SLA already exists in this version",
                                request.getRequestURI()
                        )
                );
    }

    @ExceptionHandler(
            DraftThresholdConflictException.class
    )
    public ResponseEntity<ApiError> thresholdConflict(
            HttpServletRequest request) {

        return ResponseEntity
                .status(409)
                .body(
                        ApiError.of(
                                409,
                                "THRESHOLD_LEVEL_CONFLICT",
                                "A conflicting approval threshold already exists in this version",
                                request.getRequestURI()
                        )
                );
    }

    @ExceptionHandler(
            DraftBandConflictException.class
    )
    public ResponseEntity<ApiError> bandConflict(
            HttpServletRequest request) {

        return ResponseEntity
                .status(409)
                .body(
                        ApiError.of(
                                409,
                                "BAND_CODE_CONFLICT",
                                "A conflicting band already exists in this version",
                                request.getRequestURI()
                        )
                );
    }

    @ExceptionHandler(
            InvalidConfigurationRequestException.class
    )
    public ResponseEntity<ApiError> invalidConfiguration(
            HttpServletRequest request) {

        return ResponseEntity
                .badRequest()
                .body(
                        ApiError.of(
                                400,
                                "INVALID_REQUEST",
                                "Invalid configuration request",
                                request.getRequestURI()
                        )
                );
    }

    @ExceptionHandler(
            ConfigurationVersionNotFoundException.class
    )
    public ResponseEntity<ApiError> versionNotFound(
            HttpServletRequest request) {

        return ResponseEntity
                .status(404)
                .body(
                        ApiError.of(
                                404,
                                "VERSION_NOT_FOUND",
                                "Configuration version was not found",
                                request.getRequestURI()
                        )
                );
    }

    @ExceptionHandler(
            DraftRuleConflictException.class
    )
    public ResponseEntity<ApiError> ruleConflict(
            DraftRuleConflictException exception,
            HttpServletRequest request) {

        return ResponseEntity
                .status(409)
                .body(
                        ApiError.of(
                                409,
                                exception.reason().name(),
                                exception.reason().message(),
                                request.getRequestURI()
                        )
                );
    }

    @ExceptionHandler({
            InvalidDraftRequestException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> invalidDraft(
            HttpServletRequest request) {

        return ResponseEntity
                .badRequest()
                .body(
                        ApiError.of(
                                400,
                                "INVALID_REQUEST",
                                "Invalid draft configuration request",
                                request.getRequestURI()
                        )
                );
    }

    @ExceptionHandler(
            VersionAllocationConflictException.class
    )
    public ResponseEntity<ApiError> allocationConflict(
            HttpServletRequest request) {

        return ResponseEntity
                .status(409)
                .body(
                        ApiError.of(
                                409,
                                "VERSION_ALLOCATION_CONFLICT",
                                "A configuration version could not be allocated; refresh and try again",
                                request.getRequestURI()
                        )
                );
    }

    @ExceptionHandler({
            DataAccessException.class,
            TransactionException.class
    })
    public ResponseEntity<ApiError> databaseUnavailable(
            Exception exception,
            HttpServletRequest request) {

        log.error(
                "Configuration database operation failed for {}",
                request.getRequestURI(),
                exception
        );

        return ResponseEntity
                .status(503)
                .body(
                        ApiError.of(
                                503,
                                "CONFIGURATION_UNAVAILABLE",
                                "Configuration data is temporarily unavailable",
                                request.getRequestURI()
                        )
                );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpectedFailure(
            Exception exception,
            HttpServletRequest request) {

        log.error(
                "Unexpected configuration service failure for {}",
                request.getRequestURI(),
                exception
        );

        return ResponseEntity
                .internalServerError()
                .body(
                        ApiError.of(
                                500,
                                "INTERNAL_ERROR",
                                "The request could not be completed",
                                request.getRequestURI()
                        )
                );
    }
}
