
package com.ofss.integration;

import com.ofss.entity.Supplier;
import com.ofss.entity.SupplierStatus;
import com.ofss.repository.InvoiceRepository;
import com.ofss.repository.SupplierRepository;
import com.ofss.security.JwtUserPrinciple;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@Rollback
class SupplierInvoiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private JsonMapper objectMapper;

    /*
     * TEST-ONLY SECURITY CONTEXT
     *
     * The production SupplierService reads the authenticated
     * user ID when creating supplier status history.
     *
     * Since this integration test disables servlet filters,
     * supply the correct application principal directly.
     *
     * This does not modify production JWT validation.
     */
    @BeforeEach
    void setUpAuthentication() {

        JwtUserPrinciple principal =
                new JwtUserPrinciple(
                        4L,
                        "requester2",
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_PAYMENT_REQUESTER"
                                )
                        )
                );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.authorities()
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void clearAuthentication() {

        SecurityContextHolder.clearContext();
    }

    private String uniqueValue(String prefix) {

        return prefix + "-"
                + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);
    }

    private Supplier createSupplier(
            String name,
            SupplierStatus status
    ) {

        Supplier supplier = new Supplier();

        supplier.setSupplierName(
                uniqueValue(name)
        );

        supplier.setStatus(status);

        return supplierRepository.saveAndFlush(
                supplier
        );
    }

    private String invoiceJson(
            String invoiceNumber,
            Long supplierId,
            int amount,
            String invoiceDate,
            String dueDate
    ) {

        return """
                {
                  "invoiceNumber": "%s",
                  "supplierId": %d,
                  "invoiceAmount": %d,
                  "invoiceDate": "%s",
                  "dueDate": "%s"
                }
                """.formatted(
                invoiceNumber,
                supplierId,
                amount,
                invoiceDate,
                dueDate
        );
    }

    @Test
    void createSupplierShouldReturnCreated()
            throws Exception {

        String supplierName =
                uniqueValue("Integration Supplier");

        String requestBody = """
                {
                  "supplierName": "%s",
                  "status": "ACTIVE"
                }
                """.formatted(supplierName);

        mockMvc.perform(
                post("/api/v1/suppliers")
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(requestBody)
        )
        .andExpect(status().isCreated())
        .andExpect(
                jsonPath("$.supplierId").exists()
        )
        .andExpect(
                jsonPath("$.supplierName")
                        .value(supplierName)
        )
        .andExpect(
                jsonPath("$.status")
                        .value("ACTIVE")
        );
    }

    @Test
    void createInvoiceForActiveSupplierShouldReturnCreated()
            throws Exception {

        Supplier supplier = createSupplier(
                "Active Supplier",
                SupplierStatus.ACTIVE
        );

        String invoiceNumber =
                uniqueValue("INT-INV");

        String requestBody = invoiceJson(
                invoiceNumber,
                supplier.getId(),
                60000,
                "2026-10-01",
                "2026-10-31"
        );

        mockMvc.perform(
                post("/api/v1/invoices")
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(requestBody)
        )
        .andExpect(status().isCreated())
        .andExpect(
                jsonPath("$.invoiceId").exists()
        )
        .andExpect(
                jsonPath("$.invoiceNumber")
                        .value(invoiceNumber)
        )
        .andExpect(
                jsonPath("$.supplierId")
                        .value(supplier.getId())
        )
        .andExpect(
                jsonPath("$.invoiceAmount")
                        .value(60000)
        );
    }

    @Test
    void createInvoiceForInactiveSupplierShouldReturnBadRequest()
            throws Exception {

        Supplier supplier = createSupplier(
                "Inactive Supplier",
                SupplierStatus.INACTIVE
        );

        String requestBody = invoiceJson(
                uniqueValue("INT-INACTIVE"),
                supplier.getId(),
                45000,
                "2026-10-01",
                "2026-10-31"
        );

        mockMvc.perform(
                post("/api/v1/invoices")
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(requestBody)
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateInvoiceShouldReturnBadRequest()
            throws Exception {

        Supplier supplier = createSupplier(
                "Duplicate Supplier",
                SupplierStatus.ACTIVE
        );

        String invoiceNumber =
                uniqueValue("INT-DUP");

        String requestBody = invoiceJson(
                invoiceNumber,
                supplier.getId(),
                50000,
                "2026-10-01",
                "2026-10-31"
        );

        mockMvc.perform(
                post("/api/v1/invoices")
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(requestBody)
        )
        .andExpect(status().isCreated());

        mockMvc.perform(
                post("/api/v1/invoices")
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(requestBody)
        )
        .andExpect(status().isBadRequest());

        assertTrue(
                invoiceRepository
                        .existsBySupplierIdAndInvoiceNumber(
                                supplier.getId(),
                                invoiceNumber
                        )
        );
    }

    @Test
    void dueDateBeforeInvoiceDateShouldReturnBadRequest()
            throws Exception {

        Supplier supplier = createSupplier(
                "Date Supplier",
                SupplierStatus.ACTIVE
        );

        String requestBody = invoiceJson(
                uniqueValue("INT-DATE"),
                supplier.getId(),
                25000,
                "2026-10-20",
                "2026-10-10"
        );

        mockMvc.perform(
                post("/api/v1/invoices")
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(requestBody)
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    void createAndGetInvoiceShouldReturnStoredInvoice()
            throws Exception {

        Supplier supplier = createSupplier(
                "Retrieval Supplier",
                SupplierStatus.ACTIVE
        );

        String invoiceNumber =
                uniqueValue("INT-GET");

        String requestBody = invoiceJson(
                invoiceNumber,
                supplier.getId(),
                75000,
                "2026-10-01",
                "2026-11-01"
        );

        String responseBody = mockMvc.perform(
                post("/api/v1/invoices")
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(requestBody)
        )
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();

        JsonNode json =
                objectMapper.readTree(responseBody);

        long invoiceId =
                json.get("invoiceId").asLong();

        mockMvc.perform(
                get(
                        "/api/v1/invoices/{invoiceId}",
                        invoiceId
                )
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$.invoiceId")
                        .value(invoiceId)
        )
        .andExpect(
                jsonPath("$.invoiceNumber")
                        .value(invoiceNumber)
        )
        .andExpect(
                jsonPath("$.supplierId")
                        .value(supplier.getId())
        );
    }

    @Test
    void exactDuplicateCheckShouldReturnTrue()
            throws Exception {

        Supplier supplier = createSupplier(
                "Exact Duplicate Supplier",
                SupplierStatus.ACTIVE
        );

        String invoiceNumber =
                uniqueValue("INT-EXACT");

        String requestBody = invoiceJson(
                invoiceNumber,
                supplier.getId(),
                90000,
                "2026-10-01",
                "2026-10-31"
        );

        mockMvc.perform(
                post("/api/v1/invoices")
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(requestBody)
        )
        .andExpect(status().isCreated());

        mockMvc.perform(
                get(
                        "/api/v1/invoices/duplicates/exact"
                )
                .param(
                        "supplierId",
                        supplier.getId().toString()
                )
                .param(
                        "invoiceNumber",
                        invoiceNumber
                )
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$.duplicate")
                        .value(true)
        );
    }
}

