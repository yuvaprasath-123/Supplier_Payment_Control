package com.ofss.configuration.rule;

import com.ofss.configuration.error.ApiExceptionHandler;
import com.ofss.configuration.error.ErrorResponseWriter;
import com.ofss.configuration.security.JwtVerifier;
import com.ofss.configuration.security.SecurityConfiguration;
import com.ofss.configuration.version.ConfigurationVersion;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DraftRiskRuleController.class)
@Import({DraftRiskRuleService.class, SecurityConfiguration.class, JwtVerifier.class,
        ErrorResponseWriter.class, ApiExceptionHandler.class})
class DraftRiskRuleApiTest {
    private static final String BASE = "/api/v1/configuration/versions/7";
    private static final String RULE = BASE + "/risk-rules/STRUCTURING";
    private static final String SECRET = HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final String BODY = """
            {"displayName":"Under review", "enabled":false, "weight":0,
             "parameterSchemaVersion":"draft-v1", "parameters":{},
             "evaluationOrder":1, "expectedRowVersion":"0"}
            """;
    @Autowired private MockMvc mvc;
    @MockitoBean private DraftRiskRuleRepository repository;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> SECRET);
    }
    private String token(String role, Instant expiry) {
        return Jwts.builder().subject("manager").claim("userId", "42").claim("roles", List.of(role))
                .expiration(Date.from(expiry)).signWith(KEY).compact();
    }
    private String manager() { return token("FINANCE_MANAGER", Instant.now().plusSeconds(300)); }
    private MockHttpServletRequestBuilder write(String path, String body) {
        return put(path).header("Authorization", "Bearer " + manager()).contentType("application/json").content(body);
    }
    private static ConfigurationVersionDetails parent(String state, long rowVersion) {
        return new ConfigurationVersionDetails(new ConfigurationVersion(7, 2, "Draft", state, true,
                OffsetDateTime.parse("2026-10-13T10:00:00+05:30"), null), null, "SUM", null, rowVersion);
    }
    private static RiskRule rule(long id, String code, boolean enabled, int weight, int order) {
        return new RiskRule(id, 7, code, "Under review", enabled, weight, "draft-v1", Map.of(), order);
    }

    @ParameterizedTest @ValueSource(strings = {"NEW_SUPPLIER", "NEAR_DUPLICATE_INVOICE"})
    void newlySupportedRulesStillAllowIncompleteDraftParameters(String code) throws Exception {
        when(repository.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(repository.findRule(7, code)).thenReturn(Optional.empty(), Optional.of(rule(99, code, false, 0, 1)));
        when(repository.saveRule(eq(7L), eq(code), any(), isNull())).thenReturn(99L);
        mvc.perform(write(BASE + "/risk-rules/" + code, BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rule.ruleCode").value(code)).andExpect(jsonPath("$.rule.parameters").isEmpty())
                .andExpect(jsonPath("$.rowVersion").value("1"));
        verify(repository).incrementParent(7, 0);
    }

    @Test void detailsIncludePolicyNullableMaximumAndStringConcurrencyValue() throws Exception {
        when(repository.findVersion(7, false)).thenReturn(Optional.of(parent("DRAFT", Long.MAX_VALUE)));
        mvc.perform(get(BASE).header("Authorization", "Bearer " + manager())).andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value("7"))
                .andExpect(jsonPath("$.scorePolicyCode").value("SUM"))
                .andExpect(jsonPath("$.maxFinalScore").value(nullValue()))
                .andExpect(jsonPath("$.rowVersion").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.createdAt").value("2026-10-13T04:30:00Z"));
    }

    @Test void rulesReturnRepositoryOrderAndEmptyExistingVersionsAreValid() throws Exception {
        when(repository.findVersion(7, false)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(repository.listRules(7)).thenReturn(List.of(rule(1, "UNUSUAL_AMOUNT", true, 0, 1),
                rule(2, "STRUCTURING", false, 0, 2)), List.of());
        mvc.perform(get(BASE + "/risk-rules").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ruleCode").value("UNUSUAL_AMOUNT"))
                .andExpect(jsonPath("$[1].ruleConfigId").value("2"));
        mvc.perform(get(BASE + "/risk-rules").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest @ValueSource(strings = {"STRUCTURING", "UNUSUAL_FREQUENCY", "UNUSUAL_AMOUNT",
            "RECENTLY_REACTIVATED_SUPPLIER", "PREVIOUS_RISK_FLAGS"})
    void createsSupportedDraftRuleWithIncompleteParameters(String code) throws Exception {
        when(repository.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        var saved = rule(Long.MAX_VALUE, code, false, 0, 1);
        when(repository.findRule(7, code)).thenReturn(Optional.empty(), Optional.of(saved));
        when(repository.saveRule(eq(7L), eq(code), any(), isNull())).thenReturn(Long.MAX_VALUE);
        mvc.perform(write(BASE + "/risk-rules/" + code, BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rule.ruleConfigId").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.rule.parameters").isMap()).andExpect(jsonPath("$.rowVersion").value("1"));
        var order = inOrder(repository);
        order.verify(repository).findVersion(7, true);
        order.verify(repository).findRule(7, code);
        order.verify(repository).saveRule(eq(7L), eq(code), any(), isNull());
        order.verify(repository).incrementParent(7, 0);
        order.verify(repository).findRule(7, code);
    }

    @Test void replacesRuleWhilePreservingItsIdentity() throws Exception {
        var existing = rule(88, "STRUCTURING", true, 2, 1);
        var saved = rule(88, "STRUCTURING", false, 0, 1);
        when(repository.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(repository.findRule(7, "STRUCTURING")).thenReturn(Optional.of(existing), Optional.of(saved));
        when(repository.saveRule(eq(7L), eq("STRUCTURING"), any(), eq(existing))).thenReturn(88L);
        mvc.perform(write(RULE, BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rule.ruleConfigId").value("88"))
                .andExpect(jsonPath("$.rule.enabled").value(false)).andExpect(jsonPath("$.rule.weight").value(0));
        verify(repository).incrementParent(7, 0);
    }

    @Test void requestAndPutGetResponsesPreserveNestedNumberValuesAsNumbers() throws Exception {
        when(repository.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(repository.findVersion(7, false)).thenReturn(Optional.of(parent("DRAFT", 1)));
        var saved = new java.util.concurrent.atomic.AtomicReference<RiskRule>();
        when(repository.findRule(7, "STRUCTURING")).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(repository.saveRule(eq(7L), eq("STRUCTURING"), any(), isNull())).thenAnswer(invocation -> {
            DraftRiskRuleRequest request = invocation.getArgument(2);
            ExactParameterNumbers.assertValues(request.parameters());
            saved.set(new RiskRule(88, 7, "STRUCTURING", request.displayName(), request.enabled(),
                    request.weight(), request.parameterSchemaVersion(), request.parameters(), request.evaluationOrder()));
            return 88L;
        });
        when(repository.listRules(7)).thenAnswer(invocation -> List.of(saved.get()));
        String response = mvc.perform(write(RULE, BODY.replace("\"parameters\":{}",
                        "\"parameters\":" + ExactParameterNumbers.JSON)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ExactParameterNumbers.assertPutResponse(response);
        String list = mvc.perform(get(BASE + "/risk-rules").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ExactParameterNumbers.assertListResponse(list);
    }

    @Test void acceptsExactColumnLimitsAndLosslessLargeRowVersions() throws Exception {
        long current = Long.MAX_VALUE - 1;
        String body = BODY.replace("\"Under review\"", "\"" + "D".repeat(120) + "\"")
                .replace("\"draft-v1\"", "\"" + "S".repeat(32) + "\"")
                .replace("\"weight\":0", "\"weight\":2147483647")
                .replace("\"evaluationOrder\":1", "\"evaluationOrder\":99999")
                .replace("\"0\"", "\"" + current + "\"");
        when(repository.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", current)));
        when(repository.findRule(7, "STRUCTURING")).thenReturn(Optional.empty(), Optional.of(rule(88, "STRUCTURING", false, Integer.MAX_VALUE, 99999)));
        when(repository.saveRule(eq(7L), eq("STRUCTURING"), any(), isNull())).thenReturn(88L);
        mvc.perform(write(RULE, body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rowVersion").value(Long.toString(Long.MAX_VALUE)));
        verify(repository).saveRule(eq(7L), eq("STRUCTURING"), argThat(request ->
                request.displayName().length() == 120 && request.parameterSchemaVersion().length() == 32
                        && request.weight() == Integer.MAX_VALUE && request.evaluationOrder() == 99999
                        && request.expectedRowVersion() == current), isNull());
    }

    @ParameterizedTest @ValueSource(strings = {"details", "rules", "write"})
    void missingVersionsReturn404(String endpoint) throws Exception {
        var request = endpoint.equals("write") ? write(RULE, BODY)
                : get(endpoint.equals("details") ? BASE : BASE + "/risk-rules").header("Authorization", "Bearer " + manager());
        mvc.perform(request).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VERSION_NOT_FOUND"));
        verify(repository, never()).saveRule(anyLong(), anyString(), any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"STALE", "PUBLISHED", "EXHAUSTED"})
    void protectedVersionsNeverModifyChildOrParent(String scenario) throws Exception {
        long row = scenario.equals("EXHAUSTED") ? Long.MAX_VALUE : 1;
        when(repository.findVersion(7, true)).thenReturn(Optional.of(parent(scenario.equals("PUBLISHED") ? "PUBLISHED" : "DRAFT", row)));
        String body = scenario.equals("EXHAUSTED") ? BODY.replace("\"0\"", "\"" + Long.MAX_VALUE + "\"") : BODY;
        mvc.perform(write(RULE, body)).andExpect(status().isConflict()).andExpect(jsonPath("$.code")
                .value(scenario.equals("PUBLISHED") ? "VERSION_NOT_DRAFT" : scenario.equals("STALE") ? "STALE_VERSION" : "ROW_VERSION_EXHAUSTED"));
        verify(repository).findVersion(7, true);
        verifyNoMoreInteractions(repository);
    }

    static Stream<String> invalidBodies() {
        return Stream.of("{}", "null", "[]", "{broken",
                BODY.replace("\"Under review\"", "\" \""), BODY.replace("\"Under review\"", "123"),
                BODY.replace("\"Under review\"", "\"" + "D".repeat(121) + "\""),
                BODY.replace("\"draft-v1\"", "\" \""), BODY.replace("\"draft-v1\"", "null"),
                BODY.replace("\"draft-v1\"", "\"" + "S".repeat(33) + "\""),
                BODY.replace("\"enabled\":false", "\"enabled\":null"), BODY.replace("\"enabled\":false", "\"enabled\":\"false\""),
                BODY.replace("\"weight\":0", "\"weight\":-1"), BODY.replace("\"weight\":0", "\"weight\":1.0"),
                BODY.replace("\"weight\":0", "\"weight\":\"1\""), BODY.replace("\"weight\":0", "\"weight\":2147483648"),
                BODY.replace("\"evaluationOrder\":1", "\"evaluationOrder\":0"),
                BODY.replace("\"evaluationOrder\":1", "\"evaluationOrder\":100000"),
                BODY.replace("\"evaluationOrder\":1", "\"evaluationOrder\":1.5"),
                BODY.replace("\"parameters\":{}", "\"parameters\":[]"), BODY.replace("\"parameters\":{}", "\"parameters\":null"),
                BODY.replace("\"parameters\":{}", "\"parameters\":\"{}\""),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":-1"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":1.0"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":true"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":\"9223372036854775808\""),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":0,\"ruleConfigId\":999"));
    }
    @ParameterizedTest @MethodSource("invalidBodies")
    void invalidInputIs400BeforeDatabaseAccess(String body) throws Exception {
        mvc.perform(write(RULE, body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings = {"NEAR_DUPLICATE", "structuring", "UNKNOWN"})
    void unapprovedCodesAreRejected(String code) throws Exception {
        mvc.perform(write(BASE + "/risk-rules/" + code, BODY)).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidVersionIdsAre400(String id) throws Exception {
        mvc.perform(get("/api/v1/configuration/versions/" + id).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @ValueSource(strings = {"details", "rules", "write"})
    void endpointsEnforceAuthenticationAndManagerRole(String endpoint) throws Exception {
        for (String access : List.of("missing", "invalid", "expired", "PAYMENT_REQUESTER", "FINANCE_APPROVER")) {
            var request = endpoint.equals("write") ? put(RULE).contentType("application/json").content(BODY)
                    : get(endpoint.equals("details") ? BASE : BASE + "/risk-rules");
            if (!access.equals("missing")) request.header("Authorization", "Bearer " +
                    (access.equals("invalid") ? "invalid" : token(access.equals("expired") ? "FINANCE_MANAGER" : access,
                            access.equals("expired") ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(300))));
            boolean forbidden = access.equals("PAYMENT_REQUESTER") || access.equals("FINANCE_APPROVER");
            var result = mvc.perform(request).andExpect(status().is(forbidden ? 403 : 401));
            if (forbidden) result.andExpect(header().doesNotExist("WWW-Authenticate"));
            else result.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer realm=")));
        }
        verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings = {"RULE_CODE_CONFLICT", "RULE_ORDER_CONFLICT"})
    void knownConflictsReturn409WithoutDatabaseDetails(String reason) throws Exception {
        when(repository.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(repository.saveRule(anyLong(), anyString(), any(), any())).thenThrow(
                new DraftRuleConflictException(DraftRuleConflictException.Reason.valueOf(reason)));
        mvc.perform(write(RULE, BODY)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(reason));
        verify(repository, never()).incrementParent(anyLong(), anyLong());
    }
    @Test void databaseFailuresAreSanitized() throws Exception {
        when(repository.findVersion(7, true)).thenThrow(new DataAccessResourceFailureException("private Oracle SQL"));
        mvc.perform(write(RULE, BODY)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("private Oracle SQL"))));
    }
}
