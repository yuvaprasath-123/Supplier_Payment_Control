
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    public SecurityConfiguration(
            JwtAuthFilter jwtAuthFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            CustomAccessDeniedHandler customAccessDeniedHandler
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.jwtAuthenticationEntryPoint =
                jwtAuthenticationEntryPoint;
        this.customAccessDeniedHandler =
                customAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
            .cors(cors -> {})
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .exceptionHandling(exception ->
                exception
                    .authenticationEntryPoint(
                        jwtAuthenticationEntryPoint
                    )
                    .accessDeniedHandler(
                        customAccessDeniedHandler
                    )
            )

            .headers(headers ->
                headers
                    .contentTypeOptions(
                        Customizer.withDefaults()
                    )
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(referrer ->
                        referrer.policy(
                            ReferrerPolicyHeaderWriter
                                .ReferrerPolicy
                                .STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                        )
                    )
                    .contentSecurityPolicy(csp ->
                        csp.policyDirectives(
                            "default-src 'self'; "
                            + "frame-ancestors 'none'; "
                            + "object-src 'none';"
                        )
                    )
            )

            .authorizeHttpRequests(auth -> auth

                // Existing human login endpoints.
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/verify-otp"
                )
                .permitAll()

                // Service-token issuance authenticates
                // client credentials in the controller.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/internal/service-tokens"
                )
                .permitAll()

                // Read-only lookup permissions for
                // assignment service, SLA service,
                // and authorized human approvers.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/approvers",
                    "/api/v1/users/*"
                )
                .hasAnyRole(
                    "APPROVAL_ASSIGNMENT_SERVICE",
                    "APPROVAL_SLA_SERVICE",
                    "FINANCE_MANAGER",
                    "FINANCE_APPROVER"
                )

                // Prevent both machine identities
                // from accessing ordinary user APIs.
                .requestMatchers("/api/v1/**")
                .access((authentication, context) -> {

                    var current = authentication.get();

                    boolean authenticated =
                            current != null
                            && current.isAuthenticated();

                    boolean machineIdentity =
                            authenticated
                            && current.getAuthorities().stream()
                                .anyMatch(authority ->
                                    "ROLE_APPROVAL_ASSIGNMENT_SERVICE"
                                        .equals(authority.getAuthority())
                                    ||
                                    "ROLE_APPROVAL_SLA_SERVICE"
                                        .equals(authority.getAuthority())
                                );

                    return new org.springframework.security
                        .authorization.AuthorizationDecision(
                            authenticated && !machineIdentity
                        );
                })

                .anyRequest()
                .authenticated()
            )

            .addFilterBefore(
                jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

