package com.ofss.configuration.security;

public record JwtPrincipal(
        long userId,
        String username) {
}