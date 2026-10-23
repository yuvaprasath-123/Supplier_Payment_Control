package com.ofss.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class RiskAssessmentClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    RiskAssessmentClient.class
            );

    private final RestClient restClient;

    public RiskAssessmentClient(
            @Value(
                    "${services.risk-assessment.base-url:http://localhost:8087}"
            )
            String riskAssessmentBaseUrl
    ) {

        this.restClient =
                RestClient.builder()
                        .baseUrl(
                                riskAssessmentBaseUrl
                        )
                        .build();
    }

    public void assessRisk(
            Long paymentId,
            String validationReference
    ) {

        RiskAssessmentRequest request =
                new RiskAssessmentRequest(
                        paymentId,
                        validationReference
                );

        RiskAssessmentResponse response =
                restClient
                        .post()
                        .uri(
                                "/api/v1/risk-assessments"
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .body(
                                request
                        )
                        .retrieve()
                        .body(
                                RiskAssessmentResponse.class
                        );

        if (response == null) {

            throw new IllegalStateException(
                    "Risk assessment service returned an empty response for payment "
                            + paymentId
            );
        }

        LOGGER.info(
                "Risk assessment completed for payment ID {}. Score={}, band={}, approvalLevel={}",
                paymentId,
                response.riskScore(),
                response.riskBand(),
                response.riskApprovalLevel()
        );
    }

    private record RiskAssessmentRequest(

            Long paymentId,

            String validationReference

    ) {
    }

    private record RiskAssessmentResponse(

            Long assessmentId,

            Long paymentId,

            Integer riskScore,

            String riskBand,

            Integer riskApprovalLevel,

            String status

    ) {
    }
}