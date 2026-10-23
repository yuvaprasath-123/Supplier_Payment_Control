package com.ofss.configuration.rule;

import java.util.Map;

public record RiskRule(long ruleConfigId, long versionId, String ruleCode, String displayName,
        boolean enabled, int weight, String parameterSchemaVersion,
        Map<String, Object> parameters, int evaluationOrder) {
    public record Response(String ruleConfigId, String versionId, String ruleCode, String displayName,
            boolean enabled, int weight, String parameterSchemaVersion,
            Map<String, Object> parameters, int evaluationOrder) {}
    public Response response() {
        return new Response(Long.toString(ruleConfigId), Long.toString(versionId), ruleCode,
                displayName, enabled, weight, parameterSchemaVersion, parameters, evaluationOrder);
    }
}
