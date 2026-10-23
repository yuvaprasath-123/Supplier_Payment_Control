package com.ofss.configuration.version;

import com.ofss.configuration.error.ApiExceptionHandler;
import com.ofss.configuration.error.ErrorResponseWriter;
import com.ofss.configuration.security.JwtVerifier;
import com.ofss.configuration.security.SecurityConfiguration;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigurationVersionController.class)
@Import({ConfigurationVersionService.class, SecurityConfiguration.class, JwtVerifier.class,
        ErrorResponseWriter.class, ApiExceptionHandler.class})
class ConfigurationVersionCreationApiTest {
    private static final SecretKey KEY = Jwts.SIG.HS512.key().build();
    private static final String PATH = ConfigurationVersionController.PATH;
    private static final String CHALLENGE = "Bearer realm=\"business-configuration-service\"";
    private static final String VALID = """
            {"versionLabel":"Prototype", "description":"Review draft",
             "isDemonstration":true, "scorePolicyCode":"SUM"}
            """;
    @Autowired private MockMvc mvc;
    @MockitoBean private ConfigurationVersionRepository repository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        // Raw UTF-8 test material, randomly generated only in this test JVM.
        properties.add("security.jwt.secret", () -> java.util.HexFormat.of().formatHex(KEY.getEncoded()));
    }

    private static String token(String role, Instant expiry) {
        var signingKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                java.util.HexFormat.of().formatHex(KEY.getEncoded()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Jwts.builder().subject("local-manager").claim("userId", "9223372036854775807")
                .claim("roles", List.of(role)).expiration(Date.from(expiry)).signWith(signingKey).compact();
    }

    private static String manager() {
        return token("FINANCE_MANAGER", Instant.now().plusSeconds(300));
    }

    @Test
    void createsDraftWithTrustedActorUtcTimeAndSubsequentGetIncludesIt() throws Exception {
        var created = new ConfigurationVersion(Long.MAX_VALUE, 5, "Prototype", "DRAFT", true,
                OffsetDateTime.parse("2026-10-13T10:00:00Z"), null);
        when(repository.latestVersionNumber()).thenReturn(4L);
        when(repository.insertDraft(any(), eq(5), eq(Long.MAX_VALUE), any())).thenAnswer(invocation -> {
            CreateConfigurationVersionRequest request = invocation.getArgument(0);
            assertThat(request.description()).isEqualTo("Review draft");
            assertThat(request.scorePolicyCode()).isEqualTo("SUM");
            assertThat(request.maxFinalScore()).isNull();
            OffsetDateTime timestamp = invocation.getArgument(3);
            assertThat(timestamp.getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(timestamp.toInstant()).isBetween(Instant.now().minusSeconds(10), Instant.now());
            assertThat(timestamp.getNano() % 1000).isZero();
            return created;
        });
        when(repository.findAll()).thenReturn(List.of(created));
        mvc.perform(post(PATH).header("Authorization", "bearer " + manager())
                .contentType("application/json").content(VALID))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionId").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.versionId").isString()).andExpect(jsonPath("$.versionNumber").value(5))
                .andExpect(jsonPath("$.publicationState").value("DRAFT"))
                .andExpect(jsonPath("$.isDemonstration").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-10-13T10:00:00Z"))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()));
        mvc.perform(get(PATH).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].versionId").value(Long.toString(Long.MAX_VALUE)));
        verify(repository).insertDraft(any(), eq(5), eq(Long.MAX_VALUE), any());
    }

    @Test
    void acceptsCapPolicyAndMaximumLengthsWithNoDescriptionCoercion() throws Exception {
        String body = "{\"versionLabel\":\"" + "L".repeat(100) + "\",\"description\":\""
                + "D".repeat(1000) + "\",\"isDemonstration\":false,\"scorePolicyCode\":\"CAP_AT_MAX\",\"maxFinalScore\":2147483647}";
        when(repository.insertDraft(any(), eq(1), anyLong(), any())).thenAnswer(invocation -> {
            CreateConfigurationVersionRequest request = invocation.getArgument(0);
            assertThat(request.isDemonstration()).isFalse();
            assertThat(request.maxFinalScore()).isEqualTo(Integer.MAX_VALUE);
            return new ConfigurationVersion(1, 1, request.versionLabel(), "DRAFT", false,
                    invocation.getArgument(3), null);
        });
        mvc.perform(post(PATH).header("Authorization", "Bearer " + manager())
                .contentType("application/json").content(body)).andExpect(status().isCreated());
    }

    static Stream<String> invalidBodies() {
        return Stream.of("{}", "null", "[]", "{broken",
                VALID.replace("\"Prototype\"", "\"   \""),
                VALID.replace("\"Prototype\"", "\"" + "L".repeat(101) + "\""),
                VALID.replace("\"Prototype\"", "null"), VALID.replace("\"Prototype\"", "123"),
                VALID.replace("\"Review draft\"", "\"" + "D".repeat(1001) + "\""),
                VALID.replace("\"Review draft\"", "12"), VALID.replace("\"isDemonstration\":true,", ""),
                VALID.replace("\"isDemonstration\":true", "\"isDemonstration\":null"),
                VALID.replace("\"isDemonstration\":true", "\"isDemonstration\":\"true\""),
                VALID.replace("\"isDemonstration\":true", "\"isDemonstration\":1"),
                VALID.replace("\"SUM\"", "\"sum\""), VALID.replace("\"SUM\"", "null"),
                VALID.replace("\"SUM\"", "\"UNKNOWN\""), VALID.replace("\"SUM\"", "\"CAP_AT_MAX\""),
                VALID.replace("\"SUM\"", "\"SUM\",\"maxFinalScore\":1"),
                VALID.replace("\"SUM\"", "\"CAP_AT_MAX\",\"maxFinalScore\":null"),
                VALID.replace("\"SUM\"", "\"CAP_AT_MAX\",\"maxFinalScore\":0"),
                VALID.replace("\"SUM\"", "\"CAP_AT_MAX\",\"maxFinalScore\":-1"),
                VALID.replace("\"SUM\"", "\"CAP_AT_MAX\",\"maxFinalScore\":1.0"),
                VALID.replace("\"SUM\"", "\"CAP_AT_MAX\",\"maxFinalScore\":1.5"),
                VALID.replace("\"SUM\"", "\"CAP_AT_MAX\",\"maxFinalScore\":\"1\""),
                VALID.replace("\"SUM\"", "\"CAP_AT_MAX\",\"maxFinalScore\":true"),
                VALID.replace("\"SUM\"", "\"CAP_AT_MAX\",\"maxFinalScore\":2147483648"),
                VALID.replace("\"SUM\"", "\"CAP_AT_MAX\",\"maxFinalScore\":9223372036854775808"),
                VALID.replace("\"SUM\"", "\"SUM\",\"createdByUserId\":999"),
                VALID.replace("\"SUM\"", "\"SUM\",\"publicationState\":\"PUBLISHED\""));
    }

    @ParameterizedTest @MethodSource("invalidBodies")
    void rejectsInvalidRequestsBeforeAnyDatabaseAccess(String body) throws Exception {
        mvc.perform(post(PATH).header("Authorization", "Bearer " + manager())
                .contentType("application/json").content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid draft configuration request"))
                .andExpect(jsonPath("$.timestamp").isString()).andExpect(jsonPath("$.path").value(PATH));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @ValueSource(strings = {"PAYMENT_REQUESTER", "FINANCE_APPROVER", "ROLE_FINANCE_MANAGER"})
    void insufficientRolesCannotWrite(String role) throws Exception {
        mvc.perform(post(PATH).header("Authorization", "Bearer " + token(role, Instant.now().plusSeconds(300)))
                .contentType("application/json").content(VALID)).andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        verifyNoInteractions(repository);
    }

    @Test
    void missingAuthenticationCannotWrite() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content(VALID))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", CHALLENGE));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @ValueSource(strings = {"invalid", "expired", "wrongSignature"})
    void invalidAuthenticationCannotWrite(String kind) throws Exception {
        String jwt = switch (kind) {
            case "expired" -> token("FINANCE_MANAGER", Instant.now().minusSeconds(60));
            case "wrongSignature" -> Jwts.builder().subject("manager").claim("userId", "1")
                    .claim("roles", List.of("FINANCE_MANAGER")).expiration(Date.from(Instant.now().plusSeconds(300)))
                    .signWith(Jwts.SIG.HS512.key().build()).compact();
            default -> "invalid";
        };
        mvc.perform(post(PATH).header("Authorization", "Bearer " + jwt).contentType("application/json").content(VALID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", CHALLENGE + ", error=\"invalid_token\""));
        verifyNoInteractions(repository);
    }

    @Test
    void competingAllocationReturnsSanitizedConflictWithoutRetry() throws Exception {
        when(repository.insertDraft(any(), anyInt(), anyLong(), any()))
                .thenThrow(new VersionAllocationConflictException());
        mvc.perform(post(PATH).header("Authorization", "Bearer " + manager()).contentType("application/json").content(VALID))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_ALLOCATION_CONFLICT"))
                .andExpect(content().string(not(containsString("Oracle private diagnostic"))));
        verify(repository, times(1)).latestVersionNumber();
        verify(repository, times(1)).insertDraft(any(), anyInt(), anyLong(), any());
    }

    @Test
    void exhaustedVersionNumbersReturnUnavailableWithoutMisleadingRetryAdvice() throws Exception {
        when(repository.latestVersionNumber()).thenReturn((long) Integer.MAX_VALUE);
        mvc.perform(post(PATH).header("Authorization", "Bearer " + manager()).contentType("application/json").content(VALID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"));
        verify(repository, never()).insertDraft(any(), anyInt(), anyLong(), any());
    }

    @Test
    void databaseFailureReturnsSanitized503AndNeverSuccessfulCreation() throws Exception {
        when(repository.insertDraft(any(), anyInt(), anyLong(), any()))
                .thenThrow(new DataAccessResourceFailureException("Oracle private diagnostic"));
        mvc.perform(post(PATH).header("Authorization", "Bearer " + manager()).contentType("application/json").content(VALID))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("Oracle private diagnostic"))));
    }

    @Test
    void otherIntegrityFailuresAreNotMisreportedAsAllocationConflicts() throws Exception {
        when(repository.insertDraft(any(), anyInt(), anyLong(), any()))
                .thenThrow(new DataIntegrityViolationException("Oracle private constraint"));
        mvc.perform(post(PATH).header("Authorization", "Bearer " + manager()).contentType("application/json").content(VALID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("Oracle private constraint"))));
    }

    @ParameterizedTest @ValueSource(strings = {"PK_CFG_VERSIONS", "UNKNOWN_CONSTRAINT"})
    void unclassifiedDuplicateKeysReceiveSanitizedFailure(String constraint) throws Exception {
        when(repository.insertDraft(any(), anyInt(), anyLong(), any()))
                .thenThrow(new DuplicateKeyException("Oracle private " + constraint));
        mvc.perform(post(PATH).header("Authorization", "Bearer " + manager()).contentType("application/json").content(VALID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString(constraint))))
                .andExpect(content().string(not(containsString("refresh and try again"))));
    }
}
