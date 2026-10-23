package com.ofss.controller;

import tools.jackson.databind.json.JsonMapper;

import com.ofss.dto.CreatePaymentRequest;
import com.ofss.dto.PaymentRequestResponse;
import com.ofss.entity.PaymentStatus;
import com.ofss.security.JwtAuthFilter;
import com.ofss.security.SecurityConfiguration;
import com.ofss.service.PaymentProcessingDispatchService;
import com.ofss.service.PaymentRequestService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import org.springframework.http.MediaType;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PaymentRequestController.class,
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
        PaymentRequestControllerTest.TestSecurityConfiguration.class
)
class PaymentRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private PaymentRequestService paymentRequestService;

    @MockitoBean
    private PaymentProcessingDispatchService processingDispatchService;

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
    void requesterShouldCreatePayment()
            throws Exception {

        PaymentRequestResponse response =
                new PaymentRequestResponse(
                        1L,
                        10L,
                        2L,
                        new BigDecimal("60000"),
                        1L,
                        PaymentStatus.DRAFT,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        when(
                paymentRequestService
                        .createPayment(any())
        ).thenReturn(response);

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        10L,
                        new BigDecimal("60000")
                );

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests"
                        )
                                .with(
                                        user("requester1")
                                                .roles(
                                                        "PAYMENT_REQUESTER"
                                                )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper
                                                .writeValueAsString(
                                                        request
                                                )
                                )
                )
                .andExpect(
                        status().isCreated()
                );

        verify(
                paymentRequestService
        ).createPayment(any());

        verify(
                processingDispatchService,
                never()
        ).prepareForProcessing(any());
    }

    @Test
    void financeApproverShouldNotCreatePayment()
            throws Exception {

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        10L,
                        new BigDecimal("60000")
                );

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests"
                        )
                                .with(
                                        user("approver1")
                                                .roles(
                                                        "FINANCE_APPROVER"
                                                )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper
                                                .writeValueAsString(
                                                        request
                                                )
                                )
                )
                .andExpect(
                        status().isForbidden()
                );

        verify(
                paymentRequestService,
                never()
        ).createPayment(any());

        verify(
                processingDispatchService,
                never()
        ).prepareForProcessing(any());
    }
}