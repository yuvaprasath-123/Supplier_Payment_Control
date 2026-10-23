package com.ofss.risk.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RiskAssessmentResponse(

        Long assessmentId,

        Long paymentId,

        Long supplierId,

        Long invoiceId,

        BigDecimal paymentAmount,

        Long configurationVersionId,

        Integer riskScore,

        String riskBand,

        Integer riskApprovalLevel,

        String status,

        LocalDateTime completedAt,

        List<RiskFactorResponse> factors

) {
}