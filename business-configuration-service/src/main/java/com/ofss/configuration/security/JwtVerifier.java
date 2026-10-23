package com.ofss.configuration.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class JwtVerifier {

    private final JwtParser parser;

    public JwtVerifier(
            @Value("${security.jwt.secret}")
            String secret) {

        if (secret == null
                || secret.isBlank()) {

            throw new IllegalStateException(
                    "External JWT signing configuration is required"
            );
        }

        parser =
                Jwts.parser()
                        .verifyWith(
                                Keys.hmacShaKeyFor(
                                        secret.getBytes(
                                                StandardCharsets.UTF_8
                                        )
                                )
                        )
                        .build();
    }

    public UsernamePasswordAuthenticationToken authenticate(
            String token) {

        var signed =
                parser.parseSignedClaims(token);

        Claims claims =
                signed.getPayload();

        if ("service+jwt".equals(
                    signed.getHeader().getType())
                || "SERVICE".equals(
                        claims.get(
                                "principalType"
                        ))) {

            throw new IllegalArgumentException(
                    "User authentication is required"
            );
        }

        Date expiry =
                claims.getExpiration();

        if (expiry == null
                || !expiry.after(
                        new Date()
                )) {

            throw new IllegalArgumentException(
                    "A future expiry is required"
            );
        }

        String subject =
                claims.getSubject();

        if (subject == null
                || subject.isBlank()) {

            throw new IllegalArgumentException(
                    "A nonblank subject is required"
            );
        }

        long userId =
                positiveLong(
                        claims.get("userId")
                );

        Object roleClaim =
                claims.get("roles");

        if (roleClaim != null
                && !(roleClaim instanceof List<?>)) {

            throw new IllegalArgumentException(
                    "Roles must be a list"
            );
        }

        List<?> roles =
                roleClaim == null
                        ? List.of()
                        : (List<?>) roleClaim;

        var authorities =
                roles.stream()
                        .map(
                                role -> {

                                    if (!(role instanceof String name)
                                            || name.isBlank()) {

                                        throw new
                                            IllegalArgumentException(
                                                    "Roles must contain nonblank strings"
                                            );
                                    }

                                    return new
                                            SimpleGrantedAuthority(
                                                    "ROLE_" + name
                                            );
                                }
                        )
                        .toList();

        return UsernamePasswordAuthenticationToken
                .authenticated(
                        new JwtPrincipal(
                                userId,
                                subject
                        ),
                        null,
                        authorities
                );
    }

    private static long positiveLong(
            Object value) {

        try {

            long parsed;

            if (value instanceof Float
                    || value instanceof Double) {

                throw new IllegalArgumentException(
                        "Invalid user identity"
                );

            } else if (value instanceof Number number) {

                parsed =
                        new BigDecimal(
                                number.toString()
                        ).longValueExact();

            } else if (value instanceof String text
                    && text.matches(
                            "[0-9]+"
                    )) {

                parsed =
                        Long.parseLong(text);

            } else {

                throw new IllegalArgumentException(
                        "Invalid user identity"
                );
            }

            if (parsed <= 0) {

                throw new IllegalArgumentException(
                        "Invalid user identity"
                );
            }

            return parsed;

        } catch (
                ArithmeticException
                | NumberFormatException exception
        ) {

            throw new IllegalArgumentException(
                    "Invalid user identity"
            );
        }
    }
}
