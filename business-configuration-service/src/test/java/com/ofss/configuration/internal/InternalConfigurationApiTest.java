package com.ofss.configuration.internal;

import com.ofss.configuration.activation.*;
import com.ofss.configuration.error.*;
import com.ofss.configuration.security.*;
import com.ofss.internal.*;
import io.jsonwebtoken.*;
import java.security.KeyPair;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({InternalConfigurationController.class,ConfigurationActivationController.class})
@Import({InternalSecurityConfiguration.class,SecurityConfiguration.class,JwtVerifier.class,ErrorResponseWriter.class,ApiExceptionHandler.class})
class InternalConfigurationApiTest {
    static final KeyPair KEY=Jwts.SIG.RS256.keyPair().build(),OTHER=Jwts.SIG.RS256.keyPair().build();
    static final String USER_KEY=HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    static final String ISSUER="ephemeral-internal-issuer",AUDIENCE="business-configuration-service";
    static final String JSON="{\"hashFormat\":\"configuration-content-v1\",\"versionId\":\"7\",\"riskRules\":[{\"parameters\":{\"exact\":1.0000000000000001,\"large\":9007199254740993}}],\"riskBands\":[],\"approvalThresholds\":[{\"minAmountInclusive\":\"0.00\",\"maxAmountExclusive\":null}],\"approvalSlas\":[]}";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("security.jwt.secret",()->USER_KEY);r.add("internal-access.enabled",()->true);r.add("internal-access.issuer",()->ISSUER);r.add("internal-access.audience",()->AUDIENCE);
        r.add("internal-access.public-keys",()->"{\"current\":\""+Base64.getEncoder().encodeToString(KEY.getPublic().getEncoded())+"\"}");
        r.add("internal-access.service-permissions",()->"{\"risk-assessment-service\":[\"CONFIGURATION_ACTIVE_READ\",\"CONFIGURATION_VERSION_READ\"],\"approval-service\":[\"CONFIGURATION_VERSION_READ\"]}");
    }
    @Autowired MockMvc mvc;
    @MockitoBean ConfigurationActivationService service;
    JwtBuilder serviceBuilder(String identity,List<String> permissions) { return Jwts.builder().header().type(InternalServiceVerifier.TYPE).keyId("current").and().issuer(ISSUER).audience().add(AUDIENCE).and()
        .subject(identity).claim("principalType","SERVICE").claim("permissions",permissions).expiration(Date.from(Instant.now().plusSeconds(300))); }
    String token(String permission) { return serviceBuilder("risk-assessment-service",List.of(permission)).signWith(KEY.getPrivate(),Jwts.SIG.RS256).compact(); }
    String user(boolean serviceMarker) {
        var builder=Jwts.builder().subject("manager").claim("userId",42).claim("roles",List.of("FINANCE_MANAGER")).expiration(Date.from(Instant.now().plusSeconds(300)));
        if(serviceMarker)builder.claim("principalType","SERVICE");
        return builder.signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(USER_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
    }
    ConfigurationActivationService.ActiveSnapshot active() {return new ConfigurationActivationService.ActiveSnapshot("7","9","0","2026-10-15T08:10:00Z","42","2026-10-15T08:09:00Z","42","a".repeat(64),"configuration-content-v1",JSON);}
    @ParameterizedTest @ValueSource(strings={"Bearer","bearer","BEARER"})
    void currentSnapshotUsesSeparateServiceSecurityAndPreservesExactJson(String scheme) throws Exception {
        when(service.active()).thenReturn(active());
        mvc.perform(get("/internal/v1/configuration/active").header("Authorization",scheme+" "+token(ServicePermissions.CONFIG_ACTIVE)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.versionId").value("7")).andExpect(jsonPath("$.pointerRowVersion").value("0"))
            .andExpect(jsonPath("$.configuration.approvalSlas").isArray()).andExpect(content().string(containsString("1.0000000000000001")))
            .andExpect(content().string(containsString("9007199254740993"))).andExpect(content().string(not(containsString("leaseToken"))));
        verify(service).active();
    }
    @Test void pinnedVersionUsesExplicitVersionWithoutActiveSelection() throws Exception {
        when(service.published(7)).thenReturn(new ConfigurationActivationService.PublishedSnapshot("7","9","2026-10-15T08:09:00Z","42","a".repeat(64),"configuration-content-v1",JSON));
        var token=serviceBuilder("approval-service",List.of(ServicePermissions.CONFIG_VERSION)).signWith(KEY.getPrivate(),Jwts.SIG.RS256).compact();
        mvc.perform(get("/internal/v1/configuration/versions/7").header("Authorization","Bearer "+token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.configuration.approvalThresholds[0].minAmountInclusive").value("0.00")).andExpect(jsonPath("$.pointerRowVersion").doesNotExist());
        verify(service).published(7);verify(service,never()).active();
    }
    @Test void permissionSeparationCannotBorrowAnotherServiceOrRouteGrant() throws Exception {
        mvc.perform(get("/internal/v1/configuration/versions/7").header("Authorization","Bearer "+token(ServicePermissions.CONFIG_ACTIVE))).andExpect(status().isForbidden());
        var approval=serviceBuilder("approval-service",List.of(ServicePermissions.CONFIG_VERSION)).signWith(KEY.getPrivate(),Jwts.SIG.RS256).compact();
        mvc.perform(get("/internal/v1/configuration/active").header("Authorization","Bearer "+approval)).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"missing","invalid","wrong-key","issuer","audience","identity","expired","user"})
    void invalidAuthenticationIsSanitizedBeforeAnyRead(String failure) throws Exception {
        var request=get("/internal/v1/configuration/active");
        if(!failure.equals("missing")) {
            var builder=serviceBuilder("risk-assessment-service",List.of(ServicePermissions.CONFIG_ACTIVE));
            if(failure.equals("issuer"))builder.issuer("wrong");if(failure.equals("audience"))builder.audience().clear().add("risk-assessment-service").and();
            if(failure.equals("identity"))builder.subject("unlisted");if(failure.equals("expired"))builder.expiration(Date.from(Instant.now().minusSeconds(30)));
            String token=failure.equals("invalid")?"private-invalid-token":failure.equals("user")?user(false):builder.signWith(failure.equals("wrong-key")?OTHER.getPrivate():KEY.getPrivate(),Jwts.SIG.RS256).compact();
            request.header("Authorization","Bearer "+token);
        }
        var result=mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(content().string(not(containsString("private-invalid-token")))).andExpect(header().string("WWW-Authenticate",containsString("Bearer")));
        if(!failure.equals("missing"))result.andExpect(header().string("WWW-Authenticate",containsString("invalid_token")));
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"service","service-marker-with-user-key"})
    void serviceCredentialsNeverBecomeManagerUsers(String kind) throws Exception {
        String credential=kind.equals("service")?token(ServicePermissions.CONFIG_ACTIVE):user(true);
        mvc.perform(get("/api/v1/configuration/active").header("Authorization","Bearer "+credential)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/configuration/versions/7/activate").header("Authorization","Bearer "+credential).contentType("application/json").content("{\"expectedPointerRowVersion\":null}"))
            .andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"publish","activate","risk-rules","unknown"})
    void noInternalMutationIsAuthorized(String action) throws Exception {
        mvc.perform(post("/internal/v1/configuration/versions/7/"+action).header("Authorization","Bearer "+token(ServicePermissions.CONFIG_ACTIVE)).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","9223372036854775808"})
    void invalidPinnedIdsAre400BeforeService(String id) throws Exception {
        mvc.perform(get("/internal/v1/configuration/versions/"+id).header("Authorization","Bearer "+token(ServicePermissions.CONFIG_VERSION))).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void failuresAndNoActiveUseExistingSanitizedErrors() throws Exception {
        when(service.active()).thenThrow(new ActivationFailure(ActivationFailure.Reason.NO_ACTIVE_CONFIGURATION));
        mvc.perform(get("/internal/v1/configuration/active").header("Authorization","Bearer "+token(ServicePermissions.CONFIG_ACTIVE))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NO_ACTIVE_CONFIGURATION"));
        when(service.published(7)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private SQL detail"));
        mvc.perform(get("/internal/v1/configuration/versions/7").header("Authorization","Bearer "+token(ServicePermissions.CONFIG_VERSION))).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE")).andExpect(content().string(not(containsString("private SQL detail"))));
    }
}
