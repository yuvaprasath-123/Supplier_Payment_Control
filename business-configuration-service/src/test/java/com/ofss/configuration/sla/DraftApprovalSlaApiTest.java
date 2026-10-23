package com.ofss.configuration.sla;

import com.ofss.configuration.error.ApiExceptionHandler;
import com.ofss.configuration.error.ErrorResponseWriter;
import com.ofss.configuration.rule.ConfigurationVersionDetails;
import com.ofss.configuration.rule.DraftRiskRuleRepository;
import com.ofss.configuration.security.JwtVerifier;
import com.ofss.configuration.security.SecurityConfiguration;
import com.ofss.configuration.version.ConfigurationVersion;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DraftApprovalSlaController.class)
@Import({DraftApprovalSlaService.class, SecurityConfiguration.class, JwtVerifier.class,
        ErrorResponseWriter.class, ApiExceptionHandler.class})
class DraftApprovalSlaApiTest {
    private static final String BASE = "/api/v1/configuration/versions/7/approval-slas";
    private static final String BODY = """
            {"durationSeconds":1,"startEventCode":"DRAFT_START","escalationPolicyCode":"DRAFT_ESCALATION",
             "terminalLevelPolicyCode":null,"expectedRowVersion":"0"}
            """;
    private static final String SECRET = HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    @Autowired private MockMvc mvc;
    @MockitoBean private DraftRiskRuleRepository versions;
    @MockitoBean private DraftApprovalSlaRepository slas;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("security.jwt.secret", () -> SECRET); }
    private String token(String role, Instant expiry) {
        return Jwts.builder().subject("manager").claim("userId", "42").claim("roles", List.of(role))
                .expiration(Date.from(expiry)).signWith(KEY).compact();
    }
    private String manager() { return token("FINANCE_MANAGER", Instant.now().plusSeconds(300)); }
    private static ConfigurationVersionDetails parent(String state, long row) {
        return new ConfigurationVersionDetails(new ConfigurationVersion(7, 1, "Draft", state, true,
                OffsetDateTime.now(), null), null, "SUM", null, row);
    }
    @Test void listHasStringIdsLongDurationNullablePolicyAndEmptyArray() throws Exception {
        when(versions.findVersion(7, false)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(slas.list(7)).thenReturn(List.of(new ApprovalSla(1, 7, 1, 1, "DRAFT_START", "DRAFT_ESCALATION", null),
                new ApprovalSla(2, 7, 3, 999999999999L, "DRAFT_START", "DRAFT_ESCALATION", "DRAFT_TERMINAL")), List.of());
        mvc.perform(get(BASE).header("Authorization", "Bearer " + manager())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slaConfigId").value("1"))
                .andExpect(jsonPath("$[0].versionId").value("7"))
                .andExpect(jsonPath("$[0].terminalLevelPolicyCode").value(nullValue()))
                .andExpect(jsonPath("$[1].approvalLevel").value(3))
                .andExpect(jsonPath("$[1].durationSeconds").value(999999999999L))
                .andExpect(jsonPath("$[1].terminalLevelPolicyCode").value("DRAFT_TERMINAL"));
        mvc.perform(get(BASE).header("Authorization", "Bearer " + manager())).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void createsOneIncompleteDraftWithoutPolicySemantics(int level) throws Exception {
        var saved = new ApprovalSla(Long.MAX_VALUE, 7, level, 1, "DRAFT_START", "DRAFT_ESCALATION", null);
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(slas.find(7, level)).thenReturn(Optional.empty(), Optional.of(saved));
        when(slas.save(eq(7L), eq(level), any(), isNull())).thenReturn(Long.MAX_VALUE);
        mvc.perform(put(BASE + "/" + level).header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sla.slaConfigId").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.sla.versionId").value("7"))
                .andExpect(jsonPath("$.sla.approvalLevel").value(level))
                .andExpect(jsonPath("$.sla.durationSeconds").value(1))
                .andExpect(jsonPath("$.sla.terminalLevelPolicyCode").value(nullValue()))
                .andExpect(jsonPath("$.rowVersion").value("1"));
        var order = inOrder(versions, slas);
        order.verify(versions).findVersion(7, true);
        order.verify(slas).find(7, level);
        order.verify(slas).save(eq(7L), eq(level), argThat(request -> request.durationSeconds() == 1
                && request.terminalLevelPolicyCode() == null), isNull());
        order.verify(versions).incrementParent(7, 0);
        order.verify(slas).find(7, level);
        verify(slas, never()).list(anyLong());
    }
    @Test void replacementPreservesIdentityAcceptsUpperDurationAndUnicodeLimits() throws Exception {
        String unicode = "\uD83D\uDE00".repeat(32);
        var existing = new ApprovalSla(88, 7, 2, 1, "OLD", "OLD", null);
        var saved = new ApprovalSla(88, 7, 2, 999999999999L, unicode, " Pending policy ", unicode);
        long row = Long.MAX_VALUE - 1;
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", row)));
        when(slas.find(7, 2)).thenReturn(Optional.of(existing), Optional.of(saved));
        when(slas.save(eq(7L), eq(2), any(), eq(existing))).thenReturn(88L);
        String body = "{\"durationSeconds\":999999999999,\"startEventCode\":\"" + unicode
                + "\",\"escalationPolicyCode\":\" Pending policy \",\"terminalLevelPolicyCode\":\"" + unicode
                + "\",\"expectedRowVersion\":\"" + row + "\"}";
        mvc.perform(put(BASE + "/2").header("Authorization", "Bearer " + manager()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sla.slaConfigId").value("88"))
                .andExpect(jsonPath("$.sla.durationSeconds").value(999999999999L))
                .andExpect(jsonPath("$.sla.startEventCode").value(unicode))
                .andExpect(jsonPath("$.sla.escalationPolicyCode").value(" Pending policy "))
                .andExpect(jsonPath("$.sla.terminalLevelPolicyCode").value(unicode))
                .andExpect(jsonPath("$.rowVersion").value(Long.toString(Long.MAX_VALUE)));
        verify(slas).save(eq(7L), eq(2), argThat(request -> request.durationSeconds() == 999999999999L
                && request.startEventCode().equals(unicode) && request.escalationPolicyCode().equals(" Pending policy ")
                && request.terminalLevelPolicyCode().equals(unicode)), eq(existing));
    }
    @Test void absentTerminalAndNumericRowVersionAccepted() throws Exception {
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(slas.find(7, 1)).thenReturn(Optional.empty(), Optional.of(new ApprovalSla(1, 7, 1, 1, "DRAFT_START", "DRAFT_ESCALATION", null)));
        when(slas.save(eq(7L), eq(1), any(), isNull())).thenReturn(1L);
        mvc.perform(put(BASE + "/1").header("Authorization", "Bearer " + manager()).contentType("application/json")
                .content(BODY.replace("\"terminalLevelPolicyCode\":null,", "").replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":0")))
                .andExpect(status().isOk());
    }
    static Stream<String> invalidBodies() {
        Stream<String> durations = Stream.of("null", "0", "-1", "1000000000000", "9223372036854775808",
                "1.0", "1.0000000000000001", "1e1", "\"1\"", "true", "[]", "{}")
                .map(value -> BODY.replace("\"durationSeconds\":1", "\"durationSeconds\":" + value));
        Stream<String> policies = Stream.of("startEventCode", "escalationPolicyCode", "terminalLevelPolicyCode")
                .flatMap(field -> Stream.of("\"\"", "\"   \"", "\"\\t\\n\"", "1", "true", "[]", "{}",
                        "\"" + "A".repeat(33) + "\"", "\"" + "\uD83D\uDE00".repeat(33) + "\"")
                        .map(value -> BODY.replace(field.equals("terminalLevelPolicyCode") ? "\"" + field + "\":null"
                                : "\"" + field + "\":\"" + (field.equals("startEventCode") ? "DRAFT_START" : "DRAFT_ESCALATION") + "\"",
                                "\"" + field + "\":" + value)));
        return Stream.concat(Stream.concat(durations, policies), Stream.of("{}", "null", "[]", "{broken",
                BODY.replace("\"startEventCode\":\"DRAFT_START\"", "\"startEventCode\":null"),
                BODY.replace("\"escalationPolicyCode\":\"DRAFT_ESCALATION\"", "\"escalationPolicyCode\":null"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":null"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":true"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":1.0"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":-1"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":\"9223372036854775808\""),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":\"0\",\"slaConfigId\":99")));
    }
    @ParameterizedTest @MethodSource("invalidBodies")
    void invalidFieldsAre400BeforeDatabase(String body) throws Exception {
        mvc.perform(put(BASE + "/1").header("Authorization", "Bearer " + manager()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(versions, slas);
    }
    static Stream<String> malformedUnicodeBodies() {
        return Stream.of("startEventCode", "escalationPolicyCode", "terminalLevelPolicyCode")
                .flatMap(field -> Stream.of("\\uD800", "\\uDC00", "\\uDC00\\uD800", "A\\uD800B")
                        .map(escaped -> BODY.replace(field.equals("terminalLevelPolicyCode") ? "\"" + field + "\":null"
                                : "\"" + field + "\":\"" + (field.equals("startEventCode") ? "DRAFT_START" : "DRAFT_ESCALATION") + "\"",
                                "\"" + field + "\":\"" + escaped + "\"")));
    }
    @ParameterizedTest @MethodSource("malformedUnicodeBodies")
    void malformedUnicodeIs400BeforeDatabaseAccess(String body) throws Exception {
        mvc.perform(put(BASE + "/1").header("Authorization", "Bearer " + manager()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(versions, slas);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "4", "-1", "1.0", "01", "abc", "9223372036854775808"})
    void invalidApprovalLevelsAre400(String level) throws Exception {
        mvc.perform(put(BASE + "/" + level).header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(versions, slas);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidVersionIdsAre400(String id) throws Exception {
        mvc.perform(get("/api/v1/configuration/versions/" + id + "/approval-slas").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(versions, slas);
    }
    @ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
    void missingVersionIs404(String method) throws Exception {
        var request = method.equals("GET") ? get(BASE) : put(BASE + "/1").contentType("application/json").content(BODY);
        mvc.perform(request.header("Authorization", "Bearer " + manager())).andExpect(status().isNotFound());
        verifyNoInteractions(slas);
    }
    @ParameterizedTest @ValueSource(strings = {"STALE", "PUBLISHED", "EXHAUSTED"})
    void protectedParentsAre409BeforeChildAccess(String scenario) throws Exception {
        long row = scenario.equals("EXHAUSTED") ? Long.MAX_VALUE : 1;
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent(scenario.equals("PUBLISHED") ? "PUBLISHED" : "DRAFT", row)));
        String body = scenario.equals("EXHAUSTED") ? BODY.replace("\"0\"", "\"" + row + "\"") : BODY;
        mvc.perform(put(BASE + "/1").header("Authorization", "Bearer " + manager()).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(scenario.equals("STALE") ? "STALE_VERSION"
                        : scenario.equals("PUBLISHED") ? "VERSION_NOT_DRAFT" : "ROW_VERSION_EXHAUSTED"));
        verifyNoInteractions(slas);
        verify(versions, never()).incrementParent(anyLong(), anyLong());
    }
    @ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
    void endpointsRequireValidManagerAuthentication(String method) throws Exception {
        for (String access : List.of("missing", "invalid", "expired", "PAYMENT_REQUESTER", "FINANCE_APPROVER")) {
            var request = method.equals("GET") ? get(BASE) : put(BASE + "/1").contentType("application/json").content(BODY);
            if (!access.equals("missing")) request.header("Authorization", "Bearer " + (access.equals("invalid") ? "invalid"
                    : token(access.equals("expired") ? "FINANCE_MANAGER" : access,
                            access.equals("expired") ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(300))));
            boolean forbidden = access.equals("PAYMENT_REQUESTER") || access.equals("FINANCE_APPROVER");
            var result = mvc.perform(request).andExpect(status().is(forbidden ? 403 : 401));
            if (forbidden) result.andExpect(header().doesNotExist("WWW-Authenticate"));
            else result.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer realm=")));
        }
        verifyNoInteractions(versions, slas);
    }
    @Test void knownConflictDoesNotIncrementParent() throws Exception {
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(slas.save(anyLong(), anyInt(), any(), any())).thenThrow(new DraftSlaConflictException());
        mvc.perform(put(BASE + "/1").header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLA_LEVEL_CONFLICT"));
        verify(versions, never()).incrementParent(anyLong(), anyLong());
    }
    @Test void databaseFailuresAreSanitized() throws Exception {
        when(versions.findVersion(7, true)).thenThrow(new DataAccessResourceFailureException("private Oracle SQL"));
        mvc.perform(put(BASE + "/1").header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("private Oracle SQL"))));
    }
}
