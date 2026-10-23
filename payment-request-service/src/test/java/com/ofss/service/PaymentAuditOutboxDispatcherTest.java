package com.ofss.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentAuditOutboxDispatcherTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PaymentAuditOutboxPublisher publisher;

    @Test
    void dispatchPendingEventsShouldDoNothingWhenDisabled() {

        PaymentAuditOutboxDispatcher dispatcher =
                new PaymentAuditOutboxDispatcher(
                        jdbcTemplate,
                        publisher,
                        false
                );

        dispatcher.dispatchPendingEvents();

        verifyNoInteractions(
                jdbcTemplate,
                publisher
        );
    }

    @Test
    void dispatchPendingEventsShouldDoNothingWhenNoEventsArePending() {

        when(
                jdbcTemplate.queryForList(
                        anyString(),
                        eq(Long.class)
                )
        ).thenReturn(
                List.of()
        );

        PaymentAuditOutboxDispatcher dispatcher =
                new PaymentAuditOutboxDispatcher(
                        jdbcTemplate,
                        publisher,
                        true
                );

        dispatcher.dispatchPendingEvents();

        verify(
                jdbcTemplate
        ).queryForList(
                anyString(),
                eq(Long.class)
        );

        verifyNoInteractions(
                publisher
        );
    }

    @Test
    void dispatchPendingEventsShouldPublishAllPendingEvents() {

        when(
                jdbcTemplate.queryForList(
                        anyString(),
                        eq(Long.class)
                )
        ).thenReturn(
                List.of(
                        101L,
                        102L,
                        103L
                )
        );

        PaymentAuditOutboxDispatcher dispatcher =
                new PaymentAuditOutboxDispatcher(
                        jdbcTemplate,
                        publisher,
                        true
                );

        dispatcher.dispatchPendingEvents();

        InOrder inOrder =
                inOrder(publisher);

        inOrder.verify(
                publisher
        ).publishEvent(101L);

        inOrder.verify(
                publisher
        ).publishEvent(102L);

        inOrder.verify(
                publisher
        ).publishEvent(103L);
    }

    @Test
    void dispatchPendingEventsShouldContinueAfterPublisherFailure() {

        when(
                jdbcTemplate.queryForList(
                        anyString(),
                        eq(Long.class)
                )
        ).thenReturn(
                List.of(
                        201L,
                        202L,
                        203L
                )
        );

        doThrow(
                new RuntimeException(
                        "Kafka temporarily unavailable"
                )
        ).when(
                publisher
        ).publishEvent(202L);

        PaymentAuditOutboxDispatcher dispatcher =
                new PaymentAuditOutboxDispatcher(
                        jdbcTemplate,
                        publisher,
                        true
                );

        dispatcher.dispatchPendingEvents();

        InOrder inOrder =
                inOrder(publisher);

        inOrder.verify(
                publisher
        ).publishEvent(201L);

        inOrder.verify(
                publisher
        ).publishEvent(202L);

        inOrder.verify(
                publisher
        ).publishEvent(203L);
    }

    @Test
    void dispatchPendingEventsShouldNotPublishUnknownEventIds() {

        when(
                jdbcTemplate.queryForList(
                        anyString(),
                        eq(Long.class)
                )
        ).thenReturn(
                List.of(
                        301L
                )
        );

        PaymentAuditOutboxDispatcher dispatcher =
                new PaymentAuditOutboxDispatcher(
                        jdbcTemplate,
                        publisher,
                        true
                );

        dispatcher.dispatchPendingEvents();

        verify(
                publisher
        ).publishEvent(301L);

        verify(
                publisher,
                never()
        ).publishEvent(999L);
    }
}