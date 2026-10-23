package com.ofss.integration;

import com.ofss.client.InvoiceClientResponse;
import com.ofss.client.SupplierClientResponse;
import com.ofss.client.SupplierInvoiceClient;

import com.ofss.kafka.PaymentEventProducer;

import com.ofss.repository.PaymentRequestRepository;
import com.ofss.repository.PaymentStatusHistoryRepository;

import com.ofss.security.AuthUtil;

import com.ofss.service.PaymentAuditOutboxPublisher;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;

import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.test.web.servlet.MockMvc;

import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "audit.outbox.enabled=false",
                "payment.outbox.enabled=false"
        }
)
@AutoConfigureMockMvc
@Transactional
@Rollback
class PaymentRequestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    @Autowired
    private PaymentStatusHistoryRepository paymentStatusHistoryRepository;

    /*
     * Supplier and Invoice Service is an external microservice.
     * Keep only the remote boundary mocked.
     */
    @MockitoBean
    private SupplierInvoiceClient supplierInvoiceClient;

    /*
     * Kafka broker interaction is outside the purpose
     * of this HTTP/database integration test.
     */
    @MockitoBean
    private PaymentEventProducer paymentEventProducer;

    /*
     * The real publisher requires the Audit Service
     * writer credential.
     *
     * These tests verify payment request HTTP,
     * JWT security, service logic and Oracle persistence.
     *
     * Outbound Audit Service delivery is tested separately.
     */
    @MockitoBean
    private PaymentAuditOutboxPublisher paymentAuditOutboxPublisher;

    @Test
    void noTokenShouldReturnUnauthorized()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/payment-requests"
                        )
                )
                .andExpect(
                        status().isUnauthorized()
                );

        verifyNoInteractions(
                supplierInvoiceClient,
                paymentEventProducer,
                paymentAuditOutboxPublisher
        );
    }

    @Test
    void financeApproverShouldNotCreatePayment()
            throws Exception {

        String token =
                createToken(
                        200L,
                        "approver1",
                        "FINANCE_APPROVER"
                );

        String requestBody =
                """
                {
                    "invoiceId": 1001,
                    "paymentAmount": 60000
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isForbidden()
                );

        verifyNoInteractions(
                supplierInvoiceClient,
                paymentEventProducer,
                paymentAuditOutboxPublisher
        );
    }

    @Test
    void requesterShouldCreatePaymentAndRequesterIdShouldComeFromJwt()
            throws Exception {

        Long requesterId = 101L;
        Long invoiceId = 1001L;
        Long supplierId = 501L;

        mockActiveInvoiceAndSupplier(
                invoiceId,
                supplierId
        );

        String token =
                createToken(
                        requesterId,
                        "requester101",
                        "PAYMENT_REQUESTER"
                );

        String requestBody =
                """
                {
                    "invoiceId": 1001,
                    "paymentAmount": 60000
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath(
                                "$.invoiceId"
                        ).value(
                                invoiceId
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.supplierId"
                        ).value(
                                supplierId
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.requesterId"
                        ).value(
                                requesterId
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "DRAFT"
                        )
                );

        verifyNoInteractions(
                paymentEventProducer
        );
    }

    @Test
    void inactiveSupplierShouldRejectPaymentCreation()
            throws Exception {

        Long invoiceId = 1002L;
        Long supplierId = 502L;

        InvoiceClientResponse invoice =
                mock(
                        InvoiceClientResponse.class
                );

        SupplierClientResponse supplier =
                mock(
                        SupplierClientResponse.class
                );

        when(
                invoice.invoiceId()
        ).thenReturn(
                invoiceId
        );

        when(
                invoice.supplierId()
        ).thenReturn(
                supplierId
        );

        when(
                supplier.status()
        ).thenReturn(
                "INACTIVE"
        );

        when(
                supplierInvoiceClient.getInvoice(
                        invoiceId
                )
        ).thenReturn(
                invoice
        );

        when(
                supplierInvoiceClient.getSupplier(
                        supplierId
                )
        ).thenReturn(
                supplier
        );

        String token =
                createToken(
                        101L,
                        "requester101",
                        "PAYMENT_REQUESTER"
                );

        String requestBody =
                """
                {
                    "invoiceId": 1002,
                    "paymentAmount": 50000
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );

        verifyNoInteractions(
                paymentEventProducer
        );
    }

    @Test
    void duplicateInvoiceShouldRejectSecondPayment()
            throws Exception {

        Long invoiceId = 1003L;
        Long supplierId = 503L;

        mockActiveInvoiceAndSupplier(
                invoiceId,
                supplierId
        );

        String token =
                createToken(
                        101L,
                        "requester101",
                        "PAYMENT_REQUESTER"
                );

        String requestBody =
                """
                {
                    "invoiceId": 1003,
                    "paymentAmount": 70000
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isCreated()
                );

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );

        verifyNoInteractions(
                paymentEventProducer
        );
    }

    @Test
    void requesterShouldOnlySeeOwnPayments()
            throws Exception {

        Long requesterOneInvoiceId = 1101L;
        Long requesterTwoInvoiceId = 1102L;

        mockActiveInvoiceAndSupplier(
                requesterOneInvoiceId,
                601L
        );

        mockActiveInvoiceAndSupplier(
                requesterTwoInvoiceId,
                602L
        );

        String requesterOneToken =
                createToken(
                        101L,
                        "requester101",
                        "PAYMENT_REQUESTER"
                );

        String requesterTwoToken =
                createToken(
                        102L,
                        "requester102",
                        "PAYMENT_REQUESTER"
                );

        createPayment(
                requesterOneToken,
                requesterOneInvoiceId,
                new BigDecimal("25000")
        );

        createPayment(
                requesterTwoToken,
                requesterTwoInvoiceId,
                new BigDecimal("35000")
        );

        mockMvc.perform(
                        get(
                                "/api/v1/payment-requests"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + requesterOneToken
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.length()"
                        ).value(
                                1
                        )
                )
                .andExpect(
                        jsonPath(
                                "$[0].requesterId"
                        ).value(
                                101
                        )
                )
                .andExpect(
                        jsonPath(
                                "$[0].invoiceId"
                        ).value(
                                requesterOneInvoiceId
                        )
                );
    }

    @Test
    void requesterShouldNotReadAnotherRequestersPayment()
            throws Exception {

        Long invoiceId = 1201L;

        mockActiveInvoiceAndSupplier(
                invoiceId,
                701L
        );

        String requesterOneToken =
                createToken(
                        101L,
                        "requester101",
                        "PAYMENT_REQUESTER"
                );

        String requesterTwoToken =
                createToken(
                        102L,
                        "requester102",
                        "PAYMENT_REQUESTER"
                );

        Long paymentId =
                createPayment(
                        requesterOneToken,
                        invoiceId,
                        new BigDecimal("45000")
                );

        mockMvc.perform(
                        get(
                                "/api/v1/payment-requests/{paymentId}",
                                paymentId
                        )
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + requesterTwoToken
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    void submitDraftPaymentShouldMoveToPendingValidation()
            throws Exception {

        Long invoiceId = 1301L;
        Long supplierId = 801L;

        mockActiveInvoiceAndSupplier(
                invoiceId,
                supplierId
        );

        String token =
                createToken(
                        101L,
                        "requester101",
                        "PAYMENT_REQUESTER"
                );

        Long paymentId =
                createPayment(
                        token,
                        invoiceId,
                        new BigDecimal("55000")
                );

        mockMvc.perform(
                        post(
                                "/api/v1/payment-requests/{paymentId}/submit",
                                paymentId
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.paymentId"
                        ).value(
                                paymentId
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "PENDING_VALIDATION"
                        )
                );

        verify(
                paymentEventProducer,
                times(1)
        ).publishPaymentSubmitted(
                any()
        );
    }

    private String createToken(
            Long userId,
            String username,
            String role
    ) {

        return authUtil.generateJwtToken(
                userId,
                username,
                List.of(role)
        );
    }

    private void mockActiveInvoiceAndSupplier(
            Long invoiceId,
            Long supplierId
    ) {

        InvoiceClientResponse invoice =
                mock(
                        InvoiceClientResponse.class
                );

        SupplierClientResponse supplier =
                mock(
                        SupplierClientResponse.class
                );

        when(
                invoice.invoiceId()
        ).thenReturn(
                invoiceId
        );

        when(
                invoice.supplierId()
        ).thenReturn(
                supplierId
        );

        when(
                supplier.status()
        ).thenReturn(
                "ACTIVE"
        );

        when(
                supplierInvoiceClient.getInvoice(
                        invoiceId
                )
        ).thenReturn(
                invoice
        );

        when(
                supplierInvoiceClient.getSupplier(
                        supplierId
                )
        ).thenReturn(
                supplier
        );
    }

    private Long createPayment(
            String token,
            Long invoiceId,
            BigDecimal amount
    ) throws Exception {

        String requestBody =
                """
                {
                    "invoiceId": %d,
                    "paymentAmount": %s
                }
                """.formatted(
                        invoiceId,
                        amount.toPlainString()
                );

        String responseBody =
                mockMvc.perform(
                                post(
                                        "/api/v1/payment-requests"
                                )
                                        .header(
                                                "Authorization",
                                                "Bearer " + token
                                        )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                requestBody
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode json =
                objectMapper.readTree(
                        responseBody
                );

        return json
                .get(
                        "paymentId"
                )
                .asLong();
    }
}