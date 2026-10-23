
package com.ofss.kafka;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;

@Configuration
public class ApprovalKafkaListenerConfiguration {

    @Bean("approvalDecisionKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String>
            approvalDecisionKafkaListenerContainerFactory(
                    ConsumerFactory<String, String> consumerFactory,
                    @Qualifier("approvalDecisionErrorHandler")
                    CommonErrorHandler errorHandler
            ) {

        ConcurrentKafkaListenerContainerFactory<String, String>
                factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
