
package com.ofss.kafka;

import com.ofss.event.PaymentApprovedEvent;
import com.ofss.service.PaymentProcessingAttemptService;
import com.ofss.service.PaymentProcessingAttemptResultService;
import com.ofss.service.StripePaymentService;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentApprovedConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PaymentApprovedConsumer.class
            );

    private final StripePaymentService stripePaymentService;
    private final PaymentProcessingAttemptService attemptService;
    private final PaymentProcessingAttemptResultService resultService;

    public PaymentApprovedConsumer(
            StripePaymentService stripePaymentService,
            PaymentProcessingAttemptService attemptService,
            PaymentProcessingAttemptResultService resultService
    ) {
        this.stripePaymentService = stripePaymentService;
        this.attemptService = attemptService;
        this.resultService = resultService;
    }

    @KafkaListener(
            topics = "payment.approved",
            groupId = "payment-processing-service",
            autoStartup = "${payment.processing.consumer-enabled:false}"
    )
    public void consume(PaymentApprovedEvent event) {

        if (event == null
                || event.getPaymentId() == null
                || event.getPaymentId() <= 0) {
            throw new IllegalArgumentException(
                    "Valid approved-payment event is required"
            );
        }

        Long paymentId = event.getPaymentId();

        /*
         * Verify event data against Oracle and claim
         * the payment before contacting Stripe.
         */
        var claim = attemptService.claim(event);

        switch (claim) {

            case ALREADY_PROCESSING -> {
                LOGGER.warn(
                        "Payment {} already has a processing attempt. "
                                + "Skipping Stripe.",
                        paymentId
                );
                return;
            }

            case ALREADY_COMPLETED -> {
                LOGGER.info(
                        "Payment {} already has a completed attempt. "
                                + "Skipping Stripe.",
                        paymentId
                );
                return;
            }

            case REQUIRES_RECONCILIATION -> {
                LOGGER.warn(
                        "Payment {} requires reconciliation. "
                                + "Skipping Stripe.",
                        paymentId
                );
                return;
            }

            case CLAIMED -> LOGGER.info(
                    "Payment {} validated and claimed.",
                    paymentId
            );
        }

        PaymentIntent intent;

        try {
            intent = stripePaymentService
                    .createAndConfirmPayment(event);

        } catch (StripeException | RuntimeException exception) {

            LOGGER.error(
                    "Stripe request outcome uncertain for payment {}",
                    paymentId,
                    exception
            );

            markForReconciliation(
                    paymentId,
                    null,
                    "Stripe request outcome uncertain: "
                            + exception.getClass().getSimpleName()
            );

            return;
        }

        if (intent == null
                || intent.getId() == null
                || intent.getId().isBlank()) {

            LOGGER.error(
                    "Stripe returned no valid PaymentIntent ID "
                            + "for payment {}",
                    paymentId
            );

            markForReconciliation(
                    paymentId,
                    null,
                    "Stripe returned no valid PaymentIntent ID"
            );

            return;
        }

        String stripePaymentIntentId = intent.getId();
        String stripeStatus = intent.getStatus();

        if ("succeeded".equalsIgnoreCase(stripeStatus)) {

            /*
             * Stripe succeeded.
             *
             * Persist the success and result-outbox event
             * atomically in Oracle.
             *
             * Never issue another Stripe request if
             * Oracle persistence fails.
             */
            try {
                resultService.recordCompleted(
                        paymentId,
                        stripePaymentIntentId,
                        event.getPaymentAmount()
                );

                LOGGER.info(
                        "Payment {} completed at Stripe. "
                                + "PaymentIntent={}. "
                                + "Completion outbox saved.",
                        paymentId,
                        stripePaymentIntentId
                );

            } catch (RuntimeException persistenceException) {

                LOGGER.error(
                        "CRITICAL: Stripe succeeded but Oracle "
                                + "completion persistence failed. "
                                + "Payment={}, PaymentIntent={}. "
                                + "Reconciliation required.",
                        paymentId,
                        stripePaymentIntentId,
                        persistenceException
                );

                /*
                 * recordCompleted() has its own
                 * transactional boundary.
                 *
                 * If it fails, attempt a separate
                 * reconciliation-status update.
                 */
                markForReconciliation(
                        paymentId,
                        stripePaymentIntentId,
                        "Stripe succeeded but Oracle completion "
                                + "persistence failed"
                );

                /*
                 * Do not silently acknowledge successful
                 * processing when completion persistence
                 * has failed.
                 *
                 * A Kafka redelivery will not automatically
                 * call Stripe again because the attempt
                 * remains claimed or requires reconciliation.
                 */
                throw new IllegalStateException(
                        "Stripe succeeded but Oracle completion "
                                + "persistence failed for payment "
                                + paymentId
                                + ", PaymentIntent="
                                + stripePaymentIntentId,
                        persistenceException
                );
            }

            return;
        }

        LOGGER.warn(
                "Payment {} has non-success Stripe status. "
                        + "PaymentIntent={}, status={}",
                paymentId,
                stripePaymentIntentId,
                stripeStatus
        );

        markForReconciliation(
                paymentId,
                stripePaymentIntentId,
                "Stripe PaymentIntent status: "
                        + stripeStatus
        );
    }

    /*
     * Best-effort reconciliation update.
     *
     * If Oracle is unavailable, log both the payment ID
     * and Stripe PaymentIntent ID for investigation.
     *
     * Never retry Stripe automatically here.
     */
    private void markForReconciliation(
            Long paymentId,
            String stripePaymentIntentId,
            String reason
    ) {

        try {
            resultService.recordRequiresReconciliation(
                    paymentId,
                    stripePaymentIntentId,
                    reason
            );

            LOGGER.warn(
                    "Payment {} marked REQUIRES_RECONCILIATION. "
                            + "PaymentIntent={}",
                    paymentId,
                    stripePaymentIntentId
            );

        } catch (RuntimeException reconciliationException) {

            LOGGER.error(
                    "CRITICAL: Failed to persist reconciliation "
                            + "status. Payment={}, PaymentIntent={}. "
                            + "Manual investigation required.",
                    paymentId,
                    stripePaymentIntentId,
                    reconciliationException
            );

            throw reconciliationException;
        }
    }
}

