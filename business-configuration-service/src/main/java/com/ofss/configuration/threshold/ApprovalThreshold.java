package com.ofss.configuration.threshold;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ApprovalThreshold(long thresholdConfigId, long versionId, int approvalLevel,
        BigDecimal minAmountInclusive, BigDecimal maxAmountExclusive) {
    public record Response(String thresholdConfigId, String versionId, int approvalLevel,
            String minAmountInclusive, String maxAmountExclusive) {}
    public Response response() {
        return new Response(Long.toString(thresholdConfigId), Long.toString(versionId), approvalLevel,
                minAmountInclusive.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                maxAmountExclusive == null ? null : maxAmountExclusive.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
    }
}
