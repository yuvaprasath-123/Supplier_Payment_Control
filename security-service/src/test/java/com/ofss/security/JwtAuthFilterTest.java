package com.ofss.security;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private AuthUtil authUtil;

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {

    	jwtAuthFilter =
    	        new JwtAuthFilter(
    	                authUtil,
    	                handlerExceptionResolver
    	        );

        ReflectionTestUtils.setField(
                jwtAuthFilter,
                "handlerExceptionResolver",
                handlerExceptionResolver
        );

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerTokenShouldAuthenticateUser()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Authorization",
                "Bearer valid-token"
        );

        JwtUserPrinciple principal =
                new JwtUserPrinciple(
                        1L,
                        "requester1",
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_PAYMENT_REQUESTER"
                                )
                        )
                );

        when(
                authUtil.verifyJwtToken(
                        "valid-token"
                )
        ).thenReturn(
                principal
        );

        jwtAuthFilter.doFilter(
                request,
                response,
                filterChain
        );

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertNotNull(
                authentication
        );

        assertTrue(
                authentication.isAuthenticated()
        );

        assertEquals(
                principal,
                authentication.getPrincipal()
        );

        assertEquals(
                "requester1",
                ((JwtUserPrinciple)
                        authentication
                                .getPrincipal())
                        .username()
        );

        assertTrue(
                authentication
                        .getAuthorities()
                        .stream()
                        .anyMatch(
                                authority ->
                                        authority
                                                .getAuthority()
                                                .equals(
                                                        "ROLE_PAYMENT_REQUESTER"
                                                )
                        )
        );

        verify(
                authUtil
        ).verifyJwtToken(
                "valid-token"
        );

        verify(
                filterChain
        ).doFilter(
                request,
                response
        );
    }

    @Test
    void missingAuthorizationHeaderShouldContinueWithoutAuthentication()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        jwtAuthFilter.doFilter(
                request,
                response,
                filterChain
        );

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertNull(
                authentication
        );

        verify(
                authUtil,
                never()
        ).verifyJwtToken(
                anyString()
        );

        verify(
                filterChain
        ).doFilter(
                request,
                response
        );
    }

    @Test
    void nonBearerAuthorizationHeaderShouldContinueWithoutAuthentication()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Authorization",
                "Basic abc123"
        );

        jwtAuthFilter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(
                authUtil,
                never()
        ).verifyJwtToken(
                anyString()
        );

        verify(
                filterChain
        ).doFilter(
                request,
                response
        );
    }

    @Test
    void invalidBearerTokenShouldUseExceptionResolver()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Authorization",
                "Bearer invalid-token"
        );

        RuntimeException exception =
                new RuntimeException(
                        "Invalid JWT"
                );

        when(
                authUtil.verifyJwtToken(
                        "invalid-token"
                )
        ).thenThrow(
                exception
        );

        jwtAuthFilter.doFilter(
                request,
                response,
                filterChain
        );

        verify(
                handlerExceptionResolver
        ).resolveException(
                request,
                response,
                null,
                exception
        );

        verify(
                filterChain,
                never()
        ).doFilter(
                request,
                response
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );
    }

    @Test
    void existingAuthenticationShouldNotBeOverwritten()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Authorization",
                "Bearer valid-token"
        );

        UsernamePasswordAuthenticationToken existingAuthentication =
                new UsernamePasswordAuthenticationToken(
                        "existing-user",
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_EXISTING"
                                )
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        existingAuthentication
                );

        JwtUserPrinciple principal =
                new JwtUserPrinciple(
                        1L,
                        "requester1",
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_PAYMENT_REQUESTER"
                                )
                        )
                );

        when(
                authUtil.verifyJwtToken(
                        "valid-token"
                )
        ).thenReturn(
                principal
        );

        jwtAuthFilter.doFilter(
                request,
                response,
                filterChain
        );

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertSame(
                existingAuthentication,
                authentication
        );

        assertEquals(
                "existing-user",
                authentication
                        .getPrincipal()
        );

        verify(
                authUtil
        ).verifyJwtToken(
                "valid-token"
        );

        verify(
                filterChain
        ).doFilter(
                request,
                response
        );
    }
}