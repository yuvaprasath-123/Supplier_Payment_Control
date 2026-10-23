package com.ofss.configuration.band;

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
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DraftRiskBandController.class)
@Import({DraftRiskBandService.class, SecurityConfiguration.class, JwtVerifier.class,
        ErrorResponseWriter.class, ApiExceptionHandler.class})
class DraftRiskBandApiTest {
    private static final String BASE = "/api/v1/configuration/versions/7/risk-bands";
    private static final String BODY = """
            {"minScoreInclusive":10,"maxScoreInclusive":null,"approvalLevel":3,"expectedRowVersion":"0"}
            """;
    private static final String SECRET = HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    @Autowired private MockMvc mvc;
    @MockitoBean private DraftRiskRuleRepository versions;
    @MockitoBean private DraftRiskBandRepository bands;
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

    @Test void listReturnsOrderedFixturesAndEmptyExistingVersion() throws Exception {
        when(versions.findVersion(7, false)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(bands.list(7)).thenReturn(List.of(new RiskBand(1, 7, "HIGH", 10, null, 3),
                new RiskBand(2, 7, "LOW", 10, 10, 1)), List.of());
        mvc.perform(get(BASE).header("Authorization", "Bearer " + manager())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bandConfigId").value("1"))
                .andExpect(jsonPath("$[0].bandCode").value("HIGH"))
                .andExpect(jsonPath("$[0].maxScoreInclusive").value(nullValue()))
                .andExpect(jsonPath("$[1].bandCode").value("LOW"));
        mvc.perform(get(BASE).header("Authorization", "Bearer " + manager())).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest @ValueSource(strings = {"LOW", "MEDIUM", "HIGH"})
    void createsIncompleteDraftBandWithoutInventingCoverageOrLevelMapping(String code) throws Exception {
        var saved = new RiskBand(Long.MAX_VALUE, 7, code, 10, null, 3);
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(bands.find(7, code)).thenReturn(Optional.empty(), Optional.of(saved));
        when(bands.save(eq(7L), eq(code), any(), isNull())).thenReturn(Long.MAX_VALUE);
        mvc.perform(put(BASE + "/" + code).header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.band.bandConfigId").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.band.versionId").value("7"))
                .andExpect(jsonPath("$.band.minScoreInclusive").value(10))
                .andExpect(jsonPath("$.band.maxScoreInclusive").value(nullValue()))
                .andExpect(jsonPath("$.band.approvalLevel").value(3))
                .andExpect(jsonPath("$.rowVersion").value("1"));
        var order = inOrder(versions, bands);
        order.verify(versions).findVersion(7, true);
        order.verify(bands).find(7, code);
        order.verify(bands).save(eq(7L), eq(code), any(), isNull());
        order.verify(versions).incrementParent(7, 0);
        order.verify(bands).find(7, code);
        verify(bands, never()).list(anyLong());
    }

    @Test void replacementRetainsIdentityAndAllowsEqualEndpointsAtIntegerLimit() throws Exception {
        var existing = new RiskBand(88, 7, "LOW", 10, null, 3);
        var saved = new RiskBand(88, 7, "LOW", Integer.MAX_VALUE, Integer.MAX_VALUE, 1);
        long row = Long.MAX_VALUE - 1;
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", row)));
        when(bands.find(7, "LOW")).thenReturn(Optional.of(existing), Optional.of(saved));
        when(bands.save(eq(7L), eq("LOW"), any(), eq(existing))).thenReturn(88L);
        String body = "{\"minScoreInclusive\":2147483647,\"maxScoreInclusive\":2147483647,\"approvalLevel\":1,\"expectedRowVersion\":\"" + row + "\"}";
        mvc.perform(put(BASE + "/LOW").header("Authorization", "Bearer " + manager()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.band.bandConfigId").value("88"))
                .andExpect(jsonPath("$.rowVersion").value(Long.toString(Long.MAX_VALUE)));
        verify(bands).save(eq(7L), eq("LOW"), argThat(request -> request.minScoreInclusive() == Integer.MAX_VALUE
                && request.maxScoreInclusive() == Integer.MAX_VALUE && request.approvalLevel() == 1), eq(existing));
    }

    @Test void absentUpperBoundAndNumericZeroRowVersionAreAccepted() throws Exception {
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(bands.find(7, "LOW")).thenReturn(Optional.empty(), Optional.of(new RiskBand(1, 7, "LOW", 0, null, 2)));
        when(bands.save(eq(7L), eq("LOW"), any(), isNull())).thenReturn(1L);
        mvc.perform(put(BASE + "/LOW").header("Authorization", "Bearer " + manager()).contentType("application/json")
                .content("{\"minScoreInclusive\":0,\"approvalLevel\":2,\"expectedRowVersion\":0}"))
                .andExpect(status().isOk());
    }

    static Stream<String> invalidBodies() {
        return Stream.of("{}", "null", "[]", "{broken",
                BODY.replace("\"minScoreInclusive\":10", "\"minScoreInclusive\":null"),
                BODY.replace("\"minScoreInclusive\":10", "\"minScoreInclusive\":-1"),
                BODY.replace("\"minScoreInclusive\":10", "\"minScoreInclusive\":1.0"),
                BODY.replace("\"minScoreInclusive\":10", "\"minScoreInclusive\":1e1"),
                BODY.replace("\"minScoreInclusive\":10", "\"minScoreInclusive\":\"10\""),
                BODY.replace("\"minScoreInclusive\":10", "\"minScoreInclusive\":true"),
                BODY.replace("\"minScoreInclusive\":10", "\"minScoreInclusive\":2147483648"),
                BODY.replace("\"minScoreInclusive\":10", "\"minScoreInclusive\":9223372036854775808123456789"),
                BODY.replace("\"maxScoreInclusive\":null", "\"maxScoreInclusive\":9"),
                BODY.replace("\"maxScoreInclusive\":null", "\"maxScoreInclusive\":2147483648"),
                BODY.replace("\"maxScoreInclusive\":null", "\"maxScoreInclusive\":10.0000000000000001"),
                BODY.replace("\"maxScoreInclusive\":null", "\"maxScoreInclusive\":\"10\""),
                BODY.replace("\"maxScoreInclusive\":null", "\"maxScoreInclusive\":false"),
                BODY.replace("\"approvalLevel\":3", "\"approvalLevel\":0"),
                BODY.replace("\"approvalLevel\":3", "\"approvalLevel\":4"),
                BODY.replace("\"approvalLevel\":3", "\"approvalLevel\":3.0"),
                BODY.replace("\"approvalLevel\":3", "\"approvalLevel\":\"3\""),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":-1"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":1.0"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":true"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":null"),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":\"9223372036854775808\""),
                BODY.replace("\"expectedRowVersion\":\"0\"", "\"expectedRowVersion\":\"0\",\"bandConfigId\":99"));
    }
    @ParameterizedTest @MethodSource("invalidBodies")
    void invalidFieldsReturn400BeforeDatabaseAccess(String body) throws Exception {
        mvc.perform(put(BASE + "/LOW").header("Authorization", "Bearer " + manager()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(versions, bands);
    }
    @ParameterizedTest @ValueSource(strings = {"low", "OTHER"})
    void unsupportedBandCodesAreInvalid(String code) throws Exception {
        mvc.perform(put(BASE + "/" + code).header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(versions, bands);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidVersionIdsReturn400(String id) throws Exception {
        mvc.perform(get("/api/v1/configuration/versions/" + id + "/risk-bands").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(versions, bands);
    }
    @ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
    void missingVersionsReturn404(String method) throws Exception {
        var request = method.equals("GET") ? get(BASE) : put(BASE + "/LOW").contentType("application/json").content(BODY);
        mvc.perform(request.header("Authorization", "Bearer " + manager())).andExpect(status().isNotFound());
        verifyNoInteractions(bands);
    }
    @ParameterizedTest @ValueSource(strings = {"STALE", "PUBLISHED", "EXHAUSTED"})
    void protectedParentsAreRejectedBeforeChildAccess(String scenario) throws Exception {
        long row = scenario.equals("EXHAUSTED") ? Long.MAX_VALUE : 1;
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent(scenario.equals("PUBLISHED") ? "PUBLISHED" : "DRAFT", row)));
        String body = scenario.equals("EXHAUSTED") ? BODY.replace("\"0\"", "\"" + row + "\"") : BODY;
        mvc.perform(put(BASE + "/LOW").header("Authorization", "Bearer " + manager()).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(scenario.equals("STALE") ? "STALE_VERSION"
                        : scenario.equals("PUBLISHED") ? "VERSION_NOT_DRAFT" : "ROW_VERSION_EXHAUSTED"));
        verifyNoInteractions(bands);
        verify(versions, never()).incrementParent(anyLong(), anyLong());
    }
    @ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
    void endpointsRequireValidManagerAuthentication(String method) throws Exception {
        for (String access : List.of("missing", "invalid", "expired", "PAYMENT_REQUESTER", "FINANCE_APPROVER")) {
            var request = method.equals("GET") ? get(BASE) : put(BASE + "/LOW").contentType("application/json").content(BODY);
            if (!access.equals("missing")) request.header("Authorization", "Bearer " + (access.equals("invalid") ? "invalid"
                    : token(access.equals("expired") ? "FINANCE_MANAGER" : access,
                            access.equals("expired") ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(300))));
            boolean forbidden = access.equals("PAYMENT_REQUESTER") || access.equals("FINANCE_APPROVER");
            var result = mvc.perform(request).andExpect(status().is(forbidden ? 403 : 401));
            if (forbidden) result.andExpect(header().doesNotExist("WWW-Authenticate"));
            else result.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer realm=")));
        }
        verifyNoInteractions(versions, bands);
    }
    @Test void bandUniquenessConflictIs409AndDoesNotIncrementParent() throws Exception {
        when(versions.findVersion(7, true)).thenReturn(Optional.of(parent("DRAFT", 0)));
        when(bands.save(anyLong(), anyString(), any(), any())).thenThrow(new DraftBandConflictException());
        mvc.perform(put(BASE + "/LOW").header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BAND_CODE_CONFLICT"));
        verify(versions, never()).incrementParent(anyLong(), anyLong());
    }
    @Test void databaseErrorsAreSanitized() throws Exception {
        when(versions.findVersion(7, true)).thenThrow(new DataAccessResourceFailureException("private Oracle SQL"));
        mvc.perform(put(BASE + "/LOW").header("Authorization", "Bearer " + manager()).contentType("application/json").content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("private Oracle SQL"))));
    }
}
