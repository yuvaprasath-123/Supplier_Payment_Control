package com.ofss.dto;

public record MfaChallengeResponse(
        boolean mfaRequired,
        String challengeId,
        long expiresInSeconds
) {
}