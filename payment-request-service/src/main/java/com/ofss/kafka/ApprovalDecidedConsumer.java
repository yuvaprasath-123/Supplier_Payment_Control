
package com.ofss.kafka;

import com.ofss.service.ApprovalResultSynchronizationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ApprovalDecidedConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    ApprovalDecidedConsumer.class
            );

    private final ApprovalResultSynchronizationService
            synchronizationService;

    private final ObjectMapper objectMapper;

    public ApprovalDecidedConsumer(
            ApprovalResultSynchronizationService synchronizationService,
            ObjectMapper objectMapper
    ) {
        this.synchronizationService = synchronizationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "approval.decided",
            groupId = "payment-request-service",
            containerFactory =
                    "approvalDecisionKafkaListenerContainerFactory"
    )
    public void consume(String message) {

        try {
            JsonNode event = objectMapper.readTree(message);

            JsonNode paymentIdNode =
                    event.get("paymentId");

            JsonNode decisionIdNode =
                    event.get("approvalDecisionId");

            if (paymentIdNode == null
                    || !paymentIdNode.isIntegralNumber()
                    || paymentIdNode.longValue() <= 0
                    || decisionIdNode == null
                    || !decisionIdNode.isIntegralNumber()
                    || decisionIdNode.longValue() <= 0) {

                throw new IllegalArgumentException(
                        "Valid paymentId and approvalDecisionId are required"
                );
            }

            Long paymentId = paymentIdNode.longValue();
            Long decisionId = decisionIdNode.longValue();

            LOGGER.info(
                    "Received approval.decided: paymentId={}, decisionId={}",
                    paymentId,
                    decisionId
            );

            var result = synchronizationService.synchronize(
                    paymentId,
                    decisionId
            );

            LOGGER.info(
                    "Approval synchronization completed: paymentId={}, "
                            + "status={}, alreadySynchronized={}",
                    result.paymentId(),
                    result.paymentStatus(),
                    result.alreadySynchronized()
            );

        } catch (Exception exception) {

            LOGGER.error(
                    "Unable to process approval.decided Kafka message",
                    exception
            );

            throw new IllegalStateException(
                    "Approval decision synchronization failed",
                    exception
            );
        }
    }
}

