package com.ofss.configuration.version;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfigurationVersionTransactionTest {
    private final ConfigurationVersionRepository repository = mock(ConfigurationVersionRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TransactionStatus status = mock(TransactionStatus.class);

    private ConfigurationVersionService service() {
        when(transactions.getTransaction(any())).thenReturn(status);
        var proxy = new ProxyFactory(new ConfigurationVersionService(repository));
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(transactions);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(advice);
        return (ConfigurationVersionService) proxy.getProxy();
    }

    @Test
    void creationCommitsWritableTransactionAndReadKeepsReadOnlyTransaction() {
        when(repository.insertDraft(any(), anyInt(), anyLong(), any())).thenReturn(
                new ConfigurationVersion(1, 1, "Draft", "DRAFT", false, OffsetDateTime.now(), null));
        when(repository.findAll()).thenReturn(List.of());
        var service = service();
        service.createDraft(new CreateConfigurationVersionRequest("Draft", null, false, "SUM", null), 42);
        service.listVersions();
        var definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions, times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues().get(0).isReadOnly()).isFalse();
        assertThat(definitions.getAllValues().get(1).isReadOnly()).isTrue();
        verify(transactions, times(2)).commit(status);
        verify(transactions, never()).rollback(any());
    }

    @Test
    void allocationRaceRollsBackWithoutRetryingInsert() {
        when(repository.latestVersionNumber()).thenReturn(7L);
        when(repository.insertDraft(any(), eq(8), eq(42L), any())).thenThrow(new VersionAllocationConflictException());
        assertThatThrownBy(() -> service().createDraft(
                new CreateConfigurationVersionRequest("Draft", null, false, "SUM", null), 42))
                .isInstanceOf(VersionAllocationConflictException.class);
        verify(transactions).rollback(status);
        verify(transactions, never()).commit(any());
        verify(repository).latestVersionNumber();
        verify(repository).insertDraft(any(), eq(8), eq(42L), any());
        verifyNoMoreInteractions(repository);
    }
}
