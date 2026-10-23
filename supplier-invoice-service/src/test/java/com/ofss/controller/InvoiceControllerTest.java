
package com.ofss.controller;

import com.ofss.dto.CreateInvoiceRequest;
import com.ofss.dto.InvoiceResponse;
import com.ofss.service.InvoiceService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@Import(InvoiceControllerTest.TestSecurityConfiguration.class)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private InvoiceService invoiceService;
    
    @MockitoBean
    private com.ofss.security.AuthUtil authUtil;

    /*
     * Controller-only tests.
     *
     * Security authorization must be tested separately
     * against the production SecurityFilterChain.
     */
    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain testSecurityFilterChain(
                HttpSecurity http
        ) throws Exception {

            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth ->
                            auth.anyRequest().permitAll()
                    )
                    .build();
        }
    }

    private InvoiceResponse sampleInvoice() {

        return new InvoiceResponse(
                10L,
                "INV-100",
                1L,
                "ABC Supplier",
                new BigDecimal("60000"),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                null,
                null
        );
    }

    @Test
    void createInvoiceShouldReturnCreated()
            throws Exception {

        CreateInvoiceRequest request =
                new CreateInvoiceRequest(
                        "INV-100",
                        1L,
                        new BigDecimal("60000"),
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 31)
                );

        when(
                invoiceService.createInvoice(
                        any(CreateInvoiceRequest.class)
                )
        ).thenReturn(sampleInvoice());

        mockMvc.perform(
                post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        request
                                )
                        )
        )
        .andExpect(status().isCreated())
        .andExpect(
                jsonPath("$.invoiceId").value(10)
        )
        .andExpect(
                jsonPath("$.invoiceNumber").value("INV-100")
        )
        .andExpect(
                jsonPath("$.supplierId").value(1)
        );

        verify(invoiceService).createInvoice(
                any(CreateInvoiceRequest.class)
        );
    }

    @Test
    void getInvoiceShouldReturnInvoice()
            throws Exception {

        when(
                invoiceService.getInvoice(10L)
        ).thenReturn(sampleInvoice());

        mockMvc.perform(
                get("/api/v1/invoices/10")
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$.invoiceId").value(10)
        )
        .andExpect(
                jsonPath("$.invoiceNumber").value("INV-100")
        );

        verify(invoiceService).getInvoice(10L);
    }

    @Test
    void getInvoicesShouldReturnList()
            throws Exception {

        when(
                invoiceService.getInvoices(null)
        ).thenReturn(
                List.of(sampleInvoice())
        );

        mockMvc.perform(
                get("/api/v1/invoices")
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$[0].invoiceId").value(10)
        )
        .andExpect(
                jsonPath("$[0].invoiceNumber").value("INV-100")
        );

        verify(invoiceService).getInvoices(null);
    }

    @Test
    void getInvoicesShouldFilterBySupplierId()
            throws Exception {

        when(
                invoiceService.getInvoices(1L)
        ).thenReturn(
                List.of(sampleInvoice())
        );

        mockMvc.perform(
                get("/api/v1/invoices")
                        .param("supplierId", "1")
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$[0].supplierId").value(1)
        );

        verify(invoiceService).getInvoices(1L);
    }
}
