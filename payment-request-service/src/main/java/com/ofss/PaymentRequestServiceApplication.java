
package com.ofss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PaymentRequestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                PaymentRequestServiceApplication.class,
                args
        );
    }
}
