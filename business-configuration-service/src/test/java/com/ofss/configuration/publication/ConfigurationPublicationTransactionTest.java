package com.ofss.configuration.publication;

import com.ofss.configuration.band.*;
import com.ofss.configuration.readiness.*;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.*;
import com.ofss.configuration.threshold.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.ofss.configuration.publication.PublicationFixtures.*;

/** Real Spring transaction manager and publication JDBC, mocked driver/readiness dependencies; NOT Oracle proof. */
class ConfigurationPublicationTransactionTest {
    private final DataSource source=mock(DataSource.class);
    private final Connection connection=mock(Connection.class);
    private final PreparedStatement metadata=mock(PreparedStatement.class), update=mock(PreparedStatement.class);
    private final DraftRiskRuleRepository versions=mock(DraftRiskRuleRepository.class);
    private final PublicationReadinessRuleRepository rules=mock(PublicationReadinessRuleRepository.class);
    private final DraftRiskBandRepository bands=mock(DraftRiskBandRepository.class);
    private final DraftApprovalThresholdRepository thresholds=mock(DraftApprovalThresholdRepository.class);
    private final DraftApprovalSlaRepository slas=mock(DraftApprovalSlaRepository.class);
    private final PublicationReadinessValidator validator=mock(PublicationReadinessValidator.class);
    private final ConfigurationContentHash hashes=new ConfigurationContentHash(JsonMapper.builder().build());
    private ConfigurationPublicationRepository repository;
    private ConfigurationPublicationService service;
    private String hash;
    @BeforeEach void setup() throws Exception {
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(ConfigurationPublicationRepository.METADATA)).thenReturn(metadata);
        when(connection.prepareStatement(ConfigurationPublicationRepository.PUBLISH)).thenReturn(update);
        when(update.executeUpdate()).thenReturn(1);
        when(versions.findVersion(7,true)).thenReturn(Optional.of(parent("DRAFT",8)));
        when(versions.findVersion(7,false)).thenReturn(Optional.of(parent("PUBLISHED",9)));
        when(rules.list(7)).thenReturn(rules()); when(bands.list(7)).thenReturn(bands());
        when(thresholds.list(7)).thenReturn(thresholds()); when(slas.list(7)).thenReturn(approvedSlas());
        when(validator.validate(any(),anyList(),anyList(),anyList(),anyList())).thenReturn(new PublicationReadiness("7","8","DRAFT",true,true,true,List.of()));
        hash=hashes.computePublished(parent("DRAFT",8),42,rules(),bands(),thresholds(),approvedSlas()).hash();
        var before=metadataRows(false); var after=metadataRows(true); when(metadata.executeQuery()).thenReturn(before,after);
        repository=new ConfigurationPublicationRepository(new JdbcTemplate(source));
        service=proxy(new ConfigurationPublicationService(versions,rules,bands,thresholds,slas,validator,hashes,repository,Clock.fixed(NOW.toInstant().plusNanos(789),ZoneOffset.UTC)));
    }
    private ConfigurationPublicationService proxy(ConfigurationPublicationService target) {
        var factory=new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new JdbcTransactionManager(source),new AnnotationTransactionAttributeSource()));
        return (ConfigurationPublicationService)factory.getProxy();
    }
    private ResultSet metadataRows(boolean published) throws Exception {
        var rows=mock(ResultSet.class); when(rows.next()).thenReturn(true,false);
        when(rows.getObject("CREATED_BY_USER_ID")).thenReturn(BigDecimal.valueOf(42));
        if(published) {
            when(rows.getObject("PUBLISHED_BY_USER_ID")).thenReturn(BigDecimal.valueOf(99)); when(rows.getObject("PUBLISHED_AT",java.time.OffsetDateTime.class)).thenReturn(NOW);
            when(rows.getString("CONTENT_HASH")).thenReturn(hash);
        }
        return rows;
    }
    private ConfigurationPublicationService.PublishedVersion publish() { return service.publish(7,new PublishConfigurationRequest(8),99); }
    private void useConcreteReadiness() throws Exception {
        when(slas.list(7)).thenReturn(approvedSlas());
        hash=hashes.computePublished(parent("DRAFT",8),42,rules(),bands(),thresholds(),approvedSlas()).hash();
        var before=metadataRows(false);var after=metadataRows(true);
        when(metadata.executeQuery()).thenReturn(before,after);
        service=proxy(new ConfigurationPublicationService(versions,rules,bands,thresholds,slas,new PublicationReadinessValidator(),hashes,repository,Clock.fixed(NOW.toInstant(),ZoneOffset.UTC)));
    }
    @Test void concreteReadinessCommitsActualPublicationSqlAndReadback() throws Exception {
        useConcreteReadiness();var saved=publish();assertThat(saved.contentHash()).isEqualTo(hash);
        assertThat(saved.rowVersion()).isEqualTo("9");verify(update).executeUpdate();verify(connection).commit();verify(connection,never()).rollback();
    }
    @ParameterizedTest @ValueSource(strings={"update-error","missing-readback","metadata-error"})
    void concreteReadyPublicationStillRollsBackOnWriteOrReadbackFailure(String stage) throws Exception {
        useConcreteReadiness();
        switch(stage){
            case "update-error"->when(update.executeUpdate()).thenThrow(new SQLException("Synthetic failure","42000",1031));
            case "missing-readback"->when(versions.findVersion(7,false)).thenReturn(Optional.empty());
            default->{var before=metadataRows(false);when(metadata.executeQuery()).thenReturn(before).thenThrow(new SQLException("Synthetic readback failure"));}
        }
        assertThatThrownBy(this::publish).isInstanceOf(org.springframework.dao.DataAccessException.class);
        verify(update).executeUpdate();verify(connection).rollback();verify(connection,never()).commit();
    }
    @Test void lockValidationConditionalUpdateAndReadbackCommitTogetherWithoutPointerChanges() throws Exception {
        var saved=publish(); assertThat(saved.rowVersion()).isEqualTo("9"); assertThat(saved.publishedByUserId()).isEqualTo("99");
        assertThat(saved.contentHash()).isEqualTo(hash); assertThat(saved.publishedAt()).isEqualTo(NOW.toInstant().toString());
        var order=inOrder(connection,versions,rules,bands,thresholds,slas,validator,metadata,update);
        order.verify(versions).findVersion(7,true); order.verify(rules).list(7); order.verify(bands).list(7);
        order.verify(thresholds).list(7); order.verify(slas).list(7); order.verify(validator).validate(any(),anyList(),anyList(),anyList(),anyList());
        order.verify(update).setObject(1,NOW); order.verify(update).setLong(2,99); order.verify(update).setString(3,hash);
        order.verify(update).setLong(4,7); order.verify(update).setLong(5,8); order.verify(update).executeUpdate();
        order.verify(versions).findVersion(7,false); order.verify(metadata).executeQuery(); order.verify(connection).commit();
        verify(connection,never()).rollback(); verify(connection,times(2)).prepareStatement(ConfigurationPublicationRepository.METADATA);
        verify(connection).prepareStatement(ConfigurationPublicationRepository.PUBLISH);
        var sql=org.mockito.ArgumentCaptor.forClass(String.class); verify(connection,times(3)).prepareStatement(sql.capture());
        assertThat(sql.getAllValues()).noneMatch(statement->statement.contains("CURRENT_CONFIGURATION"));
    }
    @Test void realReadinessRejectsUnsupportedPolicyAndRollsBackWithoutUpdate() throws Exception {
        when(slas.list(7)).thenReturn(slas());
        service=proxy(new ConfigurationPublicationService(versions,rules,bands,thresholds,slas,new PublicationReadinessValidator(),hashes,repository));
        var failure=catchThrowableOfType(this::publish,PublicationNotReadyException.class);
        assertThat(failure.readiness().structurallyValid()).isFalse(); assertThat(failure.readiness().ready()).isFalse();
        assertThat(failure.readiness().issues()).extracting(PublicationReadiness.Issue::code).contains("INVALID_APPROVAL_POLICY_CONTRACT");
        rollbackWithoutWrite();
    }
    @ParameterizedTest @ValueSource(strings={"stale","published","missing","overflow"})
    void rejectedParentsCannotReadChildrenOrWrite(String condition) throws Exception {
        switch(condition) {
            case "stale"->when(versions.findVersion(7,true)).thenReturn(Optional.of(parent("DRAFT",9)));
            case "published"->when(versions.findVersion(7,true)).thenReturn(Optional.of(parent("PUBLISHED",9)));
            case "missing"->when(versions.findVersion(7,true)).thenReturn(Optional.empty());
            case "overflow"->when(versions.findVersion(7,true)).thenReturn(Optional.of(parent("DRAFT",Long.MAX_VALUE)));
        }
        long expected=condition.equals("overflow")?Long.MAX_VALUE:8;
        assertThatThrownBy(()->service.publish(7,new PublishConfigurationRequest(expected),99)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(rules,bands,thresholds,slas,validator); rollbackWithoutWrite();
    }
    @ParameterizedTest @ValueSource(strings={"update-zero","update-error","missing-readback","wrong-state","wrong-revision","wrong-publisher","metadata-error"})
    void failureAfterOrDuringUpdateNeverCommitsOrReturnsSuccess(String stage) throws Exception {
        switch(stage) {
            case "update-zero"->when(update.executeUpdate()).thenReturn(0);
            case "update-error"->when(update.executeUpdate()).thenThrow(new SQLException("private detail","42000",1031));
            case "missing-readback"->when(versions.findVersion(7,false)).thenReturn(Optional.empty());
            case "wrong-state"->when(versions.findVersion(7,false)).thenReturn(Optional.of(parent("DRAFT",9)));
            case "wrong-revision"->when(versions.findVersion(7,false)).thenReturn(Optional.of(parent("PUBLISHED",8)));
            case "wrong-publisher"->{var before=metadataRows(false);var after=metadataRows(true);when(after.getObject("PUBLISHED_BY_USER_ID")).thenReturn(BigDecimal.ONE);when(metadata.executeQuery()).thenReturn(before,after);}
            case "metadata-error"->{var before=metadataRows(false);when(metadata.executeQuery()).thenReturn(before).thenThrow(new SQLException("private readback detail"));}
        }
        assertThatThrownBy(this::publish).isInstanceOf(org.springframework.dao.DataAccessException.class);
        verify(update).executeUpdate(); verify(connection).rollback(); verify(connection,never()).commit();
    }
    @Test void repositoryCannotWriteOutsideTheBoundTransaction() {
        assertThatThrownBy(()->repository.publish(7,8,99,NOW,hash)).isInstanceOf(org.springframework.dao.DataRetrievalFailureException.class);
        verifyNoInteractions(source);
    }
    @Test void inconsistentReadinessCannotBypassGate() throws Exception {
        when(validator.validate(any(),anyList(),anyList(),anyList(),anyList())).thenReturn(new PublicationReadiness("7","8","DRAFT",true,false,true,List.of()));
        assertThatThrownBy(this::publish).isInstanceOf(org.springframework.dao.DataRetrievalFailureException.class); rollbackWithoutWrite();
    }
    private void rollbackWithoutWrite() throws Exception { verify(update,never()).executeUpdate();verify(connection).rollback();verify(connection,never()).commit(); }
}
