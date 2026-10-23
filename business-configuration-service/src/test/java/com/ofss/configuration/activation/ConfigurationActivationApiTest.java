package com.ofss.configuration.activation;

import com.ofss.configuration.error.*;
import com.ofss.configuration.security.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigurationActivationController.class)
@Import({SecurityConfiguration.class,JwtVerifier.class,ErrorResponseWriter.class,ApiExceptionHandler.class})
class ConfigurationActivationApiTest {
    static final String ACTIVATE="/api/v1/configuration/versions/7/activate", ACTIVE="/api/v1/configuration/active";
    static final String KEY=HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {r.add("security.jwt.secret",()->KEY);}
    @Autowired MockMvc mvc;
    @MockitoBean ConfigurationActivationService service;
    String token(String role,boolean expired) {
        return Jwts.builder().subject("manager-fixture").claim("userId","9007199254740993").claim("roles",List.of(role))
                .expiration(Date.from(Instant.now().plusSeconds(expired?-60:300))).signWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8))).compact();
    }
    ConfigurationActivationService.ActiveSnapshot snapshot() {
        return new ConfigurationActivationService.ActiveSnapshot("7","9007199254740993","0","2026-01-02T00:00:00Z","9007199254740993",
                "2026-01-01T00:00:00Z","1","a".repeat(64),"configuration-content-v1",
                "{\"riskRules\":[{\"parameters\":{\"ratio\":1.0000000000000001}}],\"approvalThresholds\":[{\"minAmountInclusive\":\"0.00\"}],\"riskBands\":[],\"approvalSlas\":[]}");
    }
    @ParameterizedTest @ValueSource(strings={"Bearer","bearer","BEARER"})
    void managerGetsVerifiedRawSnapshotAndTrustedActor(String scheme) throws Exception {
        when(service.activate(7,new ActivationRequest(null),9007199254740993L)).thenReturn(snapshot());when(service.active()).thenReturn(snapshot());
        mvc.perform(post(ACTIVATE).header("Authorization",scheme+" "+token("FINANCE_MANAGER",false)).contentType("application/json").content("{\"expectedPointerRowVersion\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pointerRowVersion").value("0"))
                .andExpect(jsonPath("$.configuration.approvalThresholds[0].minAmountInclusive").value("0.00"))
                .andExpect(content().string(containsString("1.0000000000000001")));
        verify(service).activate(7,new ActivationRequest(null),9007199254740993L);
        mvc.perform(get(ACTIVE).header("Authorization",scheme+" "+token("FINANCE_MANAGER",false))).andExpect(status().isOk()).andExpect(jsonPath("$.versionId").value("7"));
    }
    @ParameterizedTest @ValueSource(strings={"missing","invalid","expired","PAYMENT_REQUESTER","FINANCE_APPROVER"})
    void bothRoutesRejectUnauthorizedAccess(String access) throws Exception {
        boolean forbidden=Set.of("PAYMENT_REQUESTER","FINANCE_APPROVER").contains(access);
        for(var request:List.of(get(ACTIVE),post(ACTIVATE).contentType("application/json").content("{\"expectedPointerRowVersion\":null}"))) {
            if(!access.equals("missing"))request.header("Authorization","Bearer "+(access.equals("invalid")?"invalid":token(access.equals("expired")?"FINANCE_MANAGER":access,access.equals("expired"))));
            var result=mvc.perform(request).andExpect(status().is(forbidden?403:401));
            if(!forbidden)result.andExpect(header().string("WWW-Authenticate",containsString("Bearer")));
        }
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","[]","{\"expectedPointerRowVersion\":1.0}","{\"expectedPointerRowVersion\":true}","{\"expectedPointerRowVersion\":-1}","{\"expectedPointerRowVersion\":\"9223372036854775808\"}","{\"expectedPointerRowVersion\":null,\"activatedByUserId\":1}"})
    void strictBody(String body) throws Exception {
        mvc.perform(post(ACTIVATE).header("Authorization","Bearer "+token("FINANCE_MANAGER",false)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.0","abc","9223372036854775808"})
    void invalidId(String id) throws Exception {
        mvc.perform(post(ACTIVATE.replace("/7/","/"+id+"/")).header("Authorization","Bearer "+token("FINANCE_MANAGER",false)).contentType("application/json").content("{\"expectedPointerRowVersion\":null}"))
                .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void missingActiveAndCorruptionSanitized() throws Exception {
        when(service.active()).thenThrow(new ActivationFailure(ActivationFailure.Reason.NO_ACTIVE_CONFIGURATION));
        mvc.perform(get(ACTIVE).header("Authorization","Bearer "+token("FINANCE_MANAGER",false))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NO_ACTIVE_CONFIGURATION"));
        doThrow(new org.springframework.dao.DataRetrievalFailureException("private JDBC data")).when(service).active();
        mvc.perform(get(ACTIVE).header("Authorization","Bearer "+token("FINANCE_MANAGER",false))).andExpect(status().isServiceUnavailable()).andExpect(content().string(not(containsString("private JDBC"))));
    }
    @ParameterizedTest @ValueSource(strings={"STALE_ACTIVE_CONFIGURATION","VERSION_NOT_PUBLISHED","ACTIVATION_REVISION_EXHAUSTED"})
    void conflicts(String reason) throws Exception {
        when(service.activate(anyLong(),any(),anyLong())).thenThrow(new ActivationFailure(ActivationFailure.Reason.valueOf(reason)));
        mvc.perform(post(ACTIVATE).header("Authorization","Bearer "+token("FINANCE_MANAGER",false)).contentType("application/json").content("{\"expectedPointerRowVersion\":null}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(reason));
    }
}
