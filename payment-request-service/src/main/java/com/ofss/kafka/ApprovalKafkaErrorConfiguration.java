
package com.ofss.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class ApprovalKafkaErrorConfiguration {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    ApprovalKafkaErrorConfiguration.class
            );

    @Bean("approvalDecisionErrorHandler")
    public CommonErrorHandler approvalDecisionErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (ConsumerRecord<?, ?> record, Exception exception) -> {

                            LOGGER.error(
                                    "Approval Kafka message failed: topic={}, partition={}, offset={}",
                                    record.topic(),
                                    record.partition(),
                                    record.offset(),
                                    exception
                            );

                            return new org.apache.kafka.common.TopicPartition(
                                    "approval.decided.DLT",
                                    record.partition()
                            );
                        }
                );

        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(1000L, 2L)
        );
    }
}
