package com.ofss.controller;

import com.ofss.dto.LoginRequest;
import com.ofss.security.AuthUtil;
import com.ofss.service.AuthService;
import com.ofss.service.UserService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(AuthControllerTest.TestSecurityConfiguration.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthUtil authUtil;

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http
        ) throws Exception {

            http
                    .csrf(csrf -> csrf.disable())
                    .exceptionHandling(exception ->
                            exception.authenticationEntryPoint(
                                    (request, response, error) ->
                                            response.setStatus(401)
                            )
                    )
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/api/v1/auth/login",
                                    "/api/v1/auth/verify-otp"
                            )
                            .permitAll()
                            .anyRequest()
                            .authenticated()
                    )
                    .httpBasic(basic -> basic.disable())
                    .formLogin(form -> form.disable());

            return http.build();
        }
    }

    @Test
    void blankPasswordShouldReturnBadRequest()
            throws Exception {

        LoginRequest request =
                new LoginRequest("requester1", "");

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        request
                                )
                        )
        ).andExpect(status().isBadRequest());
    }

    @Test
    void blankUsernameShouldReturnBadRequest()
            throws Exception {

        LoginRequest request =
                new LoginRequest("", "test-password");

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        request
                                )
                        )
        ).andExpect(status().isBadRequest());
    }

    @Test
    void loginShouldStartMfaChallenge()
            throws Exception {

        LoginRequest request =
                new LoginRequest(
                        "requester1",
                        "test-password"
                );

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        request
                                )
                        )
        ).andExpect(status().isOk());

        // The controller starts MFA; it does not issue
        // the access token during this request.
        verify(authService).startLogin(
                any(LoginRequest.class)
        );
    }

    @Test
    void meShouldReturnUnauthorizedWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                get("/api/v1/auth/me")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldReturnAuthenticatedUser()
            throws Exception {

        when(authUtil.getCurrentUserId())
                .thenReturn(1L);

        // Use a valid authenticated request to verify
        // the controller calls the trusted identity lookup.
        // A real response fixture can be added separately
        // once the UserResponse contract is included.

        mockMvc.perform(
                get("/api/v1/auth/me")
                        .with(
                                user("requester1")
                                        .roles("PAYMENT_REQUESTER")
                        )
        ).andExpect(status().isOk());

        verify(authUtil).getCurrentUserId();
        verify(userService).getUser(1L);
    }
}