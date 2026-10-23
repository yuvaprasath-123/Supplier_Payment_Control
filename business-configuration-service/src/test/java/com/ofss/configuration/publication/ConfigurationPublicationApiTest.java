package com.ofss.configuration.publication;

import com.ofss.configuration.error.*;
import com.ofss.configuration.readiness.PublicationReadiness;
import com.ofss.configuration.rule.DraftRuleConflictException;
import com.ofss.configuration.rule.ConfigurationVersionNotFoundException;
import com.ofss.configuration.security.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigurationPublicationController.class)
@Import({SecurityConfiguration.class,JwtVerifier.class,ErrorResponseWriter.class,ApiExceptionHandler.class})
class ConfigurationPublicationApiTest {
    private static final String PATH="/api/v1/configuration/versions/7/publish";
    private static final String KEY=HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("security.jwt.secret",()->KEY); }
    @Autowired MockMvc mvc;
    @MockitoBean ConfigurationPublicationService service;
    private String token(String role,boolean expired) {
        return Jwts.builder().subject("synthetic-manager").claim("userId","9007199254740993").claim("roles",List.of(role))
            .expiration(Date.from(Instant.now().plusSeconds(expired?-60:300))).signWith(Keys.hmacShaKeyFor(KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
    }
    @ParameterizedTest @ValueSource(strings={"Bearer","bearer","BEARER"})
    void verifiedManagerSuppliesTrustedPublisherAndExactRevision(String scheme) throws Exception {
        when(service.publish(7,new PublishConfigurationRequest(9007199254740993L),9007199254740993L)).thenReturn(
            new ConfigurationPublicationService.PublishedVersion("7","9007199254740994","PUBLISHED","a".repeat(64),ConfigurationContentHash.FORMAT,"2026-10-15T08:09:10.123456Z","9007199254740993"));
        mvc.perform(post(PATH).header("Authorization",scheme+" "+token("FINANCE_MANAGER",false)).contentType("application/json")
            .content("{\"expectedRowVersion\":\"9007199254740993\"}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.versionId").value("7")).andExpect(jsonPath("$.rowVersion").value("9007199254740994"))
            .andExpect(jsonPath("$.publishedByUserId").value("9007199254740993")).andExpect(jsonPath("$.hashFormat").value(ConfigurationContentHash.FORMAT))
            .andExpect(jsonPath("$.publishedAt").value("2026-10-15T08:09:10.123456Z"));
        verify(service).publish(7,new PublishConfigurationRequest(9007199254740993L),9007199254740993L);
    }
    @ParameterizedTest @ValueSource(strings={"missing","invalid","expired","PAYMENT_REQUESTER","FINANCE_APPROVER","Basic"})
    void authenticationAndAuthorizationPrecedePublication(String access) throws Exception {
        var request=post(PATH).contentType("application/json").content("{\"expectedRowVersion\":0}");
        if(!access.equals("missing")) request.header("Authorization",access.equals("Basic")?"Basic fixture": "Bearer "+(access.equals("invalid")?"invalid":token(access.equals("expired")?"FINANCE_MANAGER":access,access.equals("expired"))));
        boolean forbidden=access.equals("PAYMENT_REQUESTER")||access.equals("FINANCE_APPROVER");
        var result=mvc.perform(request).andExpect(status().is(forbidden?403:401));
        if(forbidden) result.andExpect(header().doesNotExist("WWW-Authenticate"));
        else result.andExpect(header().string("WWW-Authenticate",containsString("Bearer")));
        if(access.equals("invalid")||access.equals("expired")) result.andExpect(header().string("WWW-Authenticate",containsString("error=\"invalid_token\"")));
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","[]","{\"expectedRowVersion\":null}","{\"expectedRowVersion\":true}","{\"expectedRowVersion\":1.0}","{\"expectedRowVersion\":1e0}","{\"expectedRowVersion\":-1}","{\"expectedRowVersion\":\"-1\"}","{\"expectedRowVersion\":9223372036854775808}","{\"expectedRowVersion\":\"9223372036854775808\"}","{\"expectedRowVersion\":0,\"publishedByUserId\":1}"})
    void malformedOrSpoofedRequestsAreRejected(String body) throws Exception {
        mvc.perform(post(PATH).header("Authorization","Bearer "+token("FINANCE_MANAGER",false)).contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST")); verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","9223372036854775808","abc"})
    void malformedIdsCannotReachService(String id) throws Exception {
        mvc.perform(post(PATH.replace("/7/","/"+id+"/")).header("Authorization","Bearer "+token("FINANCE_MANAGER",false)).contentType("application/json").content("{\"expectedRowVersion\":0}"))
            .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void unresolvedReadinessIsReturnedAsSanitizedStructuredConflict() throws Exception {
        var readiness=new PublicationReadiness("7","8","DRAFT",true,true,false,List.of(new PublicationReadiness.Issue(PublicationReadiness.Category.POLICY_BLOCKER,"SLA_POLICY_SEMANTICS_UNRESOLVED","/approval-slas","Policy agreement required")));
        when(service.publish(anyLong(),any(),anyLong())).thenThrow(new PublicationNotReadyException(readiness));
        mvc.perform(post(PATH).header("Authorization","Bearer "+token("FINANCE_MANAGER",false)).contentType("application/json").content("{\"expectedRowVersion\":8}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFIGURATION_NOT_READY"))
            .andExpect(jsonPath("$.readiness.ready").value(false)).andExpect(jsonPath("$.readiness.issues[0].category").value("POLICY_BLOCKER"));
    }
    @ParameterizedTest @ValueSource(strings={"published","stale","missing","database"})
    void domainFailuresHaveConsistentSanitizedErrors(String failure) throws Exception {
        RuntimeException exception=switch(failure) {
            case "published"->new AlreadyPublishedException(); case "stale"->new DraftRuleConflictException(DraftRuleConflictException.Reason.STALE_VERSION);
            case "missing"->new ConfigurationVersionNotFoundException(); default->new org.springframework.dao.DataAccessResourceFailureException("private JDBC detail");
        };
        when(service.publish(anyLong(),any(),anyLong())).thenThrow(exception);
        mvc.perform(post(PATH).header("Authorization","Bearer "+token("FINANCE_MANAGER",false)).contentType("application/json").content("{\"expectedRowVersion\":8}"))
            .andExpect(status().is(failure.equals("missing")?404:failure.equals("database")?503:409))
            .andExpect(content().string(not(containsString("private JDBC detail"))));
    }
    @Test void publicationRejectsUnsupportedMethodsAndActions() throws Exception {
        mvc.perform(get(PATH).header("Authorization","Bearer "+token("FINANCE_MANAGER",false))).andExpect(status().isForbidden());
        mvc.perform(post(PATH.replace("publish","unsupported-action")).header("Authorization","Bearer "+token("FINANCE_MANAGER",false))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
