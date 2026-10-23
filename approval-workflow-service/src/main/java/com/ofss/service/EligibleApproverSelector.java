
package com.ofss.service;

import com.ofss.entity.ApprovalLevel;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EligibleApproverSelector {

    private final RestClient securityClient;

    public EligibleApproverSelector(
            @Value("${integration.security-url:http://localhost:8081}")
            String securityUrl
    ) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .build()
        );

        factory.setReadTimeout(Duration.ofSeconds(5));

        this.securityClient = RestClient.builder()
                .baseUrl(securityUrl)
                .requestFactory(factory)
                .build();
    }

    /*
     * Initial assignment:
     * Prefer the required approval level,
     * allowing a higher-level fallback.
     */
    public Long selectAtOrAboveLevel(
            ApprovalLevel requiredLevel,
            Long requesterId,
            String authorization
    ) {
        validate(requiredLevel, requesterId, authorization);

        for (ApprovalLevel level : ApprovalLevel.values()) {

            if (level.ordinal() < requiredLevel.ordinal()) {
                continue;
            }

            Long selected = findEligibleAtLevel(
                    level,
                    requesterId,
                    null,
                    authorization
            );

            if (selected != null) {
                return selected;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No eligible active approver available"
        );
    }

    /*
     * Automatic L1/L2 SLA escalation:
     * Select an eligible approver at the
     * exact next level.
     */
    public Long selectAtExactLevel(
            ApprovalLevel level,
            Long requesterId,
            Long currentApproverId,
            String authorization
    ) {
        validate(level, requesterId, authorization);

        Long selected = findEligibleAtLevel(
                level,
                requesterId,
                currentApproverId,
                authorization
        );

        if (selected == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No eligible approver at escalation level"
            );
        }

        return selected;
    }

    /*
     * NEW: Validate a specific configured backup.
     *
     * Do not choose an alternative user.
     * The backup must be explicitly configured
     * and authorized at the requested level.
     *
     * Return false for an ineligible backup.
     * Throw if Security Service cannot reliably
     * verify the approver.
     */
    public boolean isEligibleBackup(
            ApprovalLevel level,
            Long backupApproverId,
            Long requesterId,
            Long currentApproverId,
            String authorization
    ) {
        validate(level, requesterId, authorization);

        if (backupApproverId == null
                || backupApproverId <= 0
                || backupApproverId.equals(requesterId)
                || backupApproverId.equals(currentApproverId)) {
            return false;
        }

        Authority[] authorities = getAuthorities(
                level,
                authorization
        );

        boolean activeAuthority = Arrays.stream(authorities)
                .filter(Objects::nonNull)
                .anyMatch(authority ->
                        backupApproverId.equals(authority.userId())
                        && Boolean.TRUE.equals(authority.active())
                        && authority.approvalLevel() == level
                );

        if (!activeAuthority) {
            return false;
        }

        return isActiveFinanceApprover(
                backupApproverId,
                authorization
        );
    }

    private Long findEligibleAtLevel(
            ApprovalLevel level,
            Long requesterId,
            Long excludedApproverId,
            String authorization
    ) {
        Authority[] authorities = getAuthorities(
                level,
                authorization
        );

        var candidates = Arrays.stream(authorities)
                .filter(Objects::nonNull)
                .filter(a -> a.userId() != null)
                .filter(a -> Boolean.TRUE.equals(a.active()))
                .filter(a -> a.approvalLevel() == level)
                .filter(a -> !a.userId().equals(requesterId))
                .filter(a -> !a.userId().equals(excludedApproverId))
                .sorted(
                        Comparator.comparing(Authority::userId)
                )
                .toList();

        for (Authority candidate : candidates) {

            if (isActiveFinanceApprover(
                    candidate.userId(),
                    authorization
            )) {
                return candidate.userId();
            }
        }

        return null;
    }

    private Authority[] getAuthorities(
            ApprovalLevel level,
            String authorization
    ) {
        Authority[] authorities;

        try {
            authorities = securityClient.get()
                    .uri(
                            "/api/v1/approvers?approvalLevel={level}",
                            level.name()
                    )
                    .header("Authorization", authorization)
                    .retrieve()
                    .body(Authority[].class);

        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not retrieve approvers",
                    ex
            );
        }

        if (authorities == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Invalid approver lookup response"
            );
        }

        return authorities;
    }

    private boolean isActiveFinanceApprover(
            Long userId,
            String authorization
    ) {
        UserDetails user;

        try {
            user = securityClient.get()
                    .uri(
                            "/api/v1/users/{id}",
                            userId
                    )
                    .header("Authorization", authorization)
                    .retrieve()
                    .body(UserDetails.class);

        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not verify approver account",
                    ex
            );
        }

        if (user == null
                || !userId.equals(user.userId())
                || !"ACTIVE".equals(user.status())
                || user.roles() == null) {
            return false;
        }

        return user.roles().contains("FINANCE_APPROVER")
                || user.roles().contains("FINANCE_MANAGER");
    }

    private void validate(
            ApprovalLevel level,
            Long requesterId,
            String authorization
    ) {
        if (level == null || requesterId == null) {
            throw new IllegalArgumentException(
                    "Approval level and requester are required"
            );
        }

        if (authorization == null
                || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Machine authorization is required"
            );
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(
            ignoreUnknown = true
    )
    private record Authority(
            Long userId,
            ApprovalLevel approvalLevel,
            Boolean active
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(
            ignoreUnknown = true
    )
    private record UserDetails(
            Long userId,
            String status,
            List<String> roles
    ) {}
}

