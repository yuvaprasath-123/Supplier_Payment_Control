package com.ofss.configuration.security;

import com.ofss.configuration.error.ErrorResponseWriter;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private static final Pattern BEARER_CREDENTIAL =
            Pattern.compile(
                    "(?i:Bearer) +([^\\s]+)"
            );

    private final JwtVerifier verifier;

    private final ErrorResponseWriter errors;

    public JwtAuthenticationFilter(
            JwtVerifier verifier,
            ErrorResponseWriter errors) {

        this.verifier = verifier;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String header =
                request.getHeader(
                        "Authorization"
                );

        if (header != null) {

            boolean bearerScheme =
                    header.length() >= 6
                            && header.regionMatches(
                                    true,
                                    0,
                                    "Bearer",
                                    0,
                                    6
                            )
                            && (
                                header.length() == 6
                                || header.charAt(6) == ' '
                            );

            if (!bearerScheme
                    || header.substring(6).isBlank()) {

                SecurityContextHolder
                        .clearContext();

                errors.unauthorized(
                        request,
                        response,
                        false
                );

                return;
            }

            try {

                var credential =
                        BEARER_CREDENTIAL
                                .matcher(header);

                if (!credential.matches()) {

                    throw new
                        IllegalArgumentException(
                                "Invalid authentication header"
                        );
                }

                var authentication =
                        verifier.authenticate(
                                credential.group(1)
                        );

                var context =
                        SecurityContextHolder
                                .createEmptyContext();

                context.setAuthentication(
                        authentication
                );

                SecurityContextHolder
                        .setContext(context);

            } catch (
                    JwtException
                    | IllegalArgumentException exception
            ) {

                SecurityContextHolder
                        .clearContext();

                errors.unauthorized(
                        request,
                        response,
                        true
                );

                return;
            }
        }

        chain.doFilter(
                request,
                response
        );
    }
}
