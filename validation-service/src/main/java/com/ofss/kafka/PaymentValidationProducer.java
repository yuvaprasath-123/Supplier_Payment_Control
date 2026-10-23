package com.ofss.kafka;

import com.ofss.event.PaymentValidatedEvent;
import com.ofss.event.PaymentValidationFailedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentValidationProducer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PaymentValidationProducer.class
            );

    private static final String
            PAYMENT_VALIDATED_TOPIC =
            "payment.validated";

    private static final String
            PAYMENT_VALIDATION_FAILED_TOPIC =
            "payment.validation.failed";

    private final KafkaTemplate<String, Object>
            kafkaTemplate;

    public PaymentValidationProducer(
            KafkaTemplate<String, Object> kafkaTemplate) {

        this.kafkaTemplate =
                kafkaTemplate;
    }

    public void publishValidated(
            PaymentValidatedEvent event) {

        String key =
                String.valueOf(
                        event.getPaymentId()
                );

        kafkaTemplate.send(
                PAYMENT_VALIDATED_TOPIC,
                key,
                event
        );

        LOGGER.info(
                "Published payment.validated event for payment ID {}",
                event.getPaymentId()
        );
    }

    public void publishValidationFailed(
            PaymentValidationFailedEvent event) {

        String key =
                String.valueOf(
                        event.getPaymentId()
                );

        kafkaTemplate.send(
                PAYMENT_VALIDATION_FAILED_TOPIC,
                key,
                event
        );

        LOGGER.info(
                "Published payment.validation.failed event for payment ID {}. Reason: {}",
                event.getPaymentId(),
                event.getReason()
        );
    }
}