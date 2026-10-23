package com.ofss.controller;

import com.ofss.security.JwtAuthFilter;
import com.ofss.security.SecurityConfiguration;
import com.ofss.service.PaymentProcessingDispatchService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PaymentProcessingDispatchController.class,
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                JwtAuthFilter.class,
                                SecurityConfiguration.class
                        }
                )
        }
)
@Import(
        PaymentProcessingDispatchControllerTest.TestSecurityConfiguration.class
)
class PaymentProcessingDispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentProcessingDispatchService dispatchService;

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http
        ) throws Exception {

            http
                    .csrf(
                            csrf ->
                                    csrf.disable()
                    )
                    .authorizeHttpRequests(
                            auth ->
                                    auth.anyRequest()
                                            .permitAll()
                    );

            return http.build();
        }
    }

    @Test
    void financeManagerShouldPreparePaymentForProcessing()
            throws Exception {

        Long paymentId = 100L;

        when(
                dispatchService.prepareForProcessing(paymentId)
        ).thenReturn(null);

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests/{paymentId}/prepare-processing",
                                paymentId
                        )
                                .with(
                                        user("financeManager1")
                                                .roles(
                                                        "FINANCE_MANAGER"
                                                )
                                )
                )
                .andExpect(
                        status().isOk()
                );

        verify(
                dispatchService
        ).prepareForProcessing(paymentId);
    }

    @Test
    void paymentRequesterShouldNotPreparePaymentForProcessing()
            throws Exception {

        Long paymentId = 100L;

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests/{paymentId}/prepare-processing",
                                paymentId
                        )
                                .with(
                                        user("requester1")
                                                .roles(
                                                        "PAYMENT_REQUESTER"
                                                )
                                )
                )
                .andExpect(
                        status().isForbidden()
                );

        verify(
                dispatchService,
                never()
        ).prepareForProcessing(paymentId);
    }

    @Test
    void financeApproverShouldNotPreparePaymentForProcessing()
            throws Exception {

        Long paymentId = 100L;

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests/{paymentId}/prepare-processing",
                                paymentId
                        )
                                .with(
                                        user("approver1")
                                                .roles(
                                                        "FINANCE_APPROVER"
                                                )
                                )
                )
                .andExpect(
                        status().isForbidden()
                );

        verify(
                dispatchService,
                never()
        ).prepareForProcessing(paymentId);
    }

    @Test
    void unauthenticatedUserShouldNotPreparePaymentForProcessing()
            throws Exception {

        Long paymentId = 100L;

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests/{paymentId}/prepare-processing",
                                paymentId
                        )
                )
                .andExpect(
                        status().isForbidden()
                );

        verify(
                dispatchService,
                never()
        ).prepareForProcessing(paymentId);
    }
}