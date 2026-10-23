package com.ofss.configuration.activation;

import com.ofss.configuration.BusinessConfigurationApplication;
import com.ofss.internal.*;
import com.ofss.internal.verification.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/security/JDBC using controlled empty PUBLISHED fixtures, NOT genuine publication or a team issuer.
 * Programmatic test servers start ONLY after explicit user opt-in and Oracle fixture guards. */
@EnabledIfEnvironmentVariable(named="D2_VERIFY_INTERNAL_HTTP_ORACLE_WRITES",matches="true")
@EnabledIfEnvironmentVariable(named="CONFIG_ORACLE_INTERNAL_HTTP_ISOLATED",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class OracleInternalConfigurationHttpTest {
    static final String AUDIENCE="business-configuration-service",ROOT="/internal/v1/configuration";
    static final String GRANTS="{\"risk-assessment-service\":[\"CONFIGURATION_ACTIVE_READ\",\"CONFIGURATION_VERSION_READ\"],\"approval-service\":[\"CONFIGURATION_VERSION_READ\"]}";
    final OracleConfigurationActivationTest fixture=new OracleConfigurationActivationTest();
    final EphemeralCredentials credentials=new EphemeralCredentials();
    EphemeralReceiver enabled,disabled;InternalReadHttp http;
    boolean safe=true,fixtureStarted;Map<String,Object> before;
    Map<String,Object> database(){String password=System.getenv("CONFIG_DB_PASSWORD");
        if(password==null||password.isBlank())throw new AssertionError("Supply CONFIG_DB_PASSWORD externally");
        return Map.of("spring.datasource.url","jdbc:oracle:thin:@//localhost:1521/FREEPDB1","spring.datasource.username","D2_CONFIGURATION","spring.datasource.password",password);}
    @BeforeAll void prepare(){
        fixture.connect(); // fixed owner/container guard, no web application or fixture writes yet
        fixtureStarted=true;fixture.fixtures(); // reviewed atomic two-version setup refuses an existing pointer
        fixture.service.activate(fixture.first,new ActivationRequest(null),OracleConfigurationActivationTest.ACTOR);
        before=state();http=new InternalReadHttp(()->safe=false);
        enabled=EphemeralReceiver.start(BusinessConfigurationApplication.class,credentials,AUDIENCE,GRANTS,true,database());
        System.out.println("Configuration internal HTTP uses EPHEMERAL TEST ISSUER only; fixture namespace: "+fixture.namespace);
    }
    Map<String,Object> state(){
        var result=new TreeMap<String,Object>();
        for(long id:new long[]{fixture.first,fixture.second}){
            result.put(Long.toString(id),fixture.versions.findVersion(id,false).orElseThrow());
            result.put("hash/"+id,fixture.jdbc.queryForObject("SELECT CONTENT_HASH FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_ID=?",String.class,id));
            for(String table:List.of("RISK_RULE_CONFIGS","RISK_BAND_CONFIGS","APPROVAL_THRESHOLD_CONFIGS","SLA_CONFIGS"))
                result.put(table+"/"+id,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM "SUPPLIER_PAYMENT_APP""+table+" WHERE VERSION_ID=?",Long.class,id));
        }
        result.put("pointer",fixture.tx.execute(s->fixture.context.getBean(ConfigurationActivationRepository.class).pointer().orElseThrow()));return result;
    }
    String token(String identity,String permission){return credentials.service(AUDIENCE,identity,List.of(permission));}
    InternalReadHttp.Reply get(String path,String token){return http.get(enabled.base(),path,token);}
    static void response(InternalReadHttp.Reply reply,int status,String code){assertEquals(status,reply.status());if(code!=null){assertEquals(code,reply.body().get("code"));assertFalse(reply.body().containsKey("configuration"));}}
    @Test void allInternalConfigurationReadsReturnVerifiedPinnedIdentityAndAllSections(){
        var active=get(ROOT+"/active",token("risk-assessment-service",ServicePermissions.CONFIG_ACTIVE));response(active,200,null);
        assertEquals(Long.toString(fixture.first),active.body().get("versionId"));assertEquals("0",active.body().get("pointerRowVersion"));
        for(long id:new long[]{fixture.first,fixture.second}){
            var pinned=get(ROOT+"/versions/"+id,token("approval-service",ServicePermissions.CONFIG_VERSION));response(pinned,200,null);
            assertEquals(Long.toString(id),pinned.body().get("versionId"));assertEquals(fixture.expectedHashes.get(id),pinned.body().get("contentHash"));
            assertFalse(pinned.body().containsKey("pointerRowVersion"));assertTrue(pinned.body().get("configuration") instanceof Map<?,?>);
            var config=(Map<?,?>)pinned.body().get("configuration");
            for(String section:List.of("riskRules","riskBands","approvalThresholds","approvalSlas"))assertEquals(List.of(),config.get(section));
        }
    }
    @Test void allowlistedIdentityAndTokenPermissionsAreBothRequired(){
        response(get(ROOT+"/active",token("approval-service",ServicePermissions.CONFIG_VERSION)),403,"FORBIDDEN");
        response(get(ROOT+"/versions/"+fixture.first,token("risk-assessment-service",ServicePermissions.CONFIG_ACTIVE)),403,"FORBIDDEN");
        response(get(ROOT+"/active",token("unlisted-service",ServicePermissions.CONFIG_ACTIVE)),401,"UNAUTHORIZED");
        response(get(ROOT+"/active",token("approval-service",ServicePermissions.CONFIG_ACTIVE)),401,"UNAUTHORIZED");
    }
    @Test void crossAudienceWrongKeyMissingClaimsAndExpiryCannotAuthenticate(){
        String route=ROOT+"/active";response(get(route,null),401,"UNAUTHORIZED");
        response(get(route,credentials.service("risk-assessment-service","risk-assessment-service",List.of(ServicePermissions.CONFIG_ACTIVE))),401,"UNAUTHORIZED");
        for(String variation:List.of("wrong-key","unknown-kid","issuer","expired","missing-sub","missing-exp","missing-type","missing-permissions"))
            response(get(route,credentials.service(AUDIENCE,"risk-assessment-service",List.of(ServicePermissions.CONFIG_ACTIVE),variation)),401,"UNAUTHORIZED");
    }
    @Test void ephemeralUsersAndServicesCannotCrossSecurityDomains(){
        response(get(ROOT+"/active",credentials.user()),401,"UNAUTHORIZED");
        response(get("/api/v1/configuration/active",token("risk-assessment-service",ServicePermissions.CONFIG_ACTIVE)),401,"UNAUTHORIZED");
        response(get("/api/v1/configuration/active",credentials.user()),200,null); // local test user, not a real MFA issuer token
    }
    @Test void overlappingTrustedRotationKeyWorksButExtraRoutesRemainDenied(){
        response(get(ROOT+"/active",credentials.service(AUDIENCE,"risk-assessment-service",List.of(ServicePermissions.CONFIG_ACTIVE),"next")),200,null);
        response(get(ROOT+"/versions/"+fixture.first+"/raw-evidence",token("risk-assessment-service",ServicePermissions.CONFIG_VERSION)),403,"FORBIDDEN");
    }
    @Test void disabledReceiverRejectsInternalReadsAndPreservesManagerRead(){
        disabled=EphemeralReceiver.start(BusinessConfigurationApplication.class,credentials,AUDIENCE,GRANTS,false,database());
        for(String route:List.of(ROOT+"/active",ROOT+"/versions/"+fixture.first))response(http.get(disabled.base(),route,token("risk-assessment-service",ServicePermissions.CONFIG_ACTIVE)),404,"INTERNAL_ACCESS_DISABLED");
        response(http.get(disabled.base(),"/api/v1/configuration/active",credentials.user()),200,null);
    }
    @AfterEach void unchanged(){if(before!=null)assertTrue(before.equals(state()),"Internal HTTP must not mutate pointer or fixture content");}
    @AfterAll void cleanup(){
        safe=VerificationShutdown.closeAll(disabled,enabled,http)&&safe;
        if(fixtureStarted&&!safe){fixture.close();throw new AssertionError("Configuration internal HTTP cleanup deferred; stop receivers/client and retain printed namespace");}
        try{if(fixtureStarted){fixture.cleanup();
            assertEquals(0L,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_LABEL IN (?,?)",Long.class,fixture.namespace+"_A",fixture.namespace+"_B"));
            System.out.println("Configuration test receivers/client terminated; guarded cleanup remaining parents=0");}}
        finally{fixture.close();}
    }
}
