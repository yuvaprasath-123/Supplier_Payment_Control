package com.ofss.configuration.readiness;

import com.ofss.configuration.band.DraftRiskBandRepository;
import com.ofss.configuration.error.*;
import com.ofss.configuration.rule.DraftRiskRuleRepository;
import com.ofss.configuration.security.*;
import com.ofss.configuration.sla.DraftApprovalSlaRepository;
import com.ofss.configuration.threshold.DraftApprovalThresholdRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static com.ofss.configuration.readiness.ReadinessFixtures.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PublicationReadinessController.class)
@Import({PublicationReadinessService.class, PublicationReadinessValidator.class, SecurityConfiguration.class,
        JwtVerifier.class, ErrorResponseWriter.class, ApiExceptionHandler.class})
class PublicationReadinessApiTest {
    private static final String PATH = "/api/v1/configuration/versions/7/publication-readiness";
    private static final String SECRET = HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    @Autowired private MockMvc mvc;
    @MockitoBean private DraftRiskRuleRepository versionsAndRules;
    @MockitoBean private PublicationReadinessRuleRepository rules;
    @MockitoBean private DraftRiskBandRepository bands;
    @MockitoBean private DraftApprovalThresholdRepository thresholds;
    @MockitoBean private DraftApprovalSlaRepository slas;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("security.jwt.secret", () -> SECRET); }
    private String token(String role, boolean expired) {
        return Jwts.builder().subject("manager").claim("userId", "42").claim("roles", List.of(role))
                .expiration(Date.from(Instant.now().plusSeconds(expired ? -60 : 300))).signWith(KEY).compact();
    }
    private String manager() { return token("FINANCE_MANAGER", false); }
    private void complete() {
        when(versionsAndRules.findVersion(7, false)).thenReturn(Optional.of(parent("SUM", null, true)));
        when(rules.list(7)).thenReturn(ReadinessFixtures.rules());
        when(bands.list(7)).thenReturn(ReadinessFixtures.bands());
        when(thresholds.list(7)).thenReturn(ReadinessFixtures.thresholds());
        when(slas.list(7)).thenReturn(ReadinessFixtures.slas());
    }
    @Test void completeSupportedPoliciesAreReadyWithoutWrites() throws Exception {
        complete();
        mvc.perform(get(PATH).header("Authorization", "Bearer " + manager())).andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value("7"))
                .andExpect(jsonPath("$.rowVersion").value("8"))
                .andExpect(jsonPath("$.publicationState").value("DRAFT"))
                .andExpect(jsonPath("$.isDemonstration").value(true))
                .andExpect(jsonPath("$.structurallyValid").value(true))
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.issues", hasSize(0)));
        var order = inOrder(versionsAndRules, rules, bands, thresholds, slas);
        order.verify(versionsAndRules).findVersion(7, false);
        order.verify(rules).list(7);
        order.verify(bands).list(7);
        order.verify(thresholds).list(7);
        order.verify(slas).list(7);
        verifyNoMoreInteractions(versionsAndRules, rules, bands, thresholds, slas);
    }
    @Test void emptyDraftReportsConfigurationErrorsSeparatelyFromPolicyBlockers() throws Exception {
        when(versionsAndRules.findVersion(7, false)).thenReturn(Optional.of(parent("SUM", null, false)));
        mvc.perform(get(PATH).header("Authorization", "Bearer " + manager())).andExpect(status().isOk())
                .andExpect(jsonPath("$.structurallyValid").value(false)).andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.issues[?(@.code=='SECTION_MISSING')]", hasSize(4)))
                .andExpect(jsonPath("$.issues[?(@.category=='POLICY_BLOCKER')]", hasSize(0)))
                .andExpect(jsonPath("$.issues[0].category").value("CONFIGURATION_ERROR"));
    }
    @Test void absentVersionIs404WithoutChildReads() throws Exception {
        mvc.perform(get(PATH).header("Authorization", "Bearer " + manager())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VERSION_NOT_FOUND"));
        verifyNoInteractions(rules, bands, thresholds, slas);
    }
    @ParameterizedTest @ValueSource(strings = {"PARENT", "RULES", "BANDS", "THRESHOLDS", "SLAS"})
    void readFailureCannotReturnPartialReadinessOrLeakDetails(String stage) throws Exception {
        complete();
        var failure = new DataAccessResourceFailureException("private database detail");
        switch (stage) {
            case "PARENT" -> when(versionsAndRules.findVersion(7, false)).thenThrow(failure);
            case "RULES" -> when(rules.list(7)).thenThrow(failure);
            case "BANDS" -> when(bands.list(7)).thenThrow(failure);
            case "THRESHOLDS" -> when(thresholds.list(7)).thenThrow(failure);
            case "SLAS" -> when(slas.list(7)).thenThrow(failure);
        }
        mvc.perform(get(PATH).header("Authorization", "Bearer " + manager())).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.ready").doesNotExist()).andExpect(jsonPath("$.issues").doesNotExist())
                .andExpect(content().string(not(containsString("private database detail"))));
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "invalid", "expired", "PAYMENT_REQUESTER", "FINANCE_APPROVER"})
    void validManagerAuthenticationRequiredBeforeDatabase(String access) throws Exception {
        var request = get(PATH);
        if (!access.equals("missing")) request.header("Authorization", "Bearer " + (access.equals("invalid") ? "invalid"
                : token(access.equals("expired") ? "FINANCE_MANAGER" : access, access.equals("expired"))));
        boolean forbidden = access.equals("PAYMENT_REQUESTER") || access.equals("FINANCE_APPROVER");
        var result = mvc.perform(request).andExpect(status().is(forbidden ? 403 : 401));
        if (forbidden) result.andExpect(header().doesNotExist("WWW-Authenticate"));
        else result.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer realm=")));
        verifyNoInteractions(versionsAndRules, rules, bands, thresholds, slas);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidPathIs400(String id) throws Exception {
        mvc.perform(get("/api/v1/configuration/versions/" + id + "/publication-readiness").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(versionsAndRules, rules, bands, thresholds, slas);
    }
    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void mutationMethodsDeniedEvenForManager(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), PATH).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(versionsAndRules, rules, bands, thresholds, slas);
    }
}
