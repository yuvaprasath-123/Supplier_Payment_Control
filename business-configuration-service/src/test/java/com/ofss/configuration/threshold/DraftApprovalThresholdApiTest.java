package com.ofss.configuration.threshold;

import com.ofss.configuration.error.ApiExceptionHandler;
import com.ofss.configuration.error.ErrorResponseWriter;
import com.ofss.configuration.rule.ConfigurationVersionDetails;
import com.ofss.configuration.rule.DraftRiskRuleRepository;
import com.ofss.configuration.security.JwtVerifier;
import com.ofss.configuration.security.SecurityConfiguration;
import com.ofss.configuration.version.ConfigurationVersion;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
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

@WebMvcTest(DraftApprovalThresholdController.class)
@Import({DraftApprovalThresholdService.class, SecurityConfiguration.class, JwtVerifier.class,
        ErrorResponseWriter.class, ApiExceptionHandler.class})
class DraftApprovalThresholdApiTest {
    private static final String BASE = "/api/v1/configuration/versions/7/approval-thresholds";
    private static final String BODY = """
            {"minAmountInclusive":"10.01","maxAmountExclusive":null,"expectedRowVersion":"0"}
            """;
    private static final String SECRET = HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    @Autowired private MockMvc mvc;
    @MockitoBean private DraftRiskRuleRepository versions;
    @MockitoBean private DraftApprovalThresholdRepository thresholds;
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

    @Test void listingReturnsExactStringMoneyOrderedFixturesAndEmptyList() throws Exception {
        when(versions.findVersion(7, false)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(thresholds.list(7)).thenReturn(List.of(new ApprovalThreshold(1, 7, 1, new BigDecimal("0"), null),
                new ApprovalThreshold(2, 7, 3, new BigDecimal("9999999999999.98"), new BigDecimal("9999999999999.99"))), List.of());
        mvc.perform(get(BASE).header("Authorization", "Bearer " + manager())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].thresholdConfigId").value("1"))
                .andExpect(jsonPath("$[0].minAmountInclusive").value("0.00"))
                .andExpect(jsonPath("$[0].maxAmountExclusive").value(nullValue()))
                .andExpect(jsonPath("$[1].minAmountInclusive").value("9999999999999.98"))
                .andExpect(jsonPath("$[1].maxAmountExclusive").value("9999999999999.99"));
        mvc.perform(get(BASE).header("Authorization", "Bearer " + manager())).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void createsIncompleteThresholdWithExactDecimalsAndNullUpperBound(int level) throws Exception {
        var saved = new ApprovalThreshold(Long.MAX_VALUE, 7, level, new BigDecimal("10.01"), null);
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(thresholds.find(7, level)).thenReturn(Optional.empty(), Optional.of(saved));
        when(thresholds.save(eq(7L), eq(level), any(), isNull())).thenReturn(Long.MAX_VALUE);
        mvc.perform(put(BASE + "/" + level).header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.threshold.thresholdConfigId").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.threshold.versionId").value("7"))
                .andExpect(jsonPath("$.threshold.approvalLevel").value(level))
                .andExpect(jsonPath("$.threshold.minAmountInclusive").value("10.01"))
                .andExpect(jsonPath("$.threshold.maxAmountExclusive").value(nullValue()))
                .andExpect(jsonPath("$.rowVersion").value("1"));
        var order = inOrder(versions, thresholds);
        order.verify(versions).findVersion(7, true);
        order.verify(thresholds).find(7, level);
        order.verify(thresholds).save(eq(7L), eq(level), argThat(request -> request.minAmountInclusive().equals(new BigDecimal("10.01"))
                && request.maxAmountExclusive() == null), isNull());
        order.verify(versions).incrementParent(7, 0);
        order.verify(thresholds).find(7, level);
        verify(thresholds, never()).list(anyLong());
    }

    @Test void replacementKeepsIdentityAndMaximumPrecisionWithoutRounding() throws Exception {
        var existing = new ApprovalThreshold(88, 7, 2, BigDecimal.ZERO, null);
        var saved = new ApprovalThreshold(88, 7, 2, new BigDecimal("9999999999999.98"), new BigDecimal("9999999999999.99"));
        long row = Long.MAX_VALUE - 1;
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", row)));
        when(thresholds.find(7, 2)).thenReturn(Optional.of(existing), Optional.of(saved));
        when(thresholds.save(eq(7L), eq(2), any(), eq(existing))).thenReturn(88L);
        String body = "{\"minAmountInclusive\":\"9999999999999.98\",\"maxAmountExclusive\":\"9999999999999.99\",\"expectedRowVersion\":\"" + row + "\"}";
        mvc.perform(put(BASE + "/2").header("Authorization", "Bearer " + manager()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.threshold.thresholdConfigId").value("88"))
                .andExpect(jsonPath("$.threshold.minAmountInclusive").value("9999999999999.98"))
                .andExpect(jsonPath("$.threshold.maxAmountExclusive").value("9999999999999.99"))
                .andExpect(jsonPath("$.rowVersion").value(Long.toString(Long.MAX_VALUE)));
        verify(thresholds).save(eq(7L), eq(2), argThat(request -> request.minAmountInclusive().equals(new BigDecimal("9999999999999.98"))
                && request.maxAmountExclusive().equals(new BigDecimal("9999999999999.99"))), eq(existing));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "0.01", "1.2", "1.230", "9999999999999.99"})
    void acceptsExactlyRepresentableDecimalStringsAndAbsentMaximum(String minimum) throws Exception {
        BigDecimal normalized = new BigDecimal(minimum).setScale(2);
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(thresholds.find(7, 1)).thenReturn(Optional.empty(), Optional.of(new ApprovalThreshold(1, 7, 1, normalized, null)));
        when(thresholds.save(eq(7L), eq(1), any(), isNull())).thenReturn(1L);
        mvc.perform(put(BASE + "/1").header("Authorization", "Bearer " + manager()).contentType("application/json")
                .content("{\"minAmountInclusive\":\"" + minimum + "\",\"expectedRowVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.threshold.minAmountInclusive").value(normalized.toPlainString()));
        verify(thresholds).save(eq(7L), eq(1), argThat(request -> request.minAmountInclusive().equals(normalized)), isNull());
    }

    static Stream<String> invalidBodies() {
        Stream<String> minimums = Stream.of("null", "0", "10.01", "9999999999999.99", "true", "[]", "{}",
                "\"\"", "\"-0.01\"", "\"1.001\"", "\"1.0000000000000001\"", "\"10000000000000\"",
                "\"9999999999999.999\"", "\"1e2\"", "\"NaN\"", "\"Infinity\"", "\" 1.00\"", "\"+1.00\"", "\".01\"")
                .map(value -> BODY.replace("\"minAmountInclusive\":\"10.01\"", "\"minAmountInclusive\":" + value));
        Stream<String> maximums = Stream.of("10.02", "true", "\"10.01\"", "\"10.00\"", "\"-1\"",
                "\"10.011\"", "\"10000000000000.00\"", "\"1e2\"")
                .map(value -> BODY.replace("\"maxAmountExclusive\":null", "\"maxAmountExclusive\":" + value));
        return Stream.concat(Stream.concat(minimums, maximums), Stream.of("{}", "null", "[]", "{broken",
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":-1"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":1.0"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":null"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":true"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":\"9223372036854775808\""),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":\"0\",\"approvalLevel\":1")));
    }
    @ParameterizedTest @MethodSource("invalidBodies")
    void invalidInputIs400BeforeDatabaseAccess(String body) throws Exception {
        mvc.perform(put(BASE + "/1").header("Authorization", "Bearer " + manager()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(versions, thresholds);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "4", "-1", "1.0", "01", "abc", "9223372036854775808"})
    void invalidPathLevelsAre400(String level) throws Exception {
        mvc.perform(put(BASE + "/" + level).header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(versions, thresholds);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidVersionIdsAre400(String id) throws Exception {
        mvc.perform(get("/api/v1/configuration/versions/" + id + "/approval-thresholds").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(versions, thresholds);
    }
    @ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
    void missingVersionIs404(String method) throws Exception {
        var request = method.equals("GET") ? get(BASE) : put(BASE + "/1").contentType("application/json").content(BODY);
        mvc.perform(request.header("Authorization", "Bearer " + manager())).andExpect(status().isNotFound());
        verifyNoInteractions(thresholds);
    }
    @ParameterizedTest @ValueSource(strings = {"STALE", "PUBLISHED", "EXHAUSTED"})
    void protectedParentIsRejectedBeforeChildAccess(String scenario) throws Exception {
        long row = scenario.equals("EXHAUSTED") ? Long.MAX_VALUE : 1;
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent(scenario.equals("PUBLISHED") ? "PUBLISHED" : "DRAFT", row)));
        String body = scenario.equals("EXHAUSTED") ? BODY.replace("\"0\"", "\"" + row + "\"") : BODY;
        mvc.perform(put(BASE + "/1").header("Authorization", "Bearer " + manager()).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(scenario.equals("STALE") ? "STALE_VERSION"
                        : scenario.equals("PUBLISHED") ? "VERSION_NOT_DRAFT" : "ROW_VERSION_EXHAUSTED"));
        verifyNoInteractions(thresholds);
        verify(versions, never()).incrementParent(anyLong(), anyLong());
    }
    @ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
    void authenticationAndManagerRoleRequired(String method) throws Exception {
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
        verifyNoInteractions(versions, thresholds);
    }
    @Test void knownConflictIs409WithoutIncrement() throws Exception {
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(thresholds.save(anyLong(), anyInt(), any(), any())).thenThrow(new DraftThresholdConflictException());
        mvc.perform(put(BASE + "/1").header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("THRESHOLD_LEVEL_CONFLICT"));
        verify(versions, never()).incrementParent(anyLong(), anyLong());
    }
    @Test void databaseFailureIsSanitized() throws Exception {
        when(versions.findVersion(7, true)).thenThrow(new DataAccessResourceFailureException("private Oracle SQL"));
        mvc.perform(put(BASE + "/1").header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("private Oracle SQL"))));
    }
}
