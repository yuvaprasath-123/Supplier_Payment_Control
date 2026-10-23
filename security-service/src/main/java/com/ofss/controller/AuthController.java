package com.ofss.controller;

import com.ofss.dto.LoginRequest;
import com.ofss.dto.LoginResponse;
import com.ofss.dto.MfaChallengeResponse;
import com.ofss.dto.UserResponse;
import com.ofss.dto.VerifyOtpRequest;

import com.ofss.security.AuthUtil;

import com.ofss.service.AuthService;
import com.ofss.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    private final UserService userService;

    private final AuthUtil authUtil;

    public AuthController(
            AuthService authService,
            UserService userService,
            AuthUtil authUtil
    ) {

        this.authService =
                authService;

        this.userService =
                userService;

        this.authUtil =
                authUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<MfaChallengeResponse> login(
            @Valid
            @RequestBody
            LoginRequest request
    ) {

        return ResponseEntity.ok(
                authService
                        .startLogin(
                                request
                        )
        );
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<LoginResponse> verifyOtp(
            @Valid
            @RequestBody
            VerifyOtpRequest request
    ) {

        return ResponseEntity.ok(
                authService
                        .verifyOtp(
                                request
                        )
        );
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {

        Long userId =
                authUtil
                        .getCurrentUserId();

        return ResponseEntity.ok(
                userService
                        .getUser(
                                userId
                        )
        );
    }
}