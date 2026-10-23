package com.ofss.integration;

import com.ofss.entity.User;
import com.ofss.entity.UserStatus;

import com.ofss.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.http.MediaType;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JsonMapper objectMapper;

    private static final String USERNAME =
            "integration_requester";

    private static final String PASSWORD =
            "Integration@123";

    @BeforeEach
    void setUp() {

        userRepository
                .findByUsername(USERNAME)
                .ifPresent(
                        userRepository::delete
                );

        User user =
                new User();

        user.setUsername(
                USERNAME
        );

        user.setPasswordHash(
                passwordEncoder.encode(
                        PASSWORD
                )
        );

        user.setFullName(
                "Integration Requester"
        );

        user.setEmail(
                "integration.requester@test.com"
        );

        user.setStatus(
                UserStatus.ACTIVE
        );

        userRepository.save(
                user
        );
    }

    @AfterEach
    void tearDown() {

        userRepository
                .findByUsername(USERNAME)
                .ifPresent(
                        userRepository::delete
                );
    }

    @Test
    void validLoginShouldReturnAccessToken()
            throws Exception {

        String requestBody =
                """
                {
                    "username": "integration_requester",
                    "password": "Integration@123"
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/auth/login"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )

                .andExpect(
                        status().isOk()
                )

                .andExpect(
                        jsonPath(
                                "$.accessToken"
                        ).isNotEmpty()
                )

                .andExpect(
                        jsonPath(
                                "$.tokenType"
                        ).value(
                                "Bearer"
                        )
                )

                .andExpect(
                        jsonPath(
                                "$.username"
                        ).value(
                                USERNAME
                        )
                )

                .andExpect(
                        jsonPath(
                                "$.fullName"
                        ).value(
                                "Integration Requester"
                        )
                );
    }

    @Test
    void wrongPasswordShouldFailLogin()
            throws Exception {

        String requestBody =
                """
                {
                    "username": "integration_requester",
                    "password": "WrongPassword"
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/auth/login"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )

                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void protectedEndpointWithoutTokenShouldReturnUnauthorized()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/auth/me"
                        )
                )

                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void validJwtShouldAllowAccessToMeEndpoint()
            throws Exception {

        String requestBody =
                """
                {
                    "username": "integration_requester",
                    "password": "Integration@123"
                }
                """;

        String loginResponse =
                mockMvc.perform(
                                post(
                                        "/api/v1/auth/login"
                                )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                requestBody
                                        )
                        )

                        .andExpect(
                                status().isOk()
                        )

                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode json =
                objectMapper.readTree(
                        loginResponse
                );

        String accessToken =
                json
                        .get(
                                "accessToken"
                        )
                        .asText();

        assertNotNull(
                accessToken
        );

        mockMvc.perform(
                        get(
                                "/api/v1/auth/me"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + accessToken
                                )
                )

                .andExpect(
                        status().isOk()
                )

                .andExpect(
                        jsonPath(
                                "$.username"
                        ).value(
                                USERNAME
                        )
                )

                .andExpect(
                        jsonPath(
                                "$.fullName"
                        ).value(
                                "Integration Requester"
                        )
                )

                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "ACTIVE"
                        )
                );
    }
    
    @Test
    void invalidJwtShouldReturnUnauthorized()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/auth/me"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer invalid.jwt.token"
                                )
                )

                .andExpect(
                        status().isUnauthorized()
                );
    }
}