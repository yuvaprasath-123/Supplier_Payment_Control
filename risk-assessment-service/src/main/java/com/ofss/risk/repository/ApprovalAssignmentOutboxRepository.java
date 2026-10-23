
package com.ofss.risk.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ApprovalAssignmentOutboxRepository {

    private static final int MAX_RETRY_SECONDS = 900;

    private final JdbcTemplate jdbcTemplate;

    public ApprovalAssignmentOutboxRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record AssignmentEvent(
            long eventId,
            long paymentId,
            int attemptCount
    ) {}

    public void recordPendingAssignment(long paymentId) {

        if (paymentId <= 0) {
            throw new IllegalArgumentException(
                    "Payment ID must be positive"
            );
        }

        jdbcTemplate.update(
                """
                MERGE INTO RISK_APPROVAL_ASSIGNMENT_OUTBOX o
                USING (
                    SELECT ? AS PAYMENT_ID FROM DUAL
                ) src
                ON (o.PAYMENT_ID = src.PAYMENT_ID)
                WHEN NOT MATCHED THEN
                    INSERT (
                        PAYMENT_ID,
                        STATUS,
                        ATTEMPT_COUNT,
                        NEXT_RETRY_AT,
                        CREATED_AT,
                        UPDATED_AT
                    )
                    VALUES (
                        src.PAYMENT_ID,
                        'PENDING',
                        0,
                        NULL,
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    )
                """,
                paymentId
        );
    }

    /*
     * Claim an eligible event.
     *
     * The transaction commits before the dispatcher
     * makes any HTTP requests.
     */
    @Transactional
    public Optional<AssignmentEvent> claimNextPending() {

        List<AssignmentEvent> candidates =
                jdbcTemplate.query(
                        """
                        SELECT
                            EVENT_ID,
                            PAYMENT_ID,
                            ATTEMPT_COUNT
                        FROM RISK_APPROVAL_ASSIGNMENT_OUTBOX
                        WHERE STATUS = 'PENDING'
                          AND (
                              NEXT_RETRY_AT IS NULL
                              OR NEXT_RETRY_AT <= CURRENT_TIMESTAMP
                          )
                        ORDER BY EVENT_ID
                        FETCH FIRST 10 ROWS ONLY
                        """,
                        (rs, rowNum) -> new AssignmentEvent(
                                rs.getLong("EVENT_ID"),
                                rs.getLong("PAYMENT_ID"),
                                rs.getInt("ATTEMPT_COUNT")
                        )
                );

        for (AssignmentEvent candidate : candidates) {

            int updated = jdbcTemplate.update(
                    """
                    UPDATE RISK_APPROVAL_ASSIGNMENT_OUTBOX
                    SET STATUS = 'IN_PROGRESS',
                        ATTEMPT_COUNT = ATTEMPT_COUNT + 1,
                        LAST_ERROR = NULL,
                        NEXT_RETRY_AT = NULL,
                        UPDATED_AT = CURRENT_TIMESTAMP
                    WHERE EVENT_ID = ?
                      AND STATUS = 'PENDING'
                      AND ATTEMPT_COUNT = ?
                      AND (
                          NEXT_RETRY_AT IS NULL
                          OR NEXT_RETRY_AT <= CURRENT_TIMESTAMP
                      )
                    """,
                    candidate.eventId(),
                    candidate.attemptCount()
            );

            if (updated == 1) {
                return Optional.of(
                        new AssignmentEvent(
                                candidate.eventId(),
                                candidate.paymentId(),
                                candidate.attemptCount() + 1
                        )
                );
            }
        }

        return Optional.empty();
    }

    /*
     * Complete only the claim owned by this worker.
     *
     * A stale worker cannot complete a newer attempt.
     */
    public void markCompleted(
            long eventId,
            int attemptCount
    ) {

        int updated = jdbcTemplate.update(
                """
                UPDATE RISK_APPROVAL_ASSIGNMENT_OUTBOX
                SET STATUS = 'COMPLETED',
                    LAST_ERROR = NULL,
                    NEXT_RETRY_AT = NULL,
                    COMPLETED_AT = CURRENT_TIMESTAMP,
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE EVENT_ID = ?
                  AND STATUS = 'IN_PROGRESS'
                  AND ATTEMPT_COUNT = ?
                """,
                eventId,
                attemptCount
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Assignment claim is no longer active: "
                            + eventId
            );
        }
    }

    /*
     * Retry using exponential backoff:
     *
     * 30, 60, 120, 240, 480, 900 seconds.
     *
     * Only the currently owned attempt can
     * return the event to PENDING.
     */
    public void markFailed(
            long eventId,
            int attemptCount,
            String error
    ) {

        if (attemptCount < 1) {
            throw new IllegalArgumentException(
                    "Attempt count must be positive"
            );
        }

        String safeError =
                error == null
                        ? "Unknown assignment failure"
                        : error.substring(
                                0,
                                Math.min(error.length(), 1000)
                        );

        int delaySeconds =
                calculateRetryDelay(attemptCount);

        int updated = jdbcTemplate.update(
                """
                UPDATE RISK_APPROVAL_ASSIGNMENT_OUTBOX
                SET STATUS = 'PENDING',
                    LAST_ERROR = ?,
                    NEXT_RETRY_AT =
                        CURRENT_TIMESTAMP
                        + NUMTODSINTERVAL(?, 'SECOND'),
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE EVENT_ID = ?
                  AND STATUS = 'IN_PROGRESS'
                  AND ATTEMPT_COUNT = ?
                """,
                safeError,
                delaySeconds,
                eventId,
                attemptCount
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Cannot retry an inactive assignment claim: "
                            + eventId
            );
        }
    }

    private int calculateRetryDelay(
            int attemptCount
    ) {

        int delaySeconds = 30;

        for (int i = 1; i < attemptCount; i++) {

            if (delaySeconds >= MAX_RETRY_SECONDS) {
                return MAX_RETRY_SECONDS;
            }

            delaySeconds = Math.min(
                    delaySeconds * 2,
                    MAX_RETRY_SECONDS
            );
        }

        return delaySeconds;
    }

    /*
     * Recover claims stranded by a crash or restart.
     *
     * The next worker will increment ATTEMPT_COUNT
     * when it claims the recovered event.
     */
    public int recoverStaleClaims(
            int staleMinutes
    ) {

        if (staleMinutes < 1) {
            throw new IllegalArgumentException(
                    "staleMinutes must be positive"
            );
        }

        return jdbcTemplate.update(
                """
                UPDATE RISK_APPROVAL_ASSIGNMENT_OUTBOX
                SET STATUS = 'PENDING',
                    LAST_ERROR =
                        'Recovered stale assignment claim',
                    NEXT_RETRY_AT = CURRENT_TIMESTAMP,
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE STATUS = 'IN_PROGRESS'
                  AND UPDATED_AT <
                      CURRENT_TIMESTAMP
                      - NUMTODSINTERVAL(?, 'MINUTE')
                """,
                staleMinutes
        );
    }
}

