
package com.ofss.controller;

import com.ofss.security.AuthUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/service-tokens")
public class ServiceTokenController {

    private final AuthUtil authUtil;

    private final String assignmentClientId;
    private final String assignmentClientSecret;
    private final boolean assignmentEnabled;

    private final String slaClientId;
    private final String slaClientSecret;
    private final boolean slaEnabled;

    public ServiceTokenController(
            AuthUtil authUtil,

            @Value("${integration.approval-assignment.client-id}")
            String assignmentClientId,

            @Value("${integration.approval-assignment.client-secret}")
            String assignmentClientSecret,

            @Value("${integration.approval-assignment.enabled:false}")
            boolean assignmentEnabled,

            @Value("${integration.approval-sla.client-id:}")
            String slaClientId,

            @Value("${integration.approval-sla.client-secret:}")
            String slaClientSecret,

            @Value("${integration.approval-sla.enabled:false}")
            boolean slaEnabled
    ) {
        this.authUtil = authUtil;

        this.assignmentClientId = assignmentClientId;
        this.assignmentClientSecret = assignmentClientSecret;
        this.assignmentEnabled = assignmentEnabled;

        this.slaClientId = slaClientId;
        this.slaClientSecret = slaClientSecret;
        this.slaEnabled = slaEnabled;
    }

    @PostMapping
    public ResponseEntity<ServiceTokenResponse> issueToken(
            @RequestBody ServiceTokenRequest request
    ) {

        if (request == null
                || request.clientId() == null
                || request.clientId().isBlank()
                || request.clientSecret() == null
                || request.clientSecret().isBlank()) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        /*
         * Existing automatic approval-assignment identity.
         *
         * Preserve its original role, subject,
         * user ID and expiration behavior.
         */
        if (assignmentEnabled
                && secureEquals(
                        assignmentClientId,
                        request.clientId()
                )
                && secureEquals(
                        assignmentClientSecret,
                        request.clientSecret()
                )) {

            String token = authUtil.generateJwtToken(
                    0L,
                    assignmentClientId,
                    List.of("APPROVAL_ASSIGNMENT_SERVICE")
            );

            return ResponseEntity.ok(
                    new ServiceTokenResponse(
                            token,
                            "Bearer",
                            600
                    )
            );
        }

        /*
         * Dedicated automatic SLA escalation identity.
         *
         * This identity must not receive any
         * FINANCE_APPROVER or FINANCE_MANAGER role.
         *
         * Its intended permissions are limited to
         * read-only approver and user lookups.
         */
        if (slaEnabled
                && !slaClientId.isBlank()
                && !slaClientSecret.isBlank()
                && secureEquals(
                        slaClientId,
                        request.clientId()
                )
                && secureEquals(
                        slaClientSecret,
                        request.clientSecret()
                )) {

            String token = authUtil.generateJwtToken(
                    0L,
                    slaClientId,
                    List.of("APPROVAL_SLA_SERVICE")
            );

            return ResponseEntity.ok(
                    new ServiceTokenResponse(
                            token,
                            "Bearer",
                            600
                    )
            );
        }

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .build();
    }

    private boolean secureEquals(
            String expected,
            String actual
    ) {
        if (expected == null || actual == null) {
            return false;
        }

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    public record ServiceTokenRequest(
            String clientId,
            String clientSecret
    ) {}

    public record ServiceTokenResponse(
            String accessToken,
            String tokenType,
            int expiresIn
    ) {}
}

