package com.ofss.configuration.version;

import com.ofss.configuration.error.ApiExceptionHandler;
import com.ofss.configuration.error.ErrorResponseWriter;
import com.ofss.configuration.security.JwtVerifier;
import com.ofss.configuration.security.SecurityConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigurationVersionController.class)
@Import({ConfigurationVersionService.class, SecurityConfiguration.class, JwtVerifier.class,
        ErrorResponseWriter.class, ApiExceptionHandler.class})
class ConfigurationVersionApiTest {
    private static final String CHALLENGE = "Bearer realm=\"business-configuration-service\"";
    private static final String INVALID_TOKEN_CHALLENGE = CHALLENGE + ", error=\"invalid_token\"";
    // Random throwaway signing material exists only inside this test JVM.
    private static final String TEST_SECRET = newTestSecret();
    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            TEST_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    @Autowired private MockMvc mvc;
    @MockitoBean private ConfigurationVersionRepository repository;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry properties) {
        properties.add("security.jwt.secret", () -> TEST_SECRET);
    }

    private static String newTestSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String token(Object userId, String subject, List<String> roles,
            Instant expiry, SecretKey key) {
        var builder = Jwts.builder().subject(subject);
        if (userId != null) builder.claim("userId", userId);
        if (roles != null) builder.claim("roles", roles);
        if (expiry != null) builder.expiration(Date.from(expiry));
        return builder.signWith(key).compact();
    }

    private static String managerToken() {
        return token(1L, "manager", List.of("FINANCE_MANAGER"), Instant.now().plusSeconds(300), KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "bearer", "BEARER"})
    void managerReceivesAnEmptyListWithAnyBearerSchemeCase(String scheme) throws Exception {
        when(repository.findAll()).thenReturn(List.of());
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", scheme + " " + managerToken()))
                .andExpect(status().isOk()).andExpect(content().json("[]"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAYMENT_REQUESTER", "FINANCE_APPROVER", "ROLE_FINANCE_MANAGER"})
    void authenticatedWrongRolesAreForbidden(String role) throws Exception {
        String jwt = token(1L, "user", List.of(role), Instant.now().plusSeconds(300), KEY);
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        verifyNoInteractions(repository);
    }

    @Test
    void authenticatedUserWithoutRolesIsForbidden() throws Exception {
        String jwt = token(1L, "user", null, Instant.now().plusSeconds(300), KEY);
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void missingAuthenticationIsUnauthorized() throws Exception {
        mvc.perform(get(ConfigurationVersionController.PATH)).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", CHALLENGE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.path").value(ConfigurationVersionController.PATH));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer broken", "bearer broken", "BEARER broken", "Bearer broken extra"})
    void malformedAuthenticationIsUnauthorized(String header) throws Exception {
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", header))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", INVALID_TOKEN_CHALLENGE))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Valid authentication is required"));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer", "Bearer ", "bearer   ", "BEARER"})
    void emptyBearerCredentialsReceiveAChallengeWithoutTokenError(String authorization) throws Exception {
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", CHALLENGE))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic", "Digest", "BearerX"})
    void unsupportedSchemesDoNotAuthenticateEvenWithAValidManagerToken(String scheme) throws Exception {
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", scheme + " " + managerToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", CHALLENGE))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
        verifyNoInteractions(repository);
    }

    @Test
    void multipleSchemeSeparatorSpacesDoNotChangeTheToken() throws Exception {
        when(repository.findAll()).thenReturn(List.of());
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", "bearer   " + managerToken()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void wrongSignatureIsUnauthorized() throws Exception {
        String jwt = token(1L, "manager", List.of("FINANCE_MANAGER"), Instant.now().plusSeconds(300),
                Jwts.SIG.HS512.key().build());
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", INVALID_TOKEN_CHALLENGE))
                .andExpect(jsonPath("$.message").value("Valid authentication is required"));
        verifyNoInteractions(repository);
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        String jwt = token(1L, "manager", List.of("FINANCE_MANAGER"), Instant.now().minusSeconds(60), KEY);
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", INVALID_TOKEN_CHALLENGE))
                .andExpect(jsonPath("$.message").value("Valid authentication is required"));
        verifyNoInteractions(repository);
    }

    @Test
    void expiryIsRequired() throws Exception {
        String jwt = token(1L, "manager", List.of("FINANCE_MANAGER"), null, KEY);
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    static Stream<Arguments> invalidIdentities() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(0L), Arguments.of(-1L),
                Arguments.of(new BigDecimal("1.5")), Arguments.of(new BigDecimal("1.0000000000000001")),
                Arguments.of("1.5"), Arguments.of("not-a-number"),
                Arguments.of(new BigInteger("9223372036854775808")),
                Arguments.of("9223372036854775808"), Arguments.of(true));
    }

    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void identityMustBeAnIntegralPositiveLong(Object identity) throws Exception {
        String jwt = token(identity, "manager", List.of("FINANCE_MANAGER"), Instant.now().plusSeconds(300), KEY);
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void subjectMustBeNonblank(String subject) throws Exception {
        String jwt = token(1L, subject, List.of("FINANCE_MANAGER"), Instant.now().plusSeconds(300), KEY);
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    void missingSubjectIsUnauthorized() throws Exception {
        String jwt = token(1L, null, List.of("FINANCE_MANAGER"), Instant.now().plusSeconds(300), KEY);
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    void largeIdsAndOffsetTimestampsHaveLosslessWireRepresentation() throws Exception {
        OffsetDateTime created = OffsetDateTime.parse("2026-10-13T10:00:00+05:30");
        when(repository.findAll()).thenReturn(List.of(
                new ConfigurationVersion(Long.MAX_VALUE, 7, "Draft", "DRAFT", true, created, null),
                new ConfigurationVersion(8L, 8, "Published", "PUBLISHED", false, created, created.plusHours(1))));
        String jwt = token(Long.toString(Long.MAX_VALUE), "manager", List.of("FINANCE_MANAGER"),
                Instant.now().plusSeconds(300), KEY);
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionId").value("9223372036854775807"))
                .andExpect(jsonPath("$[0].versionId").isString())
                .andExpect(jsonPath("$[0].versionNumber").value(7))
                .andExpect(jsonPath("$[0].versionLabel").value("Draft"))
                .andExpect(jsonPath("$[0].publicationState").value("DRAFT"))
                .andExpect(jsonPath("$[0].isDemonstration").value(true))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-13T04:30:00Z"))
                .andExpect(jsonPath("$[0].publishedAt").value(nullValue()))
                .andExpect(jsonPath("$[1].publicationState").value("PUBLISHED"))
                .andExpect(jsonPath("$[1].publishedAt").value("2026-10-13T05:30:00Z"));
    }

    @Test
    void databaseFailureIsNotAnEmptyListOrAuthenticationFailure() throws Exception {
        when(repository.findAll()).thenThrow(new DataAccessResourceFailureException("Internal database detail"));
        mvc.perform(get(ConfigurationVersionController.PATH).header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Configuration data is temporarily unavailable"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.path").value(ConfigurationVersionController.PATH))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Internal database detail"))));
    }

    @Test
    void writeMethodsAreDeniedEvenForManagers() throws Exception {
        mvc.perform(put(ConfigurationVersionController.PATH).header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
}
