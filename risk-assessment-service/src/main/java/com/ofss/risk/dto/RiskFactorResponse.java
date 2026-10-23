package com.ofss.risk.dto;

public record RiskFactorResponse(

        String ruleCode,

        boolean triggered,

        int configuredWeight,

        int contribution,

        String explanation

) {
}