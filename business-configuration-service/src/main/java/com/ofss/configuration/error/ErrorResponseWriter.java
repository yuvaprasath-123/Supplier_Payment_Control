package com.ofss.configuration.error;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ErrorResponseWriter implements com.ofss.internal.InternalErrorWriter {
    private static final String BEARER_CHALLENGE = "Bearer realm=\"business-configuration-service\"";
    private final JsonMapper mapper;

    public ErrorResponseWriter(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public void unauthorized(HttpServletRequest request, HttpServletResponse response,
            boolean invalidToken) throws IOException {
        // RFC 6750: do not advertise a token error when no Bearer credential was
        // supplied (including unsupported schemes). Never reflect token details.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE
                + (invalidToken ? ", error=\"invalid_token\"" : ""));
        write(request, response, 401, "UNAUTHORIZED",
                invalidToken ? "Valid authentication is required" : "Authentication is required");
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
            int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(),
                ApiError.of(status, code, message, request.getRequestURI()));
    }
}
