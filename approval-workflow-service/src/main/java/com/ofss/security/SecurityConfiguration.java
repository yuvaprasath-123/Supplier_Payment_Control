package com.ofss.security;

import jakarta.servlet.DispatcherType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {

    private final String jwtSecret;

    public SecurityConfiguration(
            @Value("${jwt.secret-key}") String jwtSecret
    ) {
        this.jwtSecret = jwtSecret;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )
            .authorizeHttpRequests(auth -> auth

                .dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()

                /*
                 * Health check only.
                 * Other actuator endpoints remain protected.
                 */
                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health"
                )
                .permitAll()

                /*
                 * Machine-only approval assignment endpoint.
                 * Existing authorization is preserved.
                 */
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/internal/approval-assignments/*"
                )
                .hasRole("APPROVAL_ASSIGNMENT_SERVICE")

                /*
                 * NEW: Read-only payment SLA tracker.
                 *
                 * This rule permits the three human roles
                 * to reach the controller.
                 *
                 * The controller independently verifies:
                 * - requester owns the payment;
                 * - approver is currently assigned;
                 * - finance manager has manager authority.
                 *
                 * Machine-only accounts cannot access it.
                 */
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/payments/*/sla-tracker"
                )
                .hasAnyRole(
                    "PAYMENT_REQUESTER",
                    "FINANCE_APPROVER",
                    "FINANCE_MANAGER"
                )

                /*
                 * Existing manual workflow creation.
                 */
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/approval-routes",
                    "/api/v1/approval-tasks",
                    "/api/v1/approval-workflows"
                )
                .hasRole("FINANCE_MANAGER")

                /*
                 * Existing human approval APIs.
                 */
                .requestMatchers("/api/v1/**")
                .hasAnyRole(
                    "FINANCE_MANAGER",
                    "FINANCE_APPROVER"
                )

                /*
                 * Deny all unmatched requests.
                 */
                .anyRequest()
                .denyAll()
            )
            .addFilterBefore(
                new ApprovalJwtFilter(jwtSecret),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    private static class ApprovalJwtFilter
            extends OncePerRequestFilter {

        private final SecretKey signingKey;

        ApprovalJwtFilter(String secret) {
            this.signingKey = Keys.hmacShaKeyFor(
                    secret.getBytes(StandardCharsets.UTF_8)
            );
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain chain
        ) throws ServletException, IOException {

            String header =
                    request.getHeader("Authorization");

            if (header != null
                    && header.startsWith("Bearer ")) {

                try {
                    String token = header.substring(7);

                    Claims claims = Jwts.parser()
                            .verifyWith(signingKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

                    Object userIdClaim =
                            claims.get("userId");

                    if (userIdClaim == null) {
                        throw new IllegalArgumentException(
                                "JWT userId is missing"
                        );
                    }

                    Long userId = Long.valueOf(
                            userIdClaim.toString()
                    );

                    String username = claims.getSubject();

                    if (username == null
                            || username.isBlank()) {
                        throw new IllegalArgumentException(
                                "JWT subject is missing"
                        );
                    }

                    List<GrantedAuthority> authorities =
                            new ArrayList<>();

                    Object rolesClaim = claims.get("roles");

                    if (rolesClaim instanceof List<?> roles) {
                        for (Object role : roles) {
                            if (role instanceof String roleName) {
                                authorities.add(
                                    new SimpleGrantedAuthority(
                                        "ROLE_" + roleName
                                    )
                                );
                            }
                        }
                    }

                    /*
                     * Preserve the existing assignment-service
                     * machine identity verification.
                     */
                    boolean isMachineRole =
                            authorities.stream().anyMatch(
                                authority ->
                                    "ROLE_APPROVAL_ASSIGNMENT_SERVICE"
                                        .equals(
                                            authority.getAuthority()
                                        )
                            );

                    if (isMachineRole &&
                            (!"risk-assessment-service".equals(
                                    username
                                )
                                || !Long.valueOf(0L).equals(
                                    userId
                                )
                                || authorities.size() != 1)) {
                        throw new IllegalArgumentException(
                                "Invalid machine identity"
                        );
                    }

                    JwtPrincipal principal =
                            new JwtPrincipal(
                                userId,
                                username
                            );

                    var authentication =
                            new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                authorities
                            );

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authentication);

                } catch (Exception ex) {
                    SecurityContextHolder.clearContext();

                    response.setStatus(
                            HttpServletResponse.SC_UNAUTHORIZED
                    );

                    response.setContentType(
                            "application/json"
                    );

                    response.getWriter().write(
                            "{\"status\":\"UNAUTHORIZED\","
                            + "\"message\":\"Invalid or expired JWT\"}"
                    );

                    return;
                }
            }

            chain.doFilter(request, response);
        }
    }

    public record JwtPrincipal(
            Long userId,
            String username
    ) {}
}