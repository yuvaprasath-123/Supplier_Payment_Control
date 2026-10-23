package com.ofss.security;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class AuthUtilTest {

    private AuthUtil authUtil;

    @BeforeEach
    void setUp() {

        authUtil = new AuthUtil();

        // Test-only signing key. Never use a production secret.
        ReflectionTestUtils.setField(
                authUtil,
                "jwtSecret",
                "this-is-a-test-only-jwt-signing-key-with-sufficient-length-123456789"
        );

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generateAndVerifyTokenShouldContainUserIdAndUsername() {

        String token = authUtil.generateJwtToken(
                1L,
                "requester1",
                List.of("PAYMENT_REQUESTER")
        );

        assertNotNull(token);
        assertFalse(token.isBlank());

        JwtUserPrinciple principal =
                authUtil.verifyJwtToken(token);

        assertEquals(1L, principal.userId());
        assertEquals("requester1", principal.username());
    }

    @Test
    void generateAndVerifyTokenShouldContainRequesterRole() {

        String token = authUtil.generateJwtToken(
                1L,
                "requester1",
                List.of("PAYMENT_REQUESTER")
        );

        JwtUserPrinciple principal =
                authUtil.verifyJwtToken(token);

        List<String> authorities =
                principal.authorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();

        assertTrue(
                authorities.contains("ROLE_PAYMENT_REQUESTER")
        );
    }

    @Test
    void generateAndVerifyTokenShouldContainFinanceApproverRole() {

        String token = authUtil.generateJwtToken(
                2L,
                "approver1",
                List.of("FINANCE_APPROVER")
        );

        JwtUserPrinciple principal =
                authUtil.verifyJwtToken(token);

        List<String> authorities =
                principal.authorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();

        assertTrue(
                authorities.contains("ROLE_FINANCE_APPROVER")
        );
    }

    @Test
    void generateAndVerifyTokenShouldContainFinanceManagerRole() {

        String token = authUtil.generateJwtToken(
                3L,
                "financemanager1",
                List.of("FINANCE_MANAGER")
        );

        JwtUserPrinciple principal =
                authUtil.verifyJwtToken(token);

        List<String> authorities =
                principal.authorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();

        assertTrue(
                authorities.contains("ROLE_FINANCE_MANAGER")
        );
    }

    @Test
    void verifyJwtTokenShouldRejectInvalidToken() {

        assertThrows(
                Exception.class,
                () -> authUtil.verifyJwtToken(
                        "invalid.jwt.token"
                )
        );
    }

    @Test
    void getCurrentUserIdShouldReturnAuthenticatedUserId() {

        JwtUserPrinciple principal =
                new JwtUserPrinciple(
                        5L,
                        "requester5",
                        List.of()
                );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.authorities()
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        assertEquals(
                5L,
                authUtil.getCurrentUserId()
        );
    }

    @Test
    void getCurrentUserIdShouldRejectWhenNotAuthenticated() {

        SecurityContextHolder.clearContext();

        assertThrows(
                IllegalStateException.class,
                () -> authUtil.getCurrentUserId()
        );
    }

    @Test
    void getCurrentUserRolesShouldReturnRolesWithoutPrefix() {

        String token = authUtil.generateJwtToken(
                3L,
                "financemanager1",
                List.of("FINANCE_MANAGER", "FINANCE_APPROVER")
        );

        JwtUserPrinciple principal =
                authUtil.verifyJwtToken(token);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.authorities()
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        List<String> roles = authUtil.getCurrentUserRoles();

        assertTrue(roles.contains("FINANCE_MANAGER"));
        assertTrue(roles.contains("FINANCE_APPROVER"));

        assertFalse(
                roles.contains("ROLE_FINANCE_MANAGER")
        );
    }
}