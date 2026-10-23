package com.ofss.service;

import com.ofss.dto.MfaChallengeResponse;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

import java.time.LocalDateTime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MfaService {

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom secureRandom =
            new SecureRandom();

    private final Map<String, MfaChallenge>
            challenges =
                new ConcurrentHashMap<>();

    public MfaChallengeResponse createChallenge(
            Long userId,
            String username
    ) {

        String challengeId =
                UUID.randomUUID()
                        .toString();

        String otp =
                String.format(
                        "%06d",
                        secureRandom.nextInt(
                                1_000_000
                        )
                );

        LocalDateTime expiresAt =
                LocalDateTime.now()
                        .plusMinutes(
                                OTP_EXPIRY_MINUTES
                        );

        MfaChallenge challenge =
                new MfaChallenge(
                        userId,
                        otp,
                        expiresAt
                );

        challenges.put(
                challengeId,
                challenge
        );

        System.out.println();
        System.out.println(
                "========================================"
        );
        System.out.println(
                "MFA OTP - DEMO MODE"
        );
        System.out.println(
                "Username     : "
                        + username
        );
        System.out.println(
                "Challenge ID : "
                        + challengeId
        );
        System.out.println(
                "OTP          : "
                        + otp
        );
        System.out.println(
                "Expires At   : "
                        + expiresAt
        );
        System.out.println(
                "========================================"
        );
        System.out.println();

        return new MfaChallengeResponse(
                true,
                challengeId,
                OTP_EXPIRY_MINUTES * 60L
        );
    }

    public Long verifyChallenge(
            String challengeId,
            String otp
    ) {

        MfaChallenge challenge =
                challenges.get(
                        challengeId
                );

        if (challenge == null) {

            throw new IllegalArgumentException(
                    "Invalid MFA challenge"
            );
        }

        if (challenge.isUsed()) {

            throw new IllegalArgumentException(
                    "OTP has already been used"
            );
        }

        if (
                LocalDateTime.now()
                        .isAfter(
                                challenge.getExpiresAt()
                        )
        ) {

            challenges.remove(
                    challengeId
            );

            throw new IllegalArgumentException(
                    "OTP has expired"
            );
        }

        if (
                challenge.getAttempts()
                        >= MAX_ATTEMPTS
        ) {

            challenges.remove(
                    challengeId
            );

            throw new IllegalArgumentException(
                    "Maximum OTP attempts exceeded"
            );
        }

        if (
                !challenge.getOtp()
                        .equals(
                                otp
                        )
        ) {

            challenge.incrementAttempts();

            throw new IllegalArgumentException(
                    "Invalid OTP"
            );
        }

        challenge.setUsed(
                true
        );

        Long userId =
                challenge.getUserId();

        challenges.remove(
                challengeId
        );

        return userId;
    }
}