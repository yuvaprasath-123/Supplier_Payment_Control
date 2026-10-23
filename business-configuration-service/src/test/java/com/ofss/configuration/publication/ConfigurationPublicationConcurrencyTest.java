package com.ofss.configuration.publication;

import com.ofss.configuration.band.*;
import com.ofss.configuration.readiness.*;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.*;
import com.ofss.configuration.threshold.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.ofss.configuration.publication.PublicationFixtures.*;

/** Real readiness and transaction manager with an emulated row lock; NOT Oracle locking proof. */
class ConfigurationPublicationConcurrencyTest {
    @Test void twoPublishersProduceOneUpdateAndSecondCannotReplacePublicationMetadata() throws Exception {
        var source=mock(DataSource.class); var lock=new ReentrantLock(); var state=new AtomicReference<>(parent("DRAFT",8));
        when(source.getConnection()).thenAnswer(invocation->{
            var connection=mock(Connection.class);when(connection.getAutoCommit()).thenReturn(true);
            doAnswer(i->{if(lock.isHeldByCurrentThread())lock.unlock();return null;}).when(connection).commit();
            doAnswer(i->{if(lock.isHeldByCurrentThread())lock.unlock();return null;}).when(connection).rollback();
            return connection;
        });
        var versions=mock(DraftRiskRuleRepository.class); var rules=mock(PublicationReadinessRuleRepository.class);
        var bands=mock(DraftRiskBandRepository.class);var tiers=mock(DraftApprovalThresholdRepository.class);var slas=mock(DraftApprovalSlaRepository.class);
        var validator=new PublicationReadinessValidator();var repository=mock(ConfigurationPublicationRepository.class);
        when(versions.findVersion(7,true)).thenAnswer(i->{if(!lock.tryLock(5,TimeUnit.SECONDS))throw new AssertionError("Mock row lock timed out");return Optional.of(state.get());});
        when(versions.findVersion(7,false)).thenAnswer(i->Optional.of(state.get()));
        when(rules.list(7)).thenReturn(rules());when(bands.list(7)).thenReturn(bands());when(tiers.list(7)).thenReturn(thresholds());when(slas.list(7)).thenReturn(approvedSlas());
        var hashes=new ConfigurationContentHash(JsonMapper.builder().build());var hash=hashes.computePublished(parent("DRAFT",8),42,rules(),bands(),thresholds(),approvedSlas()).hash();
        var actor=new AtomicLong();
        when(repository.metadata(7)).thenAnswer(i->new ConfigurationPublicationRepository.Metadata(42,state.get().rowVersion()==8?null:NOW,actor.get()==0?null:actor.get(),state.get().rowVersion()==8?null:hash));
        doAnswer(i->{assertThat(lock.isHeldByCurrentThread()).isTrue();actor.set(i.getArgument(2));state.set(parent("PUBLISHED",9));return null;})
            .when(repository).publish(eq(7L),eq(8L),anyLong(),eq(NOW),eq(hash));
        var target=new ConfigurationPublicationService(versions,rules,bands,tiers,slas,validator,hashes,repository,Clock.fixed(NOW.toInstant(),ZoneOffset.UTC));
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new JdbcTransactionManager(source),new AnnotationTransactionAttributeSource()));
        var service=(ConfigurationPublicationService)proxy.getProxy();var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var workers=new ArrayList<Future<Object>>();
            for(long id:List.of(99L,100L)) workers.add(executor.submit(()->{start.await();try{return service.publish(7,new PublishConfigurationRequest(8),id);}catch(AlreadyPublishedException e){return e;}}));
            start.countDown();var outcomes=new ArrayList<Object>();for(var worker:workers)outcomes.add(worker.get(10,TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(ConfigurationPublicationService.PublishedVersion.class::isInstance)).hasSize(1);
            assertThat(outcomes.stream().filter(AlreadyPublishedException.class::isInstance)).hasSize(1);
            verify(repository,times(1)).publish(eq(7L),eq(8L),anyLong(),eq(NOW),eq(hash));
            assertThat(actor.get()).isIn(99L,100L);
        } finally { start.countDown();executor.shutdownNow();assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
}
