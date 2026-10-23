package com.ofss.internal;

import com.ofss.internal.verification.*;
import java.net.URI;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Offline only: never invokes EphemeralReceiver.start or sends an HTTP request. */
class EphemeralHttpSupportTest {
    @ParameterizedTest @ValueSource(strings={"https://127.0.0.1:8087","http://example.com:8087","http://127.0.0.1:8087/prefix","http://user@127.0.0.1:8087","http://127.0.0.1:8087/?x=1","http://127.0.0.1:8087/#fragment","http://127.0.0.1"})
    void nonLoopbackOrAmbiguousTargetsCannotReceiveTestTokens(String base){assertThrows(IllegalArgumentException.class,()->InternalReadHttp.target(URI.create(base),"/internal/v1/configuration/active"));}
    @ParameterizedTest @ValueSource(strings={"//example.com/path","/internal/v1/../secret","/api/v1/x?token=x","/internal/v1/x#fragment","/unrelated/path"})
    void noPathOrOriginOverride(String path){assertThrows(IllegalArgumentException.class,()->InternalReadHttp.target(URI.create("http://127.0.0.1:1234"),path));}
    @Test void allShutdownsAreAttemptedAndAnyFailureDefersCleanup(){
        var attempts=new ArrayList<Integer>();boolean safe=VerificationShutdown.closeAll(()->{attempts.add(1);throw new IllegalStateException("private fixture detail");},()->attempts.add(2),()->attempts.add(3));
        assertFalse(safe);assertEquals(List.of(1,2,3),attempts);assertTrue(VerificationShutdown.closeAll(null,()->{}));
    }
    @Test void fixtureServerPropertiesPinLoopbackDisableInitializersAndKeepCredentialsTestLocal(){
        var keys=new EphemeralCredentials();var properties=EphemeralReceiver.properties(keys,"test-audience","{}",true,Map.of("spring.datasource.password","test-only"));
        assertEquals("127.0.0.1",properties.get("server.address"));assertEquals(0,properties.get("server.port"));assertEquals("never",properties.get("spring.sql.init.mode"));
        assertEquals("none",properties.get("spring.jpa.hibernate.ddl-auto"));assertEquals(false,properties.get("spring.flyway.enabled"));assertEquals(false,properties.get("spring.liquibase.enabled"));
        assertFalse(keys.toString().contains(keys.userSecret()),"Credential toString must not reveal values");assertFalse(properties.containsKey("privateKey"));
        var disabled=EphemeralReceiver.properties(keys,"unused","unused",false,Map.of());
        for(String property:List.of("internal-access.issuer","internal-access.audience","internal-access.public-keys","internal-access.service-permissions"))assertEquals("",disabled.get(property));
    }
    @Test void testTokensVerifyOnlyWithExplicitEphemeralTrustAndRotationKeys(){
        var keys=new EphemeralCredentials();var verifier=new InternalServiceVerifier(true,EphemeralCredentials.ISSUER,"test-audience",keys.publicKeys(),"{\"approval-service\":[\"CONFIGURATION_VERSION_READ\"]}",ServicePermissions.CONFIGURATION);
        for(String variant:List.of("valid","next"))assertTrue(verifier.authenticate(keys.service("test-audience","approval-service",List.of(ServicePermissions.CONFIG_VERSION),variant)).isAuthenticated());
        for(String variant:List.of("wrong-key","unknown-kid","issuer","expired","missing-sub","missing-exp","missing-type","missing-permissions"))
            assertThrows(RuntimeException.class,()->verifier.authenticate(keys.service("test-audience","approval-service",List.of(ServicePermissions.CONFIG_VERSION),variant)));
        assertThrows(RuntimeException.class,()->verifier.authenticate(keys.user()));
    }
    @Test void weakWrongTypeAndPrivateKeyEncodingsAreRejectedWithoutDiagnosticCause() throws Exception {
        var weak=KeyPairGenerator.getInstance("RSA");weak.initialize(1024);var weakPair=weak.generateKeyPair();
        var ec=KeyPairGenerator.getInstance("EC").generateKeyPair();var rsa=KeyPairGenerator.getInstance("RSA").generateKeyPair();
        for(byte[] encoded:List.of(weakPair.getPublic().getEncoded(),ec.getPublic().getEncoded(),rsa.getPrivate().getEncoded())){
            var failure=assertThrows(IllegalStateException.class,()->new InternalServiceVerifier(true,"issuer","audience","{\"key\":\""+Base64.getEncoder().encodeToString(encoded)+"\"}","{\"approval-service\":[\"CONFIGURATION_VERSION_READ\"]}",ServicePermissions.CONFIGURATION));
            assertNull(failure.getCause());assertEquals("Complete valid external internal-access configuration is required",failure.getMessage());
        }
    }
}
