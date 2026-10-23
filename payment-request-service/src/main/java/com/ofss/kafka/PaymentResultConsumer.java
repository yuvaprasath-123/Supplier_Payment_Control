package com.ofss.kafka;

import com.ofss.event.PaymentCompletedEvent;
import com.ofss.event.PaymentFailedEvent;
import com.ofss.service.PaymentResultService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentResultConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PaymentResultConsumer.class
            );

    private final PaymentResultService paymentResultService;

    private final ObjectMapper objectMapper;

    public PaymentResultConsumer(
            PaymentResultService paymentResultService,
            ObjectMapper objectMapper
    ) {

        this.paymentResultService =
                paymentResultService;

        this.objectMapper =
                objectMapper;
    }

    @KafkaListener(
            topics = "payment.completed",
            groupId = "payment-request-service"	
    )
    public void consumeCompleted(
            String message
    ) {

        try {

            LOGGER.info(
                    "Received payment.completed payload: {}",
                    message
            );

            PaymentCompletedEvent event =
                    objectMapper.readValue(
                            message,
                            PaymentCompletedEvent.class
                    );

            LOGGER.info(
                    "Processing payment.completed for payment ID {}",
                    event.getPaymentId()
            );

            paymentResultService
                    .handleCompleted(
                            event
                    );

            LOGGER.info(
                    "Payment ID {} updated to COMPLETE",
                    event.getPaymentId()
            );

        } catch (Exception exception) {

            LOGGER.error(
                    "Failed to process payment.completed message: {}",
                    message,
                    exception
            );

            throw new RuntimeException(
                    "Unable to process payment.completed event",
                    exception
            );
        }
    }

    @KafkaListener(
            topics = "payment.failed",
            groupId = "payment-request-service"
    )
    public void consumeFailed(
            String message
    ) {

        try {

            LOGGER.info(
                    "Received payment.failed payload: {}",
                    message
            );

            PaymentFailedEvent event =
                    objectMapper.readValue(
                            message,
                            PaymentFailedEvent.class
                    );

            LOGGER.info(
                    "Processing payment.failed for payment ID {}",
                    event.getPaymentId()
            );

            paymentResultService
                    .handleFailed(
                            event
                    );

            LOGGER.info(
                    "Payment ID {} updated to FAILED",
                    event.getPaymentId()
            );

        } catch (Exception exception) {

            LOGGER.error(
                    "Failed to process payment.failed message: {}",
                    message,
                    exception
            );

            throw new RuntimeException(
                    "Unable to process payment.failed event",
                    exception
            );
        }
    }
}
