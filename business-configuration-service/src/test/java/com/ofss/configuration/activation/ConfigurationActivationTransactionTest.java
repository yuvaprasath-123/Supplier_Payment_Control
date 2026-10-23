package com.ofss.configuration.activation;

import com.ofss.configuration.band.*;
import com.ofss.configuration.publication.ConfigurationContentHash;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.*;
import com.ofss.configuration.threshold.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ofss.configuration.activation.ConfigurationActivationServiceTest.*;

class ConfigurationActivationTransactionTest {
    final DataSource source=mock(DataSource.class);final Connection connection=mock(Connection.class);
    final Statement lock=mock(Statement.class), pointer=mock(Statement.class);
    final PreparedStatement insert=mock(PreparedStatement.class), update=mock(PreparedStatement.class), metadata=mock(PreparedStatement.class);
    final DraftRiskRuleRepository versions=mock(DraftRiskRuleRepository.class);
    final DraftRiskBandRepository bands=mock(DraftRiskBandRepository.class);
    final DraftApprovalThresholdRepository thresholds=mock(DraftApprovalThresholdRepository.class);
    final DraftApprovalSlaRepository slas=mock(DraftApprovalSlaRepository.class);
    ConfigurationActivationRepository repository;ConfigurationActivationService service;
    @BeforeEach void setup() throws Exception {
        when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(lock,pointer,pointer);
        when(connection.prepareStatement(ConfigurationActivationRepository.INSERT)).thenReturn(insert);
        when(connection.prepareStatement(ConfigurationActivationRepository.UPDATE)).thenReturn(update);
        when(connection.prepareStatement(ConfigurationActivationRepository.METADATA)).thenReturn(metadata);
        when(insert.executeUpdate()).thenReturn(1);when(update.executeUpdate()).thenReturn(1);
        var empty = rows(false); var saved = rows(true);
        when(pointer.executeQuery(ConfigurationActivationRepository.POINTER)).thenReturn(empty,saved);
        var hashes=new ConfigurationContentHash(JsonMapper.builder().build());
        String hash=hashes.compute(parent("PUBLISHED",1),41,List.of(),List.of(),List.of(),List.of()).hash();
        var meta=mock(ResultSet.class);when(meta.next()).thenReturn(true,false);
        when(meta.getObject("CREATED_BY_USER_ID")).thenReturn(BigDecimal.valueOf(41));when(meta.getObject("PUBLISHED_BY_USER_ID")).thenReturn(BigDecimal.valueOf(42));
        when(meta.getObject("PUBLISHED_AT",OffsetDateTime.class)).thenReturn(PUBLISHED);when(meta.getString("CONTENT_HASH")).thenReturn(hash);when(metadata.executeQuery()).thenReturn(meta);
        when(versions.findVersion(7,true)).thenReturn(Optional.of(parent("PUBLISHED",1)));when(versions.findVersion(7,false)).thenReturn(Optional.of(parent("PUBLISHED",1)));
        when(versions.listRules(7)).thenReturn(List.of());when(bands.list(7)).thenReturn(List.of());when(thresholds.list(7)).thenReturn(List.of());when(slas.list(7)).thenReturn(List.of());
        repository=new ConfigurationActivationRepository(new JdbcTemplate(source));
        var target=new ConfigurationActivationService(repository,versions,bands,thresholds,slas,hashes,Clock.fixed(NOW.toInstant(),ZoneOffset.UTC));
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new JdbcTransactionManager(source),new AnnotationTransactionAttributeSource()));service=(ConfigurationActivationService)factory.getProxy();
    }
    ResultSet rows(boolean exists) throws Exception {
        var rs=mock(ResultSet.class);when(rs.next()).thenReturn(exists,false);when(rs.getObject("POINTER_ID")).thenReturn(BigDecimal.ONE);
        when(rs.getObject("VERSION_ID")).thenReturn(BigDecimal.valueOf(7));when(rs.getObject("ACTIVATED_BY_USER_ID")).thenReturn(BigDecimal.valueOf(43));
        when(rs.getObject("ROW_VERSION")).thenReturn(BigDecimal.ZERO);when(rs.getObject("ACTIVATED_AT",OffsetDateTime.class)).thenReturn(NOW);return rs;
    }
    @Test void actualJdbcLockWriteBindingsAndReadbackCommitTogether() throws Exception {
        assertThat(service.activate(7,new ActivationRequest(null),43).pointerRowVersion()).isEqualTo("0");
        var order=inOrder(lock,insert,connection);order.verify(lock).execute(ConfigurationActivationRepository.LOCK);
        order.verify(insert).setLong(1,7);order.verify(insert).setObject(2,NOW);order.verify(insert).setLong(3,43);order.verify(insert).executeUpdate();order.verify(connection).commit();
        verify(connection,never()).rollback();
    }
    @Test void existingPointerUsesGuardedUpdateWithExactOldIdentityAndRevision() throws Exception {
        var before=rows(true);when(before.getObject("VERSION_ID")).thenReturn(BigDecimal.valueOf(8));
        var after=rows(true);when(after.getObject("ROW_VERSION")).thenReturn(BigDecimal.ONE);
        when(pointer.executeQuery(ConfigurationActivationRepository.POINTER)).thenReturn(before,after);
        assertThat(service.activate(7,new ActivationRequest(0L),43).pointerRowVersion()).isEqualTo("1");
        verify(update).setLong(1,7);verify(update).setObject(2,NOW);verify(update).setLong(3,43);verify(update).setLong(4,8);verify(update).setLong(5,0);
        verify(update).executeUpdate();verify(insert,never()).executeUpdate();verify(connection).commit();verify(connection,never()).rollback();
    }
    @ParameterizedTest @ValueSource(strings={"pointer-id","float-id","negative-revision","overflow-revision","missing-time","duplicate"})
    void corruptedPointerMetadataFailsClosed(String kind) throws Exception {
        var bad=rows(true);
        switch(kind){
            case "pointer-id"->when(bad.getObject("POINTER_ID")).thenReturn(BigDecimal.valueOf(2));
            case "float-id"->when(bad.getObject("VERSION_ID")).thenReturn(7.0);
            case "negative-revision"->when(bad.getObject("ROW_VERSION")).thenReturn(BigDecimal.valueOf(-1));
            case "overflow-revision"->when(bad.getObject("ROW_VERSION")).thenReturn(new BigDecimal("9223372036854775808"));
            case "missing-time"->when(bad.getObject("ACTIVATED_AT",OffsetDateTime.class)).thenReturn(null);
            case "duplicate"->when(bad.next()).thenReturn(true,true,false);
        }
        when(pointer.executeQuery(ConfigurationActivationRepository.POINTER)).thenReturn(bad);
        assertThatThrownBy(()->service.activate(7,new ActivationRequest(0L),43)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        verify(insert,never()).executeUpdate();verify(update,never()).executeUpdate();verify(connection).rollback();verify(connection,never()).commit();
    }
    @ParameterizedTest @ValueSource(strings={"zero","sql","missing-readback","bad-readback"})
    void postWriteFailuresRollBack(String stage) throws Exception {
        if(stage.equals("zero"))when(insert.executeUpdate()).thenReturn(0);
        if(stage.equals("sql"))when(insert.executeUpdate()).thenThrow(new SQLException("private SQL", "42000",1031));
        if(stage.equals("missing-readback")){var empty=rows(false);var missing=rows(false);when(pointer.executeQuery(ConfigurationActivationRepository.POINTER)).thenReturn(empty,missing);}
        if(stage.equals("bad-readback")){var empty=rows(false);var bad=rows(true);when(bad.getObject("VERSION_ID")).thenReturn(BigDecimal.valueOf(8));when(pointer.executeQuery(ConfigurationActivationRepository.POINTER)).thenReturn(empty,bad);}
        assertThatThrownBy(()->service.activate(7,new ActivationRequest(null),43)).isInstanceOf(RuntimeException.class);
        verify(insert).executeUpdate();verify(connection).rollback();verify(connection,never()).commit();
    }
    @ParameterizedTest @ValueSource(strings={"PK_CFG_CURRENT","UK_CFG_OTHER","PK_OTHER"})
    void onlyExactFirstPointerCollisionIsConflict(String constraint) throws Exception {
        when(insert.executeUpdate()).thenThrow(new SQLException("ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP""+constraint+") violated", "23000",1));
        var error=catchThrowable(()->service.activate(7,new ActivationRequest(null),43));
        if(constraint.equals("PK_CFG_CURRENT"))assertThat(error).isInstanceOf(ActivationFailure.class);else assertThat(error).isInstanceOf(org.springframework.dao.DataAccessException.class);
        verify(connection).rollback();verify(connection,never()).commit();
    }
    @Test void activeUsesSerializableReadOnlyAndNeverLocksOrWrites() throws Exception {
        var saved=rows(true);when(connection.createStatement()).thenReturn(pointer);when(pointer.executeQuery(ConfigurationActivationRepository.POINTER)).thenReturn(saved);
        service.active();verify(connection).setReadOnly(true);verify(connection).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        verify(insert,never()).executeUpdate();verify(update,never()).executeUpdate();verify(lock,never()).execute(anyString());
    }
    @Test void repositoryRejectsUnboundAccess() {
        assertThatThrownBy(repository::lockPointer).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(repository::pointer).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
