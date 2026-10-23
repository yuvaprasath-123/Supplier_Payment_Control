package com.ofss.kafka;

import com.ofss.event.PaymentCompletedEvent;
import com.ofss.event.PaymentFailedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentProcessingProducer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PaymentProcessingProducer.class
            );

    private final KafkaTemplate<String, Object>
            kafkaTemplate;

    public PaymentProcessingProducer(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {

        this.kafkaTemplate =
                kafkaTemplate;
    }

    public void publishCompleted(
            PaymentCompletedEvent event
    ) {

        kafkaTemplate.send(
                "payment.completed",
                String.valueOf(
                        event.getPaymentId()
                ),
                event
        );

        LOGGER.info(
                "Published payment.completed for payment ID {}",
                event.getPaymentId()
        );
    }

    public void publishFailed(
            PaymentFailedEvent event
    ) {

        kafkaTemplate.send(
                "payment.failed",
                String.valueOf(
                        event.getPaymentId()
                ),
                event
        );

        LOGGER.info(
                "Published payment.failed for payment ID {}",
                event.getPaymentId()
        );
    }
}