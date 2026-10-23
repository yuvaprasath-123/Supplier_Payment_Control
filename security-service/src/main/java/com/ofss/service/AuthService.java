package com.ofss.service;

import com.ofss.dto.LoginRequest;
import com.ofss.dto.LoginResponse;
import com.ofss.dto.MfaChallengeResponse;
import com.ofss.dto.VerifyOtpRequest;

import com.ofss.entity.User;
import com.ofss.entity.UserStatus;

import com.ofss.repository.UserRepository;

import com.ofss.security.AuthUtil;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

    private final UserRepository
            userRepository;

    private final UserService
            userService;

    private final PasswordEncoder
            passwordEncoder;

    private final AuthUtil
            authUtil;

    private final MfaService
            mfaService;

    public AuthService(
            UserRepository userRepository,
            UserService userService,
            PasswordEncoder passwordEncoder,
            AuthUtil authUtil,
            MfaService mfaService
    ) {

        this.userRepository =
                userRepository;

        this.userService =
                userService;

        this.passwordEncoder =
                passwordEncoder;

        this.authUtil =
                authUtil;

        this.mfaService =
                mfaService;
    }

    public MfaChallengeResponse startLogin(
            LoginRequest request
    ) {

        User user =
                userRepository
                        .findByUsername(
                                request.username()
                        )
                        .orElseThrow(
                                () ->
                                        new BadCredentialsException(
                                                "Invalid username or password"
                                        )
                        );

        if (
                user.getStatus()
                        != UserStatus.ACTIVE
        ) {

            throw new DisabledException(
                    "User account is inactive"
            );
        }

        if (
                !passwordEncoder.matches(
                        request.password(),
                        user.getPasswordHash()
                )
        ) {

            throw new BadCredentialsException(
                    "Invalid username or password"
            );
        }

        List<String> roles =
                userService
                        .getRoles(
                                user.getId()
                        );

        if (
                roles == null
                        ||
                        roles.isEmpty()
        ) {

            throw new BadCredentialsException(
                    "User does not have any assigned role"
            );
        }

        return mfaService
                .createChallenge(
                        user.getId(),
                        user.getUsername()
                );
    }

    public LoginResponse verifyOtp(
            VerifyOtpRequest request
    ) {

        Long userId =
                mfaService
                        .verifyChallenge(
                                request.challengeId(),
                                request.otp()
                        );

        User user =
                userRepository
                        .findById(
                                userId
                        )
                        .orElseThrow(
                                () ->
                                        new BadCredentialsException(
                                                "User not found"
                                        )
                        );

        if (
                user.getStatus()
                        != UserStatus.ACTIVE
        ) {

            throw new DisabledException(
                    "User account is inactive"
            );
        }

        List<String> roles =
                userService
                        .getRoles(
                                user.getId()
                        );

        if (
                roles == null
                        ||
                        roles.isEmpty()
        ) {

            throw new BadCredentialsException(
                    "User does not have any assigned role"
            );
        }

        String accessToken =
                authUtil
                        .generateJwtToken(
                                user.getId(),
                                user.getUsername(),
                                roles
                        );

        return new LoginResponse(
                accessToken,
                "Bearer",
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                roles
        );
    }
}