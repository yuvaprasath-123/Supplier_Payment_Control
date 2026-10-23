
package com.ofss.kafka;

import com.ofss.event.PaymentApprovedEvent;
import com.ofss.event.PaymentSubmittedEvent;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventProducer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PaymentEventProducer.class
            );

    private static final String PAYMENT_SUBMITTED_TOPIC =
            "payment.submitted";

    private static final String PAYMENT_APPROVED_TOPIC =
            "payment.approved";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /*
     * Existing payment-submitted publishing logic.
     * Preserved for the validation workflow.
     */
    public void publishPaymentSubmitted(
            PaymentSubmittedEvent event
    ) {

        String key =
                String.valueOf(event.getPaymentId());

        LOGGER.info(
                "Publishing payment.submitted for payment ID {}",
                event.getPaymentId()
        );

        kafkaTemplate.send(
                PAYMENT_SUBMITTED_TOPIC,
                key,
                event
        );

        LOGGER.info(
                "Kafka publish request sent for payment ID {}",
                event.getPaymentId()
        );
    }

    /*
     * New producer for approved payments.
     * Will be called by the processing dispatch workflow.
     */
    public CompletableFuture<SendResult<String, Object>>
            publishPaymentApproved(
                    PaymentApprovedEvent event
            ) {

        if (event == null
                || event.getPaymentId() == null
                || event.getPaymentId() <= 0) {

            throw new IllegalArgumentException(
                    "Valid approved payment event is required"
            );
        }

        if (event.getPaymentAmount() == null
                || event.getPaymentAmount().signum() <= 0
                || event.getInvoiceId() == null
                || event.getSupplierId() == null
                || event.getApprovedBy() == null
                || event.getApprovedAt() == null) {

            throw new IllegalArgumentException(
                    "Approved payment event is missing required fields"
            );
        }

        CompletableFuture<SendResult<String, Object>> result =
                kafkaTemplate.send(
                        PAYMENT_APPROVED_TOPIC,
                        String.valueOf(event.getPaymentId()),
                        event
                );

        result.whenComplete((sendResult, exception) -> {

            if (exception != null) {

                LOGGER.error(
                        "Failed to publish payment.approved "
                                + "for payment ID {}",
                        event.getPaymentId(),
                        exception
                );

                return;
            }

            RecordMetadata metadata =
                    sendResult.getRecordMetadata();

            LOGGER.info(
                    "Published payment.approved for payment ID {}, "
                            + "partition {}, offset {}",
                    event.getPaymentId(),
                    metadata.partition(),
                    metadata.offset()
            );
        });

        return result;
    }
}
