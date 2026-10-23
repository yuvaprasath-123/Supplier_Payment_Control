package com.ofss.config;

import org.apache.kafka.clients.admin.NewTopic;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {

    @Bean
    public NewTopic paymentSubmittedTopic() {

        return TopicBuilder
                .name(
                        "payment.submitted"
                )
                .partitions(
                        1
                )
                .replicas(
                        1
                )
                .build();
    }

    @Bean
    public NewTopic paymentValidatedTopic() {

        return TopicBuilder
                .name(
                        "payment.validated"
                )
                .partitions(
                        1
                )
                .replicas(
                        1
                )
                .build();
    }

    @Bean
    public NewTopic paymentValidationFailedTopic() {

        return TopicBuilder
                .name(
                        "payment.validation.failed"
                )
                .partitions(
                        1
                )
                .replicas(
                        1
                )
                .build();
    }
}