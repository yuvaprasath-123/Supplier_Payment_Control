package com.ofss.configuration.activation;

import com.ofss.configuration.band.*;
import com.ofss.configuration.publication.ConfigurationContentHash;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.*;
import com.ofss.configuration.threshold.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ofss.configuration.activation.ConfigurationActivationServiceTest.*;

/** Models lock lifetime through real Spring transaction completion. Not Oracle contention proof. */
class ConfigurationActivationConcurrencyTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void competingFirstOrSubsequentActivationsCannotBothUseOneRevision(boolean existing) throws Exception {
        var source=mock(DataSource.class);
        when(source.getConnection()).thenAnswer(i->{var c=mock(Connection.class);when(c.getAutoCommit()).thenReturn(true);return c;});
        var state=new AtomicReference<ConfigurationActivationRepository.Pointer>(existing?new ConfigurationActivationRepository.Pointer(8,NOW,43,0):null);
        var lock=new ReentrantLock(true);
        var hashes=new ConfigurationContentHash(JsonMapper.builder().build());
        String hash=hashes.compute(parent("PUBLISHED",1),41,List.of(),List.of(),List.of(),List.of()).hash();
        var repository=new ConfigurationActivationRepository(new JdbcTemplate(source)) {
            @Override public void lockPointer(){
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                lock.lock();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){lock.unlock();}});
            }
            @Override public Optional<Pointer> pointer(){return Optional.ofNullable(state.get());}
            @Override public Metadata metadata(long id){return new Metadata(41,PUBLISHED,42,hash);}
            @Override public void save(Pointer before,long id,long actor,OffsetDateTime now){
                assertThat(lock.isHeldByCurrentThread()).isTrue();assertThat(state.compareAndSet(before,new Pointer(id,now,actor,before==null?0:before.rowVersion()+1))).isTrue();
            }
        };
        var versions=mock(DraftRiskRuleRepository.class);when(versions.findVersion(7,true)).thenReturn(Optional.of(parent("PUBLISHED",1)));when(versions.listRules(7)).thenReturn(List.of());
        var bands=mock(DraftRiskBandRepository.class);when(bands.list(7)).thenReturn(List.of());
        var thresholds=mock(DraftApprovalThresholdRepository.class);when(thresholds.list(7)).thenReturn(List.of());
        var slas=mock(DraftApprovalSlaRepository.class);when(slas.list(7)).thenReturn(List.of());
        var target=new ConfigurationActivationService(repository,versions,bands,thresholds,slas,hashes,Clock.fixed(NOW.toInstant(),ZoneOffset.UTC));
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new JdbcTransactionManager(source),new AnnotationTransactionAttributeSource()));
        var service=(ConfigurationActivationService)proxy.getProxy();var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var outcomes=new ArrayList<Future<String>>();
            for(int i=0;i<2;i++)outcomes.add(executor.submit(()->{start.await();try{service.activate(7,new ActivationRequest(existing?0L:null),43);return "ACTIVE";}catch(ActivationFailure e){return e.reason().name();}}));
            start.countDown();var results=new ArrayList<String>();for(var outcome:outcomes)results.add(outcome.get(10,TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder("ACTIVE","STALE_ACTIVE_CONFIGURATION");
            assertThat(state.get().rowVersion()).isEqualTo(existing?1:0);
        } finally {start.countDown();executor.shutdownNow();assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
}
