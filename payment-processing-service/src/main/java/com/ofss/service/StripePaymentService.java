
package com.ofss.service;

import com.ofss.event.PaymentApprovedEvent;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentIntentSearchResult;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class StripePaymentService {

    @Value("${stripe.currency}")
    private String currency;

    @Value("${stripe.test-payment-method}")
    private String testPaymentMethod;

    /*
     * Existing payment creation method.
     * Do not call this method during reconciliation.
     */
    public PaymentIntent createAndConfirmPayment(
            PaymentApprovedEvent event
    ) throws StripeException {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Payment event is required"
            );
        }

        if (event.getPaymentId() == null
                || event.getPaymentId() <= 0) {
            throw new IllegalArgumentException(
                    "Valid payment ID is required"
            );
        }

        if (event.getPaymentAmount() == null
                || event.getPaymentAmount()
                        .compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        long amountInPaise =
                event.getPaymentAmount()
                        .multiply(new BigDecimal("100"))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();

        PaymentIntentCreateParams.AutomaticPaymentMethods
                automaticPaymentMethods =
                PaymentIntentCreateParams
                        .AutomaticPaymentMethods
                        .builder()
                        .setEnabled(true)
                        .setAllowRedirects(
                                PaymentIntentCreateParams
                                        .AutomaticPaymentMethods
                                        .AllowRedirects.NEVER
                        )
                        .build();

        PaymentIntentCreateParams params =
                PaymentIntentCreateParams
                        .builder()
                        .setAmount(amountInPaise)
                        .setCurrency(currency)
                        .setPaymentMethod(testPaymentMethod)
                        .setConfirm(true)
                        .setAutomaticPaymentMethods(
                                automaticPaymentMethods
                        )
                        .putMetadata(
                                "paymentId",
                                event.getPaymentId().toString()
                        )
                        .putMetadata(
                                "invoiceId",
                                String.valueOf(event.getInvoiceId())
                        )
                        .putMetadata(
                                "supplierId",
                                String.valueOf(event.getSupplierId())
                        )
                        .build();

        RequestOptions requestOptions =
                RequestOptions
                        .builder()
                        .setIdempotencyKey(
                                "payment-approved-"
                                        + event.getPaymentId()
                        )
                        .build();

        return PaymentIntent.create(
                params,
                requestOptions
        );
    }

    /*
     * Retrieve a known Stripe PaymentIntent.
     * Read-only: does not create or confirm a payment.
     */
    public PaymentIntent retrievePaymentIntent(
            String stripePaymentIntentId
    ) throws StripeException {

        if (stripePaymentIntentId == null
                || stripePaymentIntentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Stripe PaymentIntent ID is required"
            );
        }

        if (!stripePaymentIntentId.startsWith("pi_")) {
            throw new IllegalArgumentException(
                    "Invalid Stripe PaymentIntent ID"
            );
        }

        return PaymentIntent.retrieve(
                stripePaymentIntentId
        );
    }

    /*
     * Find existing PaymentIntents by Oracle payment ID.
     *
     * Read-only investigation method.
     *
     * Important:
     * 1. Stripe Search may be unavailable for the account.
     * 2. Search results may be delayed.
     * 3. Zero results do not prove that no charge occurred.
     * 4. Multiple results require manual investigation.
     * 5. This method never creates or confirms a payment.
     *
     * Do not use this result alone to finalize a payment.
     */
    public List<PaymentIntent> findExistingPaymentIntents(
            Long paymentId
    ) throws StripeException {

        if (paymentId == null || paymentId <= 0) {
            throw new IllegalArgumentException(
                    "Valid payment ID is required"
            );
        }

        String query =
                "metadata['paymentId']:'" + paymentId + "'";

        PaymentIntentSearchResult result =
                PaymentIntent.search(
                        Map.of(
                                "query", query,
                                "limit", 100
                        )
                );

        if (result == null || result.getData() == null) {
            throw new IllegalStateException(
                    "Stripe returned an invalid search response"
            );
        }

        /*
         * Never treat a partial search page as a
         * complete list of matching transactions.
         */
        if (Boolean.TRUE.equals(result.getHasMore())) {
            throw new IllegalStateException(
                    "Stripe search results exceed 100 records. "
                            + "Manual investigation required."
            );
        }

        List<PaymentIntent> matches =
                result.getData()
                        .stream()
                        .filter(intent ->
                                intent != null
                                && intent.getMetadata() != null
                                && paymentId.toString().equals(
                                        intent.getMetadata().get(
                                                "paymentId"
                                        )
                                )
                        )
                        .toList();

        return List.copyOf(matches);
    }
}

