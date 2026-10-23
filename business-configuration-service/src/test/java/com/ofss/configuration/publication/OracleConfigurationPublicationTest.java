package com.ofss.configuration.publication;

import com.ofss.configuration.band.DraftRiskBandRepository;
import com.ofss.configuration.readiness.*;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.DraftApprovalSlaRepository;
import com.ofss.configuration.threshold.DraftApprovalThresholdRepository;
import com.ofss.configuration.version.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Opt-in COMMITTED fixture writes, real Oracle/Spring path and REAL readiness.
 * No application/server starts. Successful publication remains impossible while policy blockers persist. */
@EnabledIfEnvironmentVariable(named="CONFIG_VERIFY_ORACLE_PUBLICATION_WRITES",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class OracleConfigurationPublicationTest {
    @TestConfiguration @EnableTransactionManagement static class Transactions {}
    private AnnotationConfigApplicationContext context;
    private DataSource source;
    private JdbcTemplate jdbc;
    private ConfigurationPublicationService service;
    private String namespace;
    private long id;
    private Map<String,Object> baseline;
    private boolean workersStopped=true;
    @BeforeAll void connect() {
        source=OraclePublicationFixtures.externalSource();jdbc=new JdbcTemplate(source);
        context=new AnnotationConfigApplicationContext();context.register(Transactions.class);
        context.registerBean(DataSource.class,()->source);context.registerBean(JdbcTemplate.class,()->jdbc);
        context.registerBean(JdbcTransactionManager.class,()->new JdbcTransactionManager(source));
        context.registerBean(JsonMapper.class,()->JsonMapper.builder().build());
        context.register(ConfigurationVersionRepository.class,ConfigurationVersionService.class,DraftRiskRuleRepository.class,
            PublicationReadinessRuleRepository.class,DraftRiskBandRepository.class,DraftApprovalThresholdRepository.class,DraftApprovalSlaRepository.class,
            PublicationReadinessValidator.class,ConfigurationContentHash.class,ConfigurationPublicationRepository.class,ConfigurationPublicationService.class);
        context.refresh();service=context.getBean(ConfigurationPublicationService.class);
    }
    @BeforeEach void fixture() {
        namespace=OraclePublicationFixtures.PREFIX+UUID.randomUUID();id=0;
        System.out.println("Publication fixture namespace: "+namespace);
        var request=new CreateConfigurationVersionRequest(namespace,OraclePublicationFixtures.DESCRIPTION,true,"CAP_AT_MAX",100);
        for(int attempt=0;attempt<3;attempt++) {
            try {id=Long.parseLong(context.getBean(ConfigurationVersionService.class).createDraft(request,OraclePublicationFixtures.ACTOR).versionId());break;}
            catch(VersionAllocationConflictException collision) {if(attempt==2)throw new AssertionError("Synthetic version allocation conflict; retry this suite later");}
            catch(RuntimeException failure) {throw new AssertionError("Oracle publication fixture creation failed; details withheld");}
        }
        baseline=state();
    }
    private Map<String,Object> state() {
        return jdbc.queryForObject("SELECT VERSION_ID,VERSION_NUMBER,VERSION_LABEL,DESCRIPTION,IS_DEMONSTRATION,SCORE_POLICY_CODE,MAX_FINAL_SCORE,CREATED_BY_USER_ID,CREATED_AT,PUBLICATION_STATE,ROW_VERSION,PUBLISHED_AT,PUBLISHED_BY_USER_ID,CONTENT_HASH FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_ID=? AND VERSION_LABEL=?",(rs,n)-> {
            var result=new LinkedHashMap<String,Object>();
            for(String name:List.of("VERSION_ID","VERSION_NUMBER","VERSION_LABEL","DESCRIPTION","IS_DEMONSTRATION","SCORE_POLICY_CODE","MAX_FINAL_SCORE","CREATED_BY_USER_ID","PUBLICATION_STATE","ROW_VERSION","PUBLISHED_BY_USER_ID","CONTENT_HASH"))result.put(name,rs.getObject(name));
            result.put("CREATED_AT",rs.getObject("CREATED_AT",OffsetDateTime.class));result.put("PUBLISHED_AT",rs.getObject("PUBLISHED_AT",OffsetDateTime.class));return result;
        },id,namespace);
    }
    @Test void actualPolicyBlockersAndMissingSectionsRejectWithoutPublicationOrActivation() {
        var exception=catchThrowableOfType(()->service.publish(id,new PublishConfigurationRequest(0),OraclePublicationFixtures.ACTOR),PublicationNotReadyException.class);
        assertThat(exception.readiness().ready()).isFalse();assertThat(exception.readiness().structurallyValid()).isFalse();
        assertThat(exception.readiness().issues()).extracting(PublicationReadiness.Issue::code).contains("SECTION_MISSING","REQUIRED_SLA_MISSING");
    }
    @Test void staleExpectedRevisionRollsBackAndPreservesAllMetadata() {
        var exception=catchThrowableOfType(()->service.publish(id,new PublishConfigurationRequest(1),OraclePublicationFixtures.ACTOR),DraftRuleConflictException.class);
        assertThat(exception.reason()).isEqualTo(DraftRuleConflictException.Reason.STALE_VERSION);
    }
    @Test void repeatedBlockedPublicationDoesNotAdvanceRevisionOrCreateHash() {
        for(int attempt=0;attempt<2;attempt++)assertThatThrownBy(()->service.publish(id,new PublishConfigurationRequest(0),OraclePublicationFixtures.ACTOR)).isInstanceOf(PublicationNotReadyException.class);
    }
    @Test void twoBlockedPublishersReleaseParentLockAndPreserveDraft() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);workersStopped=false;
        try {
            var workers=new ArrayList<Future<Boolean>>();
            for(int worker=0;worker<2;worker++)workers.add(executor.submit(()->{start.await();try{service.publish(id,new PublishConfigurationRequest(0),OraclePublicationFixtures.ACTOR);return false;}catch(PublicationNotReadyException expected){return true;}catch(RuntimeException failure){throw new AssertionError("Oracle concurrent publication failed; details withheld");}}));
            start.countDown();for(var worker:workers)assertThat(worker.get(20,TimeUnit.SECONDS)).isTrue();
        } finally {
            start.countDown();executor.shutdownNow();workersStopped=executor.awaitTermination(20,TimeUnit.SECONDS);
            System.out.println("Publication workers terminated: "+workersStopped);assertThat(workersStopped).isTrue();
        }
    }
    @AfterEach void cleanup() {
        if(namespace==null)return;
        if(!workersStopped)throw new AssertionError("Cleanup deferred: confirm workers stopped before exact-namespace recovery");
        try {
            if(id!=0&&baseline!=null)assertThat(state()).as("Fixture data must remain unchanged").isEqualTo(baseline);
            OraclePublicationFixtures.clean(source,namespace);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_LABEL=?",Long.class,namespace)).isZero();
            System.out.println("Publication fixture cleanup remaining parents: 0");
        }catch(RuntimeException failure){throw new AssertionError("Guarded publication cleanup failed; retain exact namespace for recovery; details withheld");}
        finally{namespace=null;baseline=null;}
    }
    @AfterAll void close(){if(context!=null)context.close();}
}
