package com.ofss.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Component;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter
        extends OncePerRequestFilter {

    private final AuthUtil authUtil;

    public JwtAuthFilter(
            AuthUtil authUtil
    ) {
        this.authUtil = authUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    )
            throws ServletException,
            IOException {

        String authorizationHeader =
                request.getHeader("Authorization");

        if (
                authorizationHeader != null
                &&
                authorizationHeader.startsWith("Bearer ")
        ) {

            try {

                String token =
                        authorizationHeader.substring(7);

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

            } catch (Exception ex) {

                SecurityContextHolder
                        .clearContext();

                ex.printStackTrace();

                response.setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );

                response.setContentType(
                        "application/json"
                );

                response.getWriter().write(
                        """
                        {
                          "status": "UNAUTHORIZED",
                          "message": "Invalid or expired JWT token"
                        }
                        """
                );

                return;
            }
        }

        filterChain.doFilter(
                request,
                response
        );
    }
}