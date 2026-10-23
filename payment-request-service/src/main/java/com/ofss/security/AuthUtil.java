package com.ofss.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class AuthUtil {

    @Value("${jwt.secret-key}")
    private String jwtSecret;

    private SecretKey getSigningKey() {

        return Keys.hmacShaKeyFor(
                jwtSecret.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    public String generateJwtToken(
            Long userId,
            String username,
            List<String> roles
    ) {

        long expirationTime =
                10 * 60 * 1000;

        Date now =
                new Date();

        Date expiry =
                new Date(
                        now.getTime()
                                + expirationTime
                );

        return Jwts.builder()
                .subject(username)

                .claim(
                        "userId",
                        userId
                )

                .claim(
                        "roles",
                        roles
                )

                .issuedAt(now)

                .expiration(expiry)

                .signWith(
                        getSigningKey()
                )

                .compact();
    }
    
    public JwtUserPrinciple verifyJwtToken(String token) {

        Claims claims =
                Jwts.parser()
                        .verifyWith(
                                getSigningKey()
                        )
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

        String username =
                claims.getSubject();

        Object userIdClaim =
                claims.get("userId");

        if (userIdClaim == null) {
            throw new IllegalStateException(
                    "JWT does not contain userId claim"
            );
        }

        Long userId;

        if (userIdClaim instanceof Number) {

            userId =
                    ((Number) userIdClaim)
                            .longValue();

        } else {

            try {

                userId =
                        Long.valueOf(
                                userIdClaim.toString()
                        );

            } catch (NumberFormatException ex) {

                throw new IllegalStateException(
                        "JWT does not contain a valid userId claim",
                        ex
                );
            }
        }

        List<?> roles =
                claims.get(
                        "roles",
                        List.class
                );

        List<GrantedAuthority> authorities =
                new ArrayList<>();

        if (roles != null) {

            for (Object role : roles) {

                authorities.add(
                        new SimpleGrantedAuthority(
                                "ROLE_" + role.toString()
                        )
                );
            }
        }

        return new JwtUserPrinciple(
                userId,
                username,
                authorities
        );
    }
    public JwtUserPrinciple getCurrentUser() {

        if (
                SecurityContextHolder
                        .getContext()
                        .getAuthentication() == null
        ) {

            throw new IllegalStateException(
                    "Authenticated user not found"
            );
        }

        Object principal =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        if (!(principal instanceof JwtUserPrinciple)) {

            throw new IllegalStateException(
                    "Authenticated user not found"
            );
        }

        return (JwtUserPrinciple) principal;
    }

    public Long getCurrentUserId() {

        return getCurrentUser()
                .userId();
    }
}