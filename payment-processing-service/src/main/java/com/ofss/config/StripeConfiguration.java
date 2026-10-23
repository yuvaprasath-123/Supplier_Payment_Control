package com.ofss.config;

import com.stripe.Stripe;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfiguration {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @PostConstruct
    public void initializeStripe() {

        Stripe.apiKey = secretKey;
    }
}