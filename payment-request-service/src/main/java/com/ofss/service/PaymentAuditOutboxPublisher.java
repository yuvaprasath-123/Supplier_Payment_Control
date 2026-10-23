
package com.ofss.service;

import java.io.IOException;
import java.net.URI;
import java.sql.Clob;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class PaymentAuditOutboxPublisher {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String auditServiceUrl;
    private final String writerToken;

    private record AuditOutboxRow(
            Long eventId,
            Long paymentId,
            String eventType,
            String payload,
            String status
    ) {}

    public PaymentAuditOutboxPublisher(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${audit.service.base-url}") String auditServiceUrl,
            @Value("${audit.service.writer-token}") String writerToken
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.auditServiceUrl = auditServiceUrl;
        this.writerToken = writerToken;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                Duration.ofSeconds(5)
        );

        requestFactory.setReadTimeout(
                Duration.ofSeconds(15)
        );

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /*
     * Deliver one committed audit outbox event.
     *
     * The Oracle row is locked while the HTTP delivery
     * takes place. This prevents concurrent dispatchers
     * from publishing the same row simultaneously.
     *
     * Retries remain safe because Audit Service
     * implements source-event idempotency.
     */
    @Transactional
    public void publishEvent(Long eventId) {

        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException(
                    "Valid audit outbox event ID is required"
            );
        }

        /*
         * Read the complete Oracle CLOB within the
         * same transaction that locks the outbox row.
         */
        List<AuditOutboxRow> rows = jdbcTemplate.query(
                """
                SELECT
                    EVENT_ID,
                    PAYMENT_ID,
                    EVENT_TYPE,
                    PAYLOAD,
                    STATUS
                FROM PAY_AUDIT_OUTBOX
                WHERE EVENT_ID = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new AuditOutboxRow(
                        rs.getLong("EVENT_ID"),
                        rs.getLong("PAYMENT_ID"),
                        rs.getString("EVENT_TYPE"),
                        rs.getString("PAYLOAD"),
                        rs.getString("STATUS")
                ),
                eventId
        );

        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "Audit outbox event not found: " + eventId
            );
        }

        AuditOutboxRow row = rows.get(0);

        if ("PUBLISHED".equals(row.status())) {
            return;
        }

        if (!"PENDING".equals(row.status())) {
            throw new IllegalStateException(
                    "Audit event is not pending: " + eventId
            );
        }

        String payload = row.payload();

        if (payload == null || payload.isBlank()) {
            throw new IllegalStateException(
                    "Audit payload is empty: " + eventId
            );
        }

        JsonNode parsed = objectMapper.readTree(payload);

        if (!(parsed instanceof ObjectNode event)) {
            throw new IllegalStateException(
                    "Audit payload must be a JSON object"
            );
        }

        /*
         * The source event ID is derived from the
         * persisted Oracle outbox ID, so it remains
         * stable across all retries.
         */
        event.put(
                "sourceService",
                "payment-request-service"
        );

        event.put(
                "sourceEventId",
                "audit-outbox-" + eventId
        );

        event.put(
                "paymentId",
                row.paymentId()
        );

        if (!row.eventType().equals(
                event.path("eventType").asText()
        )) {
            throw new IllegalStateException(
                    "Audit event type does not match Oracle: "
                            + eventId
            );
        }

        if (!"PAYMENT".equals(
                event.path("resourceType").asText()
        )) {
            throw new IllegalStateException(
                    "Unexpected audit resource type: " + eventId
            );
        }

        if (!row.paymentId().toString().equals(
                event.path("resourceId").asText()
        )) {
            throw new IllegalStateException(
                    "Audit resource ID does not match payment ID: "
                            + eventId
            );
        }

        String requestBody =
                objectMapper.writeValueAsString(event);

        /*
         * API NAME: Create Audit Event API
         * METHOD: POST
         * URL: http://localhost:8088/api/v1/audit-events
         *
         * A successful response means Audit Service
         * accepted the event.
         *
         * HTTP errors cause the Oracle transaction
         * to roll back, leaving the event PENDING.
         */
        restClient.post()
                .uri(URI.create(
                        auditServiceUrl + "/api/v1/audit-events"
                ))
                .header(
                        "X-Audit-Service-Token",
                        writerToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();

        /*
         * Only mark the event PUBLISHED after the
         * Audit Service acknowledges delivery.
         */
        int updated = jdbcTemplate.update(
                """
                UPDATE PAY_AUDIT_OUTBOX
                SET
                    STATUS = 'PUBLISHED',
                    PUBLISHED_AT = SYSTIMESTAMP,
                    ATTEMPT_COUNT = ATTEMPT_COUNT + 1,
                    LAST_ERROR = NULL
                WHERE EVENT_ID = ?
                  AND STATUS = 'PENDING'
                """,
                eventId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Failed to mark audit event published: "
                            + eventId
            );
        }
    }
}
