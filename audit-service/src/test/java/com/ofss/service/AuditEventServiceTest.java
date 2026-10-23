package com.ofss.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.ofss.dto.CreateAuditEventRequest;
import com.ofss.entity.AuditEvent;
import com.ofss.repository.AuditEventRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {
    @Mock AuditEventRepository repository;
    @Test void recordsAnImmutableEventPayload() {
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> { AuditEvent e = invocation.getArgument(0); e.setId(11L); return e; });
        var response = new AuditEventService(repository).record(new CreateAuditEventRequest("PAYMENT_APPROVED", "PAYMENT_REQUEST", "42", "7", "corr-1", "{\"status\":\"PENDING\"}", "{\"status\":\"APPROVED\"}", "{}"));
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        assertEquals(11L, response.id()); assertEquals("PAYMENT_REQUEST", captor.getValue().getResourceType());
        assertEquals("42", captor.getValue().getResourceId()); assertEquals("corr-1", response.correlationId());
    }
    @Test void requiresAQueryScope() { assertThrows(IllegalArgumentException.class, () -> new AuditEventService(repository).find(null, null, null)); }
}
