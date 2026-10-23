package com.ofss;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
                "stripe.secret-key=sk_test_dummy",
                "stripe.currency=inr",
                "stripe.test-payment-method=pm_card_visa",
                "spring.kafka.listener.auto-startup=false"
        }
)
class PaymentProcessingServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}