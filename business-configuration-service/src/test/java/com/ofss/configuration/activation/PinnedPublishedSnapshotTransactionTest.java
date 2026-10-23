package com.ofss.configuration.activation;

import java.sql.Connection;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Existing verifier under a real Spring JDBC transaction manager; all JDBC/config reads mocked. */
class PinnedPublishedSnapshotTransactionTest {
    @ParameterizedTest @ValueSource(strings={"success","rules","bands","thresholds","slas"})
    void pinnedReadsShareOneReadOnlySerializableTransactionAndNeverUsePointer(String stage) throws Exception {
        var f=new ConfigurationActivationServiceTest();f.setup();var source=mock(DataSource.class);var connection=mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(f.versions.findVersion(7,false)).thenAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();assertThat(TransactionSynchronizationManager.hasResource(source)).isTrue();return Optional.of(f.parent);});
        var failure=new org.springframework.dao.DataAccessResourceFailureException("private fixture failure");
        switch(stage){case "rules"->when(f.versions.listRules(7)).thenThrow(failure);case "bands"->when(f.bands.list(7)).thenThrow(failure);
            case "thresholds"->when(f.thresholds.list(7)).thenThrow(failure);case "slas"->when(f.slas.list(7)).thenThrow(failure);}
        var proxy=new ProxyFactory(f.service);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new JdbcTransactionManager(source),new AnnotationTransactionAttributeSource()));
        var service=(ConfigurationActivationService)proxy.getProxy();
        if(stage.equals("success")){assertThat(service.published(7).contentHash()).isEqualTo(f.hash);verify(connection).commit();verify(connection,never()).rollback();}
        else {assertThatThrownBy(()->service.published(7)).isSameAs(failure);verify(connection).rollback();verify(connection,never()).commit();}
        verify(connection).setReadOnly(true);verify(connection).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        verify(f.repository).metadata(7);verifyNoMoreInteractions(f.repository);verify(source,times(1)).getConnection();
    }
}
