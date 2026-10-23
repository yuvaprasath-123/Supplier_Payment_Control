package com.ofss.service;

import java.time.LocalDateTime;

public class MfaChallenge {

    private final Long userId;
    private final String otp;
    private final LocalDateTime expiresAt;

    private int attempts;
    private boolean used;

    public MfaChallenge(
            Long userId,
            String otp,
            LocalDateTime expiresAt
    ) {

        this.userId = userId;
        this.otp = otp;
        this.expiresAt = expiresAt;
        this.attempts = 0;
        this.used = false;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOtp() {
        return otp;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(
            boolean used
    ) {
        this.used = used;
    }
}