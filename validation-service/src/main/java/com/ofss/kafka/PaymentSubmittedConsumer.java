package com.ofss.kafka;

import com.ofss.event.PaymentSubmittedEvent;
import com.ofss.event.PaymentValidatedEvent;
import com.ofss.event.PaymentValidationFailedEvent;

import com.ofss.service.PaymentValidationService;
import com.ofss.service.RiskAssessmentClient;
import com.ofss.service.ValidationResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PaymentSubmittedConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PaymentSubmittedConsumer.class
            );

    private final PaymentValidationService
            paymentValidationService;

    private final PaymentValidationProducer
            paymentValidationProducer;

    private final RiskAssessmentClient
            riskAssessmentClient;

    public PaymentSubmittedConsumer(
            PaymentValidationService paymentValidationService,
            PaymentValidationProducer paymentValidationProducer,
            RiskAssessmentClient riskAssessmentClient
    ) {

        this.paymentValidationService =
                paymentValidationService;

        this.paymentValidationProducer =
                paymentValidationProducer;

        this.riskAssessmentClient =
                riskAssessmentClient;
    }

    @KafkaListener(
            topics = "payment.submitted",
            groupId = "validation-service"
    )
    public void consume(
            PaymentSubmittedEvent event
    ) {

        LOGGER.info(
                "Payment submitted event received for payment ID {}",
                event.getPaymentId()
        );

        ValidationResult result =
                paymentValidationService
                        .validate(
                                event
                        );

        /*
         * VALIDATION SUCCESS
         */
        if (result.valid()) {

            LocalDateTime validatedAt =
                    LocalDateTime.now();

            String validationReference =
                    "VAL-"
                            + event.getPaymentId();

            /*
             * Risk assessment happens only
             * after validation succeeds.
             *
             * If this call fails, an exception
             * is allowed to propagate so that
             * the payment does not continue
             * as successfully validated.
             */
            riskAssessmentClient
                    .assessRisk(
                            event.getPaymentId(),
                            validationReference
                    );

            /*
             * Risk assessment succeeded.
             * Now publish the validation
             * success event.
             */
            PaymentValidatedEvent validatedEvent =
                    new PaymentValidatedEvent(
                            event.getPaymentId(),
                            event.getInvoiceId(),
                            event.getPaymentAmount(),
                            event.getSubmittedBy(),
                            event.getSubmittedAt(),
                            validatedAt
                    );

            paymentValidationProducer
                    .publishValidated(
                            validatedEvent
                    );

            LOGGER.info(
                    "Payment validation and risk assessment completed successfully for payment ID {}",
                    event.getPaymentId()
            );

            return;
        }

        /*
         * VALIDATION FAILURE
         */
        PaymentValidationFailedEvent failedEvent =
                new PaymentValidationFailedEvent(
                        event.getPaymentId(),
                        event.getInvoiceId(),
                        event.getPaymentAmount(),
                        result.reason(),
                        LocalDateTime.now()
                );

        paymentValidationProducer
                .publishValidationFailed(
                        failedEvent
                );

        LOGGER.warn(
                "Payment validation failed for payment ID {}. Reason: {}",
                event.getPaymentId(),
                result.reason()
        );
    }
}