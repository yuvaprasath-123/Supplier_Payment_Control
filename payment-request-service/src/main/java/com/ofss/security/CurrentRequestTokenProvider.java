package com.ofss.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

@Component
public class CurrentRequestTokenProvider {

    private final HttpServletRequest request;

    public CurrentRequestTokenProvider(
            HttpServletRequest request
    ) {

        this.request =
                request;
    }

    public String getAuthorizationHeader() {

        return request.getHeader(
                "Authorization"
        );
    }
}