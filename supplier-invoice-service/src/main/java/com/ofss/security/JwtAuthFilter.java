package com.ofss.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Component;

import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
public class JwtAuthFilter
        extends OncePerRequestFilter {

    private final AuthUtil authUtil;

    private final HandlerExceptionResolver
            handlerExceptionResolver;

    public JwtAuthFilter(
            AuthUtil authUtil,

            @Qualifier(
                    "handlerExceptionResolver"
            )
            HandlerExceptionResolver
                    handlerExceptionResolver
    ) {

        this.authUtil =
                authUtil;

        this.handlerExceptionResolver =
                handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    )
            throws ServletException,
            IOException {

        try {

            String authHeader =
                    request.getHeader(
                            "Authorization"
                    );

            if (
                    authHeader == null
                            ||
                    !authHeader.startsWith(
                            "Bearer "
                    )
            ) {

                filterChain.doFilter(
                        request,
                        response
                );

                return;
            }

            String token =
                    authHeader.substring(
                            7
                    );

            JwtUserPrinciple principal =
                    authUtil.verifyJwtToken(
                            token
                    );

            if (
                    SecurityContextHolder
                            .getContext()
                            .getAuthentication()
                            == null
            ) {

                UsernamePasswordAuthenticationToken
                        authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.authorities()
                        );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(
                                authentication
                        );
            }

            filterChain.doFilter(
                    request,
                    response
            );

        } catch (
                Exception ex
        ) {

            handlerExceptionResolver
                    .resolveException(
                            request,
                            response,
                            null,
                            ex
                    );
        }
    }
}