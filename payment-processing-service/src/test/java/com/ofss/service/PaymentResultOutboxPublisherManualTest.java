
package com.ofss.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentResultOutboxPublisherManualTest {

    @Autowired
    private PaymentResultOutboxPublisher publisher;

    @Test
    void publishPaymentThreeCompletion() {

        var result = publisher.publishEvent(1L);

        System.out.println(
                "Event ID: " + result.eventId()
        );

        System.out.println(
                "Payment ID: " + result.paymentId()
        );

        System.out.println(
                "Status: " + result.status()
        );
    }
}
