package com.ofss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentAuditOutboxPublisherTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JsonMapper objectMapper;

    private PaymentAuditOutboxPublisher publisher;

    @BeforeEach
    void setUp() {

        objectMapper =
                JsonMapper.builder()
                        .build();

        publisher =
                new PaymentAuditOutboxPublisher(
                        jdbcTemplate,
                        objectMapper,
                        "http://localhost:8088",
                        "test-writer-token"
                );
    }

    @Test
    void publishEventShouldRejectNullEventId() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> publisher.publishEvent(null)
                );

        assertEquals(
                "Valid audit outbox event ID is required",
                exception.getMessage()
        );

        verifyNoInteractions(
                jdbcTemplate
        );
    }

    @Test
    void publishEventShouldRejectNonPositiveEventId() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> publisher.publishEvent(0L)
                );

        assertEquals(
                "Valid audit outbox event ID is required",
                exception.getMessage()
        );

        verifyNoInteractions(
                jdbcTemplate
        );
    }

    @Test
    void publishEventShouldFailWhenOutboxEventDoesNotExist() {

        Long eventId = 100L;

        when(
                jdbcTemplate.query(
                        anyString(),
                        any(RowMapper.class),
                        eq(eventId)
                )
        ).thenReturn(
                List.of()
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                "Audit outbox event not found: " + eventId,
                exception.getMessage()
        );

        verify(
                jdbcTemplate,
                never()
        ).update(
                anyString(),
                any(Object[].class)
        );
    }

    @Test
    void publishEventShouldReturnWithoutRepublishingAlreadyPublishedEvent()
            throws Exception {

        Long eventId = 101L;

        mockOutboxRow(
                eventId,
                501L,
                "PAYMENT_REJECTED",
                """
                {
                  "eventType":"PAYMENT_REJECTED",
                  "resourceType":"PAYMENT",
                  "resourceId":"501"
                }
                """,
                "PUBLISHED"
        );

        publisher.publishEvent(eventId);

        verify(
                jdbcTemplate,
                never()
        ).update(
                anyString(),
                any(Object[].class)
        );
    }

    @Test
    void publishEventShouldRejectEventWithUnexpectedStatus()
            throws Exception {

        Long eventId = 102L;

        mockOutboxRow(
                eventId,
                502L,
                "PAYMENT_REJECTED",
                """
                {
                  "eventType":"PAYMENT_REJECTED",
                  "resourceType":"PAYMENT",
                  "resourceId":"502"
                }
                """,
                "FAILED"
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                "Audit event is not pending: " + eventId,
                exception.getMessage()
        );

        verify(
                jdbcTemplate,
                never()
        ).update(
                anyString(),
                any(Object[].class)
        );
    }

    @Test
    void publishEventShouldRejectBlankPayload()
            throws Exception {

        Long eventId = 103L;

        mockOutboxRow(
                eventId,
                503L,
                "PAYMENT_REJECTED",
                "   ",
                "PENDING"
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                "Audit payload is empty: " + eventId,
                exception.getMessage()
        );

        verify(
                jdbcTemplate,
                never()
        ).update(
                anyString(),
                any(Object[].class)
        );
    }

    @Test
    void publishEventShouldRejectPayloadThatIsNotJsonObject()
            throws Exception {

        Long eventId = 104L;

        mockOutboxRow(
                eventId,
                504L,
                "PAYMENT_REJECTED",
                """
                [
                  "PAYMENT_REJECTED"
                ]
                """,
                "PENDING"
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                "Audit payload must be a JSON object",
                exception.getMessage()
        );

        verify(
                jdbcTemplate,
                never()
        ).update(
                anyString(),
                any(Object[].class)
        );
    }

    @Test
    void publishEventShouldRejectEventTypeMismatch()
            throws Exception {

        Long eventId = 105L;

        mockOutboxRow(
                eventId,
                505L,
                "PAYMENT_REJECTED",
                """
                {
                  "eventType":"PAYMENT_COMPLETED",
                  "resourceType":"PAYMENT",
                  "resourceId":"505"
                }
                """,
                "PENDING"
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                "Audit event type does not match Oracle: "
                        + eventId,
                exception.getMessage()
        );

        verify(
                jdbcTemplate,
                never()
        ).update(
                anyString(),
                any(Object[].class)
        );
    }

    @Test
    void publishEventShouldRejectUnexpectedResourceType()
            throws Exception {

        Long eventId = 106L;

        mockOutboxRow(
                eventId,
                506L,
                "PAYMENT_REJECTED",
                """
                {
                  "eventType":"PAYMENT_REJECTED",
                  "resourceType":"INVOICE",
                  "resourceId":"506"
                }
                """,
                "PENDING"
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                "Unexpected audit resource type: "
                        + eventId,
                exception.getMessage()
        );

        verify(
                jdbcTemplate,
                never()
        ).update(
                anyString(),
                any(Object[].class)
        );
    }

    @Test
    void publishEventShouldRejectResourceIdMismatch()
            throws Exception {

        Long eventId = 107L;

        mockOutboxRow(
                eventId,
                507L,
                "PAYMENT_REJECTED",
                """
                {
                  "eventType":"PAYMENT_REJECTED",
                  "resourceType":"PAYMENT",
                  "resourceId":"999"
                }
                """,
                "PENDING"
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> publisher.publishEvent(eventId)
                );

        assertEquals(
                "Audit resource ID does not match payment ID: "
                        + eventId,
                exception.getMessage()
        );

        verify(
                jdbcTemplate,
                never()
        ).update(
                anyString(),
                any(Object[].class)
        );
    }

    @SuppressWarnings({
            "rawtypes",
            "unchecked"
    })
    private void mockOutboxRow(
            Long eventId,
            Long paymentId,
            String eventType,
            String payload,
            String status
    ) throws Exception {

        doAnswer(
                invocation -> {

                    RowMapper rowMapper =
                            invocation.getArgument(1);

                    ResultSet resultSet =
                            org.mockito.Mockito.mock(
                                    ResultSet.class
                            );

                    when(
                            resultSet.getLong("EVENT_ID")
                    ).thenReturn(eventId);

                    when(
                            resultSet.getLong("PAYMENT_ID")
                    ).thenReturn(paymentId);

                    when(
                            resultSet.getString("EVENT_TYPE")
                    ).thenReturn(eventType);

                    when(
                            resultSet.getString("PAYLOAD")
                    ).thenReturn(payload);

                    when(
                            resultSet.getString("STATUS")
                    ).thenReturn(status);

                    Object mappedRow =
                            rowMapper.mapRow(
                                    resultSet,
                                    0
                            );

                    return List.of(
                            mappedRow
                    );
                }
        ).when(
                jdbcTemplate
        ).query(
                anyString(),
                any(RowMapper.class),
                eq(eventId)
        );
    }
}