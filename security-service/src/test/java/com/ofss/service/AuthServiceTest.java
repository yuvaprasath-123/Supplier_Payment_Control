package com.ofss.service;

import com.ofss.dto.LoginRequest;
import com.ofss.dto.MfaChallengeResponse;
import com.ofss.entity.User;
import com.ofss.entity.UserStatus;
import com.ofss.repository.UserRepository;
import com.ofss.security.AuthUtil;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthUtil authUtil;

    @Mock
    private MfaService mfaService;

    private AuthService authService;

    @BeforeEach
    void setUp() {

        authService = new AuthService(
                userRepository,
                userService,
                passwordEncoder,
                authUtil,
                mfaService
        );
    }

    @Test
    void startLoginShouldCreateMfaChallengeForValidCredentials() {

        User user = createActiveRequester();

        LoginRequest request =
                new LoginRequest(
                        "requester1",
                        "test-password"
                );

        List<String> roles =
                List.of("PAYMENT_REQUESTER");

        MfaChallengeResponse challenge =
                mock(MfaChallengeResponse.class);

        when(userRepository.findByUsername("requester1"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "test-password",
                user.getPasswordHash()
        )).thenReturn(true);

        when(userService.getRoles(1L))
                .thenReturn(roles);

        when(mfaService.createChallenge(
                1L,
                "requester1"
        )).thenReturn(challenge);

        MfaChallengeResponse result =
                authService.startLogin(request);

        assertSame(challenge, result);

        verify(mfaService).createChallenge(
                1L,
                "requester1"
        );

        // No JWT should be issued before successful OTP
        // verification.
        verifyNoInteractions(authUtil);
    }

    @Test
    void startLoginShouldRejectUnknownUsername() {

        LoginRequest request =
                new LoginRequest(
                        "unknownuser",
                        "test-password"
                );

        when(userRepository.findByUsername("unknownuser"))
                .thenReturn(Optional.empty());

        BadCredentialsException exception =
                assertThrows(
                        BadCredentialsException.class,
                        () -> authService.startLogin(request)
                );

        assertEquals(
                "Invalid username or password",
                exception.getMessage()
        );

        verifyNoInteractions(
                passwordEncoder,
                userService,
                authUtil,
                mfaService
        );
    }

    @Test
    void startLoginShouldRejectInactiveUser() {

        User user = createActiveRequester();
        user.setStatus(UserStatus.INACTIVE);

        LoginRequest request =
                new LoginRequest(
                        "requester1",
                        "test-password"
                );

        when(userRepository.findByUsername("requester1"))
                .thenReturn(Optional.of(user));

        DisabledException exception =
                assertThrows(
                        DisabledException.class,
                        () -> authService.startLogin(request)
                );

        assertEquals(
                "User account is inactive",
                exception.getMessage()
        );

        verifyNoInteractions(
                passwordEncoder,
                userService,
                authUtil,
                mfaService
        );
    }

    @Test
    void startLoginShouldRejectWrongPassword() {

        User user = createActiveRequester();

        LoginRequest request =
                new LoginRequest(
                        "requester1",
                        "wrong-password"
                );

        when(userRepository.findByUsername("requester1"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "wrong-password",
                user.getPasswordHash()
        )).thenReturn(false);

        assertThrows(
                BadCredentialsException.class,
                () -> authService.startLogin(request)
        );

        verifyNoInteractions(
                userService,
                authUtil,
                mfaService
        );
    }

    @Test
    void startLoginShouldRejectUserWithoutRoles() {

        User user = createActiveRequester();

        LoginRequest request =
                new LoginRequest(
                        "requester1",
                        "test-password"
                );

        when(userRepository.findByUsername("requester1"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "test-password",
                user.getPasswordHash()
        )).thenReturn(true);

        when(userService.getRoles(1L))
                .thenReturn(List.of());

        BadCredentialsException exception =
                assertThrows(
                        BadCredentialsException.class,
                        () -> authService.startLogin(request)
                );

        assertEquals(
                "User does not have any assigned role",
                exception.getMessage()
        );

        verifyNoInteractions(authUtil, mfaService);
    }

    private User createActiveRequester() {

        User user = new User();

        user.setId(1L);
        user.setUsername("requester1");
        user.setFullName("Requester One");
        user.setPasswordHash(
                "$2a$10$encodedPassword"
        );
        user.setStatus(UserStatus.ACTIVE);

        return user;
    }
}