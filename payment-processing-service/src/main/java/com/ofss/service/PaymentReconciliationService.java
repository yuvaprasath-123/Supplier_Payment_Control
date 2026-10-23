
package com.ofss.service;

import com.stripe.model.PaymentIntent;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentReconciliationService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PaymentReconciliationService.class
            );

    private final JdbcTemplate jdbcTemplate;
    private final StripePaymentService stripePaymentService;
    private final PaymentProcessingAttemptResultService resultService;

    public PaymentReconciliationService(
            JdbcTemplate jdbcTemplate,
            StripePaymentService stripePaymentService,
            PaymentProcessingAttemptResultService resultService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.stripePaymentService = stripePaymentService;
        this.resultService = resultService;
    }

    public record ReconciliationCandidate(
            Long paymentId,
            String status,
            String stripePaymentIntentId
    ) {}

    public void inspectCandidates() {

        List<ReconciliationCandidate> candidates =
                jdbcTemplate.query(
                        """
                        SELECT
                            PAYMENT_ID,
                            STATUS,
                            STRIPE_PAYMENT_INTENT_ID
                        FROM PAY_PROCESSING_ATTEMPTS
                        WHERE STATUS IN (
                            'REQUIRES_RECONCILIATION',
                            'PROCESSING'
                        )
                        ORDER BY PAYMENT_ID
                        """,
                        (rs, rowNum) ->
                                new ReconciliationCandidate(
                                        rs.getLong("PAYMENT_ID"),
                                        rs.getString("STATUS"),
                                        rs.getString(
                                                "STRIPE_PAYMENT_INTENT_ID"
                                        )
                                )
                );

        LOGGER.info(
                "Found {} reconciliation candidates",
                candidates.size()
        );

        for (ReconciliationCandidate candidate : candidates) {
            inspectCandidate(candidate);
        }
    }

    private void inspectCandidate(
            ReconciliationCandidate candidate
    ) {

        Long paymentId = candidate.paymentId();
        String oracleStatus = candidate.status();
        String intentId = candidate.stripePaymentIntentId();

        if ("PROCESSING".equals(oracleStatus)) {
            LOGGER.warn(
                    "Payment {} is still PROCESSING. "
                            + "Automatic recovery skipped.",
                    paymentId
            );
            return;
        }

        if (!"REQUIRES_RECONCILIATION".equals(oracleStatus)) {
            return;
        }

        if (intentId == null || intentId.isBlank()) {
            LOGGER.warn(
                    "Payment {} requires manual investigation: "
                            + "Stripe PaymentIntent ID is missing.",
                    paymentId
            );
            return;
        }

        PaymentIntent intent;

        try {
            /*
             * Read-only Stripe lookup.
             * Never creates or confirms a charge.
             */
            intent = stripePaymentService.retrievePaymentIntent(
                    intentId
            );
        } catch (Exception exception) {
            LOGGER.error(
                    "Unable to retrieve existing Stripe "
                            + "PaymentIntent for payment {}",
                    paymentId,
                    exception
            );
            return;
        }

        if (intent == null
                || intent.getId() == null
                || !intentId.equals(intent.getId())) {
            LOGGER.error(
                    "Stripe returned an unexpected PaymentIntent "
                            + "for payment {}",
                    paymentId
            );
            return;
        }

        String stripeStatus = intent.getStatus();

        LOGGER.info(
                "Reconciliation inspection: paymentId={}, "
                        + "oracleStatus={}, "
                        + "stripeIntentId={}, "
                        + "stripeStatus={}",
                paymentId,
                oracleStatus,
                intentId,
                stripeStatus
        );

        /*
         * Case 1: Stripe confirms successful payment.
         */
        if ("succeeded".equalsIgnoreCase(stripeStatus)) {

            try {
                var result =
                        resultService.recordReconciledCompletion(
                                paymentId,
                                intent
                        );

                LOGGER.info(
                        "Payment {} reconciled successfully. "
                                + "Completion outbox event {} created.",
                        paymentId,
                        result.outboxEventId()
                );

            } catch (Exception exception) {
                LOGGER.error(
                        "Unable to finalize successful reconciliation "
                                + "for payment {}",
                        paymentId,
                        exception
                );
            }

            return;
        }

        /*
         * Case 2: Stripe confirms a canceled PaymentIntent.
         *
         * The result service independently verifies
         * the PaymentIntent, Oracle amount, payment
         * identity, and existing attempt state.
         */
        if ("canceled".equalsIgnoreCase(stripeStatus)) {

            try {
                var result = resultService.recordFailed(
                        paymentId,
                        intent
                );

                LOGGER.info(
                        "Payment {} marked FAILED after "
                                + "verified Stripe cancellation. "
                                + "Failure outbox event {} created.",
                        paymentId,
                        result.outboxEventId()
                );

            } catch (Exception exception) {
                LOGGER.error(
                        "Unable to finalize canceled Stripe "
                                + "PaymentIntent for payment {}",
                        paymentId,
                        exception
                );
            }

            return;
        }

        /*
         * All other Stripe outcomes remain unresolved.
         * Never assume a network error or intermediate
         * status means the payment failed.
         */
        LOGGER.warn(
                "Payment {} requires manual investigation. "
                        + "Stripe status={}.",
                paymentId,
                stripeStatus
        );
    }
}
