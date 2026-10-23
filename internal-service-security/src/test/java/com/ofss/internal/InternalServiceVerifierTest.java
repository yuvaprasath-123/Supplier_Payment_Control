package com.ofss.internal;

import io.jsonwebtoken.*;
import java.security.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class InternalServiceVerifierTest {
    static final KeyPair KEY=Jwts.SIG.RS256.keyPair().build();
    static final KeyPair OTHER=Jwts.SIG.RS256.keyPair().build();
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String KEYS=JSON.writeValueAsString(Map.of("current",Base64.getEncoder().encodeToString(KEY.getPublic().getEncoded())));
    static final String GRANTS=JSON.writeValueAsString(Map.of("risk-assessment-service",List.of(ServicePermissions.CONFIG_ACTIVE,ServicePermissions.CONFIG_VERSION),
            "approval-service",List.of(ServicePermissions.CONFIG_VERSION)));
    final InternalServiceVerifier verifier=new InternalServiceVerifier(true,"test-internal-issuer","configuration",KEYS,GRANTS,ServicePermissions.CONFIGURATION);
    JwtBuilder builder() { return Jwts.builder().header().type(InternalServiceVerifier.TYPE).keyId("current").and().issuer("test-internal-issuer").audience().add("configuration").and()
        .subject("risk-assessment-service").claim("principalType","SERVICE").claim("permissions",List.of(ServicePermissions.CONFIG_ACTIVE))
        .expiration(Date.from(Instant.now().plusSeconds(300))); }
    @Test void verifiedServiceHasOnlyExplicitServiceAuthoritiesAndNoCredentialRetention() {
        var auth=verifier.authenticate(builder().signWith(KEY.getPrivate(),Jwts.SIG.RS256).compact());
        assertThat(auth.getPrincipal()).isEqualTo(new ServicePrincipal("risk-assessment-service"));assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly(ServicePermissions.authority(ServicePermissions.CONFIG_ACTIVE));
    }
    @ParameterizedTest @ValueSource(strings={"wrong-key","issuer","audience","extra-audience","unknown-identity","blank-identity","expired","no-exp","no-iss","no-aud","no-sub","no-type","no-kid","unknown-kid","no-principal-type","user-principal","no-permissions","empty-permissions","string-permissions","duplicate-permissions","extra-grant","user-id","roles","wrong-algorithm"})
    void invalidOrCrossDomainCredentialsFailClosed(String failure) {
        var token=builder();
        switch(failure) {
            case "issuer"->token.issuer("wrong"); case "audience"->token.audience().clear().add("risk").and();
            case "extra-audience"->token.audience().add("risk").and(); case "unknown-identity"->token.subject("unlisted-service");
            case "blank-identity"->token.subject(" "); case "expired"->token.expiration(Date.from(Instant.now().minusSeconds(30)));
            case "no-exp"->token.expiration(null);case "no-iss"->token.issuer(null);case "no-aud"->token.audience().clear().and();case "no-sub"->token.subject(null);
            case "no-type"->token.header().type(null).and();case "no-kid"->token.header().keyId(null).and();case "unknown-kid"->token.header().keyId("unknown").and();
            case "no-principal-type"->token.claim("principalType",null);case "user-principal"->token.claim("principalType","USER");
            case "no-permissions"->token.claim("permissions",null);case "empty-permissions"->token.claim("permissions",List.of());case "string-permissions"->token.claim("permissions",ServicePermissions.CONFIG_ACTIVE);
            case "duplicate-permissions"->token.claim("permissions",List.of(ServicePermissions.CONFIG_ACTIVE,ServicePermissions.CONFIG_ACTIVE));
            case "extra-grant"->token.subject("approval-service").claim("permissions",List.of(ServicePermissions.CONFIG_ACTIVE));
            case "user-id"->token.claim("userId",42);case "roles"->token.claim("roles",List.of("FINANCE_MANAGER"));
        }
        String encoded=failure.equals("wrong-algorithm")?token.signWith(KEY.getPrivate(),Jwts.SIG.PS256).compact():token.signWith(failure.equals("wrong-key")?OTHER.getPrivate():KEY.getPrivate(),Jwts.SIG.RS256).compact();
        assertThatThrownBy(()->verifier.authenticate(encoded)).isInstanceOfAny(JwtException.class,IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"issuer","audience","keys","grants","malformed-key","unknown-permission","bad-identity","empty-grants","duplicate-grants","duplicate-identity"})
    void enabledModeRequiresCompleteExplicitConfigurationWithoutLeakingInput(String missing) {
        String issuer=missing.equals("issuer")?"":"test-internal-issuer",audience=missing.equals("audience")?"":"configuration";
        String keys=missing.equals("keys")?"{}":missing.equals("malformed-key")?"{\"kid\":\"private-fixture-value\"}":KEYS;
        String grants=switch(missing){case "grants"->"";case "unknown-permission"->"{\"approval-service\":[\"FINANCE_MANAGER\"]}";case "bad-identity"->"{\" \":[\"CONFIGURATION_ACTIVE_READ\"]}";
            case "empty-grants"->"{\"approval-service\":[]}";case "duplicate-grants"->"{\"approval-service\":[\"CONFIGURATION_ACTIVE_READ\",\"CONFIGURATION_ACTIVE_READ\"]}";
            case "duplicate-identity"->"{\"approval-service\":[\"CONFIGURATION_ACTIVE_READ\"],\"approval-service\":[\"CONFIGURATION_VERSION_READ\"]}";default->GRANTS;};
        assertThatThrownBy(()->new InternalServiceVerifier(true,issuer,audience,keys,grants,ServicePermissions.CONFIGURATION)).isInstanceOf(IllegalStateException.class)
            .hasMessage("Complete valid external internal-access configuration is required").hasNoCause();
    }
    @Test void disabledModeNeedsNoCredentialsAndNeverAuthenticates() {
        var disabled=new InternalServiceVerifier(false,null,null,null,null,Set.of());assertThat(disabled.enabled()).isFalse();
        assertThatThrownBy(()->disabled.authenticate("anything")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rotationRequiresKnownKidAndAllowsBoundedOldNewOverlap() {
        var keys=JSON.writeValueAsString(Map.of("current",Base64.getEncoder().encodeToString(KEY.getPublic().getEncoded()),"next",Base64.getEncoder().encodeToString(OTHER.getPublic().getEncoded())));
        var rotating=new InternalServiceVerifier(true,"test-internal-issuer","configuration",keys,GRANTS,ServicePermissions.CONFIGURATION);
        assertThat(rotating.authenticate(builder().signWith(KEY.getPrivate(),Jwts.SIG.RS256).compact()).isAuthenticated()).isTrue();
        assertThat(rotating.authenticate(builder().header().keyId("next").and().signWith(OTHER.getPrivate(),Jwts.SIG.RS256).compact()).isAuthenticated()).isTrue();
    }
}
