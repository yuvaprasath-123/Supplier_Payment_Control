
package com.ofss.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentOutboxDispatcher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PaymentOutboxDispatcher.class
            );

    private final JdbcTemplate jdbcTemplate;
    private final PaymentOutboxPublisher publisher;
    private final boolean enabled;

    public PaymentOutboxDispatcher(
            JdbcTemplate jdbcTemplate,
            PaymentOutboxPublisher publisher,
            @Value("${payment.outbox.enabled:false}")
            boolean enabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.publisher = publisher;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString =
                    "${payment.outbox.interval-ms:5000}"
    )
    public void dispatchPendingEvents() {

        if (!enabled) {
            return;
        }

        List<Long> eventIds = jdbcTemplate.query(
                """
                SELECT EVENT_ID
                FROM PAY_PAYMENT_OUTBOX
                WHERE STATUS = 'PENDING'
                ORDER BY EVENT_ID
                FETCH FIRST 20 ROWS ONLY
                """,
                (rs, rowNum) -> rs.getLong("EVENT_ID")
        );

        for (Long eventId : eventIds) {
            try {
                publisher.publishEvent(eventId);

            } catch (Exception exception) {
                LOGGER.error(
                        "Unable to publish payment outbox event {}. "
                                + "It remains pending for retry.",
                        eventId,
                        exception
                );
            }
        }
    }
}
