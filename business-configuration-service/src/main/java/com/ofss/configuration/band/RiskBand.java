package com.ofss.configuration.band;

public record RiskBand(long bandConfigId, long versionId, String bandCode, int minScoreInclusive,
        Integer maxScoreInclusive, int approvalLevel) {
    public record Response(String bandConfigId, String versionId, String bandCode,
            int minScoreInclusive, Integer maxScoreInclusive, int approvalLevel) {}

    public Response response() {
        return new Response(Long.toString(bandConfigId), Long.toString(versionId), bandCode,
                minScoreInclusive, maxScoreInclusive, approvalLevel);
    }
}
