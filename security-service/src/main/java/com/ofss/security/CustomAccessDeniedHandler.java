package com.ofss.security;

import tools.jackson.databind.ObjectMapper;

import com.ofss.exception.ApiError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;



import org.springframework.http.HttpStatus;

import org.springframework.security.access.AccessDeniedException;

import org.springframework.security.web.access.AccessDeniedHandler;

import org.springframework.stereotype.Component;

import java.io.IOException;

@Component

public class CustomAccessDeniedHandler
        implements AccessDeniedHandler {

    private ObjectMapper objectMapper;
   
    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    )
            throws IOException {

        ApiError apiError =
                new ApiError(
                        HttpStatus.FORBIDDEN,
                        "You do not have permission to access this resource"
                );

        response.setStatus(
                HttpStatus.FORBIDDEN.value()
        );

        response.setContentType(
                "application/json"
        );

        objectMapper.writeValue(
                response.getOutputStream(),
                apiError
        );
    }
}