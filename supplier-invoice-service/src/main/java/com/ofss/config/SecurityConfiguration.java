package com.ofss.config;

import com.ofss.security.CustomAccessDeniedHandler;

import com.ofss.security.JwtAuthFilter;

import com.ofss.security.JwtAuthenticationEntryPoint;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.config.Customizer;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    private final JwtAuthFilter
            jwtAuthFilter;

    private final JwtAuthenticationEntryPoint
            jwtAuthenticationEntryPoint;

    private final CustomAccessDeniedHandler
            customAccessDeniedHandler;

    public SecurityConfiguration(
            JwtAuthFilter jwtAuthFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            CustomAccessDeniedHandler customAccessDeniedHandler
    ) {

        this.jwtAuthFilter =
                jwtAuthFilter;

        this.jwtAuthenticationEntryPoint =
                jwtAuthenticationEntryPoint;

        this.customAccessDeniedHandler =
                customAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain
    securityFilterChain(
            HttpSecurity http
    )
            throws Exception {

        http

                .csrf(
                        csrf ->
                                csrf.disable()
                )

                .cors(
                        Customizer
                                .withDefaults()
                )

                .sessionManagement(
                        session ->
                                session
                                        .sessionCreationPolicy(
                                                SessionCreationPolicy
                                                        .STATELESS
                                        )
                )

                .exceptionHandling(
                        exception ->
                                exception

                                        .authenticationEntryPoint(
                                                jwtAuthenticationEntryPoint
                                        )

                                        .accessDeniedHandler(
                                                customAccessDeniedHandler
                                        )
                )

                .headers(
                        headers ->
                                headers

                                        .contentTypeOptions(
                                                Customizer
                                                        .withDefaults()
                                        )

                                        .frameOptions(
                                                frame ->
                                                        frame.deny()
                                        )

                                        .referrerPolicy(
                                                referrer ->
                                                        referrer.policy(
                                                                ReferrerPolicyHeaderWriter
                                                                        .ReferrerPolicy
                                                                        .STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                                                        )
                                        )

                                        .contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; "
                                                                        +
                                                                "frame-ancestors 'none'; "
                                                                        +
                                                                "object-src 'none';"
                                                        )
                                        )
                )

                .authorizeHttpRequests(
                        auth ->
                                auth

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/suppliers/**",
                                                "/api/v1/invoices/**"
                                        )
                                        .hasAnyRole(
                                                "PAYMENT_REQUESTER",
                                                "FINANCE_APPROVER",
                                                "FINANCE_MANAGER"
                                        )

                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/suppliers/**",
                                                "/api/v1/invoices/**"
                                        )
                                        .hasRole(
                                                "PAYMENT_REQUESTER"
                                        )

                                        .requestMatchers(
                                                HttpMethod.PUT,
                                                "/api/v1/suppliers/**",
                                                "/api/v1/invoices/**"
                                        )
                                        .hasRole(
                                                "PAYMENT_REQUESTER"
                                        )

                                        .requestMatchers(
                                                HttpMethod.PATCH,
                                                "/api/v1/suppliers/**",
                                                "/api/v1/invoices/**"
                                        )
                                        .hasRole(
                                                "PAYMENT_REQUESTER"
                                        )

                                        .anyRequest()
                                        .authenticated()
                )

                .addFilterBefore(
                        jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}