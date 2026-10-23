
package com.ofss.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentProcessingStatusService {

    private final JdbcTemplate jdbcTemplate;

    public PaymentProcessingStatusService(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ProcessingStatusResponse(
            Long paymentId,
            String paymentStatus,
            String processingStatus,
            Integer attemptCount,
            String stripePaymentIntentId,
            String lastError
    ) {}

    public ProcessingStatusResponse getStatus(
            Long paymentId
    ) {

        if (paymentId == null || paymentId <= 0) {
            throw new IllegalArgumentException(
                    "Valid payment ID is required"
            );
        }

        List<ProcessingStatusResponse> result =
                jdbcTemplate.query(
                        """
                        SELECT
                            P.PAYMENT_ID,
                            P.STATUS AS PAYMENT_STATUS,
                            A.STATUS AS PROCESSING_STATUS,
                            A.ATTEMPT_COUNT,
                            A.STRIPE_PAYMENT_INTENT_ID,
                            A.LAST_ERROR
                        FROM PAY_PAYMENT_REQUESTS P
                        LEFT JOIN PAY_PROCESSING_ATTEMPTS A
                          ON A.PAYMENT_ID = P.PAYMENT_ID
                        WHERE P.PAYMENT_ID = ?
                        """,
                        (rs, rowNum) ->
                                new ProcessingStatusResponse(
                                        rs.getLong("PAYMENT_ID"),
                                        rs.getString("PAYMENT_STATUS"),
                                        rs.getString("PROCESSING_STATUS"),
                                        rs.getObject(
                                                "ATTEMPT_COUNT",
                                                Integer.class
                                        ),
                                        rs.getString(
                                                "STRIPE_PAYMENT_INTENT_ID"
                                        ),
                                        rs.getString("LAST_ERROR")
                                ),
                        paymentId
                );

        if (result.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Payment not found"
            );
        }

        if (result.size() != 1) {
            throw new IllegalStateException(
                    "Multiple processing records found for payment "
                            + paymentId
            );
        }

        return result.get(0);
    }
}
