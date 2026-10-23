
package com.ofss.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentOutboxPublisherManualTest {

    @Autowired
    private PaymentOutboxPublisher publisher;

    @Test
    void publishApprovedPaymentThree() {
        var result = publisher.publishEvent(1L);

        System.out.println(
                "Event ID: " + result.eventId()
        );

        System.out.println(
                "Payment ID: " + result.paymentId()
        );

        System.out.println(
                "Status: " + result.outboxStatus()
        );
    }
}
