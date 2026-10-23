package com.ofss.configuration.rule;

import com.ofss.configuration.version.ConfigurationVersion;
import com.ofss.configuration.version.ConfigurationVersionResponse;

public record ConfigurationVersionDetails(ConfigurationVersion version, String description,
        String scorePolicyCode, Integer maxFinalScore, long rowVersion) {
    public record Response(String versionId, int versionNumber, String versionLabel,
            String publicationState, boolean isDemonstration, String createdAt, String publishedAt,
            String description, String scorePolicyCode, Integer maxFinalScore, String rowVersion) {}
    public Response response() {
        var base = ConfigurationVersionResponse.from(version);
        return new Response(base.versionId(), base.versionNumber(), base.versionLabel(),
                base.publicationState(), base.isDemonstration(), base.createdAt(), base.publishedAt(),
                description, scorePolicyCode, maxFinalScore, Long.toString(rowVersion));
    }
}
