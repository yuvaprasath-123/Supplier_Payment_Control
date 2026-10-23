
package com.ofss.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ApprovalOutboxDispatcher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    ApprovalOutboxDispatcher.class
            );

    private final JdbcTemplate jdbcTemplate;
    private final ApprovalOutboxPublisher publisher;
    private final boolean enabled;

    public ApprovalOutboxDispatcher(
            JdbcTemplate jdbcTemplate,
            ApprovalOutboxPublisher publisher,
            @Value("${approval.outbox.enabled:false}")
            boolean enabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.publisher = publisher;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString =
                    "${approval.outbox.interval-ms:5000}"
    )
    public void dispatch() {

        if (!enabled) {
            return;
        }

        List<Long> eventIds = jdbcTemplate.query(
                """
                SELECT EVENT_ID
                FROM PAY_APPROVAL_OUTBOX
                WHERE STATUS = 'PENDING'
                ORDER BY EVENT_ID
                FETCH FIRST 20 ROWS ONLY
                """,
                (rs, rowNum) -> rs.getLong("EVENT_ID")
        );

        for (Long eventId : eventIds) {

            try {
                publisher.publish(eventId);

            } catch (Exception exception) {

                LOGGER.error(
                        "Approval outbox event {} failed; "
                                + "will be retried",
                        eventId,
                        exception
                );
            }
        }
    }
}
