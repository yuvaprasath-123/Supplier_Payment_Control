
package com.ofss.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentAuditOutboxDispatcher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PaymentAuditOutboxDispatcher.class
            );

    private final JdbcTemplate jdbcTemplate;
    private final PaymentAuditOutboxPublisher publisher;
    private final boolean enabled;

    public PaymentAuditOutboxDispatcher(
            JdbcTemplate jdbcTemplate,
            PaymentAuditOutboxPublisher publisher,
            @Value("${audit.outbox.enabled:false}")
            boolean enabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.publisher = publisher;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString =
                    "${audit.outbox.interval-ms:5000}"
    )
    public void dispatchPendingEvents() {

        if (!enabled) {
            return;
        }

        List<Long> pendingEventIds =
                jdbcTemplate.queryForList(
                        """
                        SELECT EVENT_ID
                        FROM (
                            SELECT EVENT_ID
                            FROM PAY_AUDIT_OUTBOX
                            WHERE STATUS = 'PENDING'
                            ORDER BY CREATED_AT, EVENT_ID
                        )
                        WHERE ROWNUM <= 20
                        """,
                        Long.class
                );

        for (Long eventId : pendingEventIds) {

            try {
                publisher.publishEvent(eventId);

                LOGGER.info(
                        "Audit outbox event {} delivered",
                        eventId
                );

            } catch (Exception exception) {

                LOGGER.error(
                        "Audit outbox event {} delivery failed. "
                                + "Event remains pending.",
                        eventId,
                        exception
                );
            }
        }
    }
}
