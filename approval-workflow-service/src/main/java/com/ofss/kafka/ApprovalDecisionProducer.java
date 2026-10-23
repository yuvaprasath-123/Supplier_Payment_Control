
package com.ofss.kafka;

import com.ofss.event.ApprovalDecidedEvent;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.RecordMetadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
public class ApprovalDecisionProducer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    ApprovalDecisionProducer.class
            );

    private static final String TOPIC =
            "approval.decided";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ApprovalDecisionProducer(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, Object>> publish(
            ApprovalDecidedEvent event
    ) {
        if (event == null || event.paymentId() == null) {
            throw new IllegalArgumentException(
                    "Approval event and payment ID are required"
            );
        }

        CompletableFuture<SendResult<String, Object>> result =
                kafkaTemplate.send(
                        TOPIC,
                        String.valueOf(event.paymentId()),
                        event
                );

        result.whenComplete((sendResult, exception) -> {
            if (exception != null) {
                LOGGER.error(
                        "Failed to publish approval decision for payment ID {}",
                        event.paymentId(),
                        exception
                );
                return;
            }

            RecordMetadata metadata =
                    sendResult.getRecordMetadata();

            LOGGER.info(
                    "Published approval.decided for payment ID {}, " +
                    "decision ID {}, partition {}, offset {}",
                    event.paymentId(),
                    event.approvalDecisionId(),
                    metadata.partition(),
                    metadata.offset()
            );
        });

        return result;
    }
}
