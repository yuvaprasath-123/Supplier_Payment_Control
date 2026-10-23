package com.ofss.controller;

import com.ofss.dto.CreateSupplierRequest;
import com.ofss.dto.SupplierResponse;
import com.ofss.entity.SupplierStatus;
import com.ofss.service.SupplierService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupplierController.class)
class SupplierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private SupplierService supplierService;
    
    @MockitoBean
    private com.ofss.security.AuthUtil authUtil;

    @Test
    void createSupplierShouldReturnCreated()
            throws Exception {

        CreateSupplierRequest request =
                new CreateSupplierRequest(
                        "ABC Supplier",
                        SupplierStatus.ACTIVE
                );

        SupplierResponse response =
                new SupplierResponse(
                        1L,
                        "ABC Supplier",
                        SupplierStatus.ACTIVE,
                        null,
                        null
                );

        when(
                supplierService.createSupplier(
                        any(CreateSupplierRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/v1/suppliers"
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
                )
                .andExpect(
                        jsonPath(
                                "$.supplierId"
                        ).value(1)
                )
                .andExpect(
                        jsonPath(
                                "$.supplierName"
                        ).value(
                                "ABC Supplier"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "ACTIVE"
                        )
                );
    }

    @Test
    void getSupplierShouldReturnSupplier()
            throws Exception {

        SupplierResponse response =
                new SupplierResponse(
                        1L,
                        "ABC Supplier",
                        SupplierStatus.ACTIVE,
                        null,
                        null
                );

        when(
                supplierService.getSupplier(
                        1L
                )
        ).thenReturn(response);

        mockMvc.perform(
                        get(
                                "/api/v1/suppliers/1"
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.supplierId"
                        ).value(1)
                )
                .andExpect(
                        jsonPath(
                                "$.supplierName"
                        ).value(
                                "ABC Supplier"
                        )
                );
    }

    @Test
    void getAllSuppliersShouldReturnList()
            throws Exception {

        SupplierResponse response =
                new SupplierResponse(
                        1L,
                        "ABC Supplier",
                        SupplierStatus.ACTIVE,
                        null,
                        null
                );

        when(
                supplierService.getAllSuppliers(
                        null
                )
        ).thenReturn(
                List.of(response)
        );

        mockMvc.perform(
                        get(
                                "/api/v1/suppliers"
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$[0].supplierId"
                        ).value(1)
                );
    }
}