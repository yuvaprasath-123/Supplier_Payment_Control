package com.ofss.configuration.activation;

import com.ofss.configuration.band.DraftRiskBandRepository;
import com.ofss.configuration.publication.*;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.DraftApprovalSlaRepository;
import com.ofss.configuration.threshold.DraftApprovalThresholdRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Opt-in mechanics fixtures deliberately set publication metadata without asserting readiness.
 * Requires NO active pointer and exclusive use of configuration activation while running.
 * No production readiness bypass, DDL, server, sequence reset, or existing-pointer restoration. */
@EnabledIfEnvironmentVariable(named="CONFIG_VERIFY_ORACLE_ACTIVATION_WRITES",matches="true")
@EnabledIfEnvironmentVariable(named="CONFIG_ORACLE_ACTIVATION_ISOLATED",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class OracleConfigurationActivationTest {
    @Configuration @EnableTransactionManagement static class Transactions {}
    static final long ACTOR=9223372036854775701L;
    static final String DESCRIPTION="Controlled activation mechanics fixture; not readiness-approved";
    DataSource source; JdbcTemplate jdbc; TransactionTemplate tx; AnnotationConfigApplicationContext context;
    ConfigurationActivationService service; ConfigurationContentHash hashes; DraftRiskRuleRepository versions;
    String namespace; long first,second; boolean workersStopped=true;
    final Map<Long,String> expectedHashes=new HashMap<>();
    final Map<Long,ConfigurationVersionDetails> expectedParents=new HashMap<>();
    @BeforeAll void connect() {
        String password=System.getenv("CONFIG_DB_PASSWORD");
        if(password==null||password.isBlank())throw new AssertionError("Supply CONFIG_DB_PASSWORD externally");
        source=new DriverManagerDataSource("jdbc:oracle:thin:@//localhost:1521/FREEPDB1","D2_CONFIGURATION",password);
        jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(15);tx=new TransactionTemplate(new JdbcTransactionManager(source));tx.setTimeout(15);
        try {
            var identity=jdbc.queryForMap("SELECT SYS_CONTEXT('USERENV','CON_NAME') AS PDB, SYS_CONTEXT('USERENV','SESSION_USER') AS OWNER_NAME FROM DUAL");
            guard("FREEPDB1".equals(identity.get("PDB"))&&"D2_CONFIGURATION".equals(identity.get("OWNER_NAME")));
        } catch(RuntimeException failure){throw new AssertionError("Activation Oracle connection guard failed; no fixtures started");}
        context=new AnnotationConfigApplicationContext();context.register(Transactions.class);
        context.registerBean(DataSource.class,()->source);context.registerBean(JdbcTemplate.class,()->jdbc);
        context.registerBean(JdbcTransactionManager.class,()->new JdbcTransactionManager(source));context.registerBean(JsonMapper.class,()->JsonMapper.builder().build());
        context.register(DraftRiskRuleRepository.class,DraftRiskBandRepository.class,DraftApprovalThresholdRepository.class,DraftApprovalSlaRepository.class,
                ConfigurationContentHash.class,ConfigurationPublicationRepository.class,ConfigurationActivationRepository.class,ConfigurationActivationService.class);
        context.refresh();service=context.getBean(ConfigurationActivationService.class);hashes=context.getBean(ConfigurationContentHash.class);versions=context.getBean(DraftRiskRuleRepository.class);
    }
    @BeforeEach void fixtures() {
        namespace="D2_CFG_ACTIVE_"+UUID.randomUUID();first=0;second=0;expectedHashes.clear();expectedParents.clear();workersStopped=true;
        System.out.println("Activation mechanics fixture namespace (retain for recovery): "+namespace);
        tx.executeWithoutResult(status->{
            jdbc.execute(ConfigurationActivationRepository.LOCK);
            guard(jdbc.queryForObject("SELECT COUNT(*) FROM "SUPPLIER_PAYMENT_APP"CURRENT_CONFIGURATION",Long.class)==0);
            first=create("A");second=create("B");
        });
    }
    long create(String suffix) {
        String label=namespace+"_"+suffix;
        jdbc.update("INSERT INTO "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS (VERSION_NUMBER,VERSION_LABEL,PUBLICATION_STATE,DESCRIPTION,IS_DEMONSTRATION,SCORE_POLICY_CODE,MAX_FINAL_SCORE,CREATED_AT,CREATED_BY_USER_ID,ROW_VERSION) SELECT NVL(MAX(VERSION_NUMBER),0)+1,?,'DRAFT',?,1,'CAP_AT_MAX',100,?,?,0 FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS",
                label,DESCRIPTION,OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).withNano(0),ACTOR);
        long id=jdbc.queryForObject("SELECT VERSION_ID FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_LABEL=?",Long.class,label);
        var parent=versions.findVersion(id,true).orElseThrow();
        String hash=hashes.compute(parent,ACTOR,List.of(),List.of(),List.of(),List.of()).hash();
        context.getBean(ConfigurationPublicationRepository.class).publish(id,0,ACTOR,OffsetDateTime.now(ZoneOffset.UTC).minusHours(1).withNano(0),hash);
        expectedHashes.put(id,hash);expectedParents.put(id,versions.findVersion(id,false).orElseThrow());return id;
    }
    @Test void firstActivationAndAllSnapshotSectionsReadBack() {
        var active=service.activate(first,new ActivationRequest(null),ACTOR);
        assertThat(active.pointerRowVersion()).isEqualTo("0");assertThat(active.versionRowVersion()).isEqualTo("1");
        assertThat(active.configuration()).contains("\"riskRules\":[]","\"riskBands\":[]","\"approvalThresholds\":[]","\"approvalSlas\":[]");
        assertThat(service.active()).isEqualTo(active);
    }
    @Test void switchingAndRepeatingPreservePublishedVersionsAndMetadata() {
        var before=service.activate(first,new ActivationRequest(null),ACTOR);
        assertThat(service.activate(first,new ActivationRequest(0L),ACTOR-1)).isEqualTo(before);
        var after=service.activate(second,new ActivationRequest(0L),ACTOR);
        assertThat(after.pointerRowVersion()).isEqualTo("1");assertThat(after.versionId()).isEqualTo(Long.toString(second));
        assertThatThrownBy(()->service.activate(first,new ActivationRequest(0L),ACTOR)).isInstanceOf(ActivationFailure.class);
        assertThat(service.active()).isEqualTo(after);
    }
    @Test void noActiveAndDraftRejectionDoNotCreatePointer() {
        assertThatThrownBy(service::active).isInstanceOf(ActivationFailure.class);
        tx.executeWithoutResult(s->jdbc.update("UPDATE "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS SET PUBLICATION_STATE='DRAFT',PUBLISHED_AT=NULL,PUBLISHED_BY_USER_ID=NULL,CONTENT_HASH=NULL WHERE VERSION_ID=?",first));
        try {assertThatThrownBy(()->service.activate(first,new ActivationRequest(null),ACTOR)).isInstanceOf(ActivationFailure.class);}
        finally {restorePublication(first);}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "SUPPLIER_PAYMENT_APP"CURRENT_CONFIGURATION",Long.class)).isZero();
    }
    @Test void corruptHashFailsBeforePointerWrite() {
        tx.executeWithoutResult(s->jdbc.update("UPDATE "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS SET CONTENT_HASH=? WHERE VERSION_ID=?","f".repeat(64),first));
        try {assertThatThrownBy(()->service.activate(first,new ActivationRequest(null),ACTOR)).isInstanceOf(org.springframework.dao.DataAccessException.class);}
        finally {restorePublication(first);}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "SUPPLIER_PAYMENT_APP"CURRENT_CONFIGURATION",Long.class)).isZero();
    }
    void restorePublication(long id) {
        tx.executeWithoutResult(s->jdbc.update("UPDATE "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS SET PUBLICATION_STATE='PUBLISHED',PUBLISHED_AT=?,PUBLISHED_BY_USER_ID=?,CONTENT_HASH=? WHERE VERSION_ID=?",
                expectedParents.get(id).version().publishedAt(),ACTOR,expectedHashes.get(id),id));
    }
    @Test void actualWriteRollsBackOnInjectedReadbackFailure() {
        var repository=new ConfigurationActivationRepository(jdbc) {
            boolean wrote;
            @Override public void save(Pointer before,long id,long actor,OffsetDateTime now){super.save(before,id,actor,now);wrote=true;}
            @Override public Optional<Pointer> pointer(){if(wrote)throw new InjectedReadbackFailure();return super.pointer();}
        };
        var target=new ConfigurationActivationService(repository,versions,context.getBean(DraftRiskBandRepository.class),
                context.getBean(DraftApprovalThresholdRepository.class),context.getBean(DraftApprovalSlaRepository.class),hashes);
        assertThatThrownBy(()->tx.execute(s->target.activate(first,new ActivationRequest(null),ACTOR))).isInstanceOf(InjectedReadbackFailure.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "SUPPLIER_PAYMENT_APP"CURRENT_CONFIGURATION",Long.class)).isZero();
    }
    static class InjectedReadbackFailure extends RuntimeException {}
    @Test void concurrentFirstActivationsProduceOneWinnerAndOneStaleRevision() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);workersStopped=false;
        try {
            List<Future<String>> futures=new ArrayList<>();
            for(long id:new long[]{first,second})futures.add(executor.submit(()->{
                ready.countDown();start.await();
                try {service.activate(id,new ActivationRequest(null),ACTOR);return "ACTIVE";}
                catch(ActivationFailure failure){return failure.reason().name();}
            }));
            guard(ready.await(5,TimeUnit.SECONDS));start.countDown();
            var outcomes=new ArrayList<String>();for(var future:futures)outcomes.add(future.get(25,TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("ACTIVE","STALE_ACTIVE_CONFIGURATION");
            assertThat(service.active().pointerRowVersion()).isEqualTo("0");
        } finally {
            start.countDown();executor.shutdown();workersStopped=executor.awaitTermination(30,TimeUnit.SECONDS);
            if(!workersStopped){executor.shutdownNow();workersStopped=executor.awaitTermination(15,TimeUnit.SECONDS);}
            System.out.println("Activation workers terminated="+workersStopped);
            guard(workersStopped);
        }
    }
    @AfterEach void cleanup() {
        if(namespace==null)return;
        guard(workersStopped);
        try {
            tx.executeWithoutResult(status->{
                jdbc.execute(ConfigurationActivationRepository.LOCK);
                var rows=jdbc.queryForList("SELECT VERSION_ID,VERSION_LABEL,DESCRIPTION,PUBLICATION_STATE,IS_DEMONSTRATION,CREATED_BY_USER_ID,PUBLISHED_BY_USER_ID,CONTENT_HASH,ROW_VERSION FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_LABEL IN (?,?) FOR UPDATE",namespace+"_A",namespace+"_B");
                guard(rows.size()==expectedHashes.size());
                for(var row:rows){
                    long id=ConfigurationActivationRepository.number(row.get("VERSION_ID"),false);
                    guard(expectedHashes.containsKey(id)&&DESCRIPTION.equals(row.get("DESCRIPTION"))&&"PUBLISHED".equals(row.get("PUBLICATION_STATE"))
                            &&ConfigurationActivationRepository.number(row.get("IS_DEMONSTRATION"),false)==1
                            &&ConfigurationActivationRepository.number(row.get("CREATED_BY_USER_ID"),false)==ACTOR
                            &&ConfigurationActivationRepository.number(row.get("PUBLISHED_BY_USER_ID"),false)==ACTOR
                            &&ConfigurationActivationRepository.number(row.get("ROW_VERSION"),false)==1&&expectedHashes.get(id).equals(row.get("CONTENT_HASH")));
                    var stored=versions.findVersion(id,false).orElseThrow();
                    guard(stored.equals(expectedParents.get(id))&&hashes.compute(stored,ACTOR,List.of(),List.of(),List.of(),List.of()).hash().equals(expectedHashes.get(id)));
                    for(String table:List.of("RISK_RULE_CONFIGS","RISK_BAND_CONFIGS","APPROVAL_THRESHOLD_CONFIGS","SLA_CONFIGS"))
                        guard(jdbc.queryForObject("SELECT COUNT(*) FROM "SUPPLIER_PAYMENT_APP""+table+" WHERE VERSION_ID=?",Long.class,id)==0);
                }
                var pointers=jdbc.queryForList(ConfigurationActivationRepository.POINTER);guard(pointers.size()<=1);
                for(var pointer:pointers){
                    long id=ConfigurationActivationRepository.number(pointer.get("VERSION_ID"),false);
                    guard(expectedHashes.containsKey(id)&&ConfigurationActivationRepository.number(pointer.get("POINTER_ID"),false)==1
                            &&ConfigurationActivationRepository.number(pointer.get("ACTIVATED_BY_USER_ID"),false)==ACTOR);
                    guard(jdbc.update("DELETE FROM "SUPPLIER_PAYMENT_APP"CURRENT_CONFIGURATION WHERE POINTER_ID=1 AND VERSION_ID=? AND ACTIVATED_BY_USER_ID=? AND ROW_VERSION=?",
                            id,ACTOR,pointer.get("ROW_VERSION"))==1);
                }
                for(var row:rows)guard(jdbc.update("DELETE FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_ID=? AND VERSION_LABEL=? AND CONTENT_HASH=?",
                        row.get("VERSION_ID"),row.get("VERSION_LABEL"),row.get("CONTENT_HASH"))==1);
            });
            System.out.println("Activation fixture cleanup completed; remaining fixture parents=0; namespace: "+namespace);
        } catch(RuntimeException e){throw new AssertionError("Cleanup refused or failed; retain exact namespace; never use broad deletion");}
    }
    static void guard(boolean valid){if(!valid)throw new AssertionError("Activation fixture guard refused unexpected state; no existing pointer may be replaced");}
    @AfterAll void close(){if(context!=null)context.close();}
}
