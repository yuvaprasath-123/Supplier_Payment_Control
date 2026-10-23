package com.ofss.configuration.sla;

public record ApprovalSla(long slaConfigId, long versionId, int approvalLevel, long durationSeconds,
        String startEventCode, String escalationPolicyCode, String terminalLevelPolicyCode) {
    public record Response(String slaConfigId, String versionId, int approvalLevel, long durationSeconds,
            String startEventCode, String escalationPolicyCode, String terminalLevelPolicyCode) {}
    public Response response() {
        return new Response(Long.toString(slaConfigId), Long.toString(versionId), approvalLevel, durationSeconds,
                startEventCode, escalationPolicyCode, terminalLevelPolicyCode);
    }
}
