package com.ofss.configuration.version;

import java.time.OffsetDateTime;

public record ConfigurationVersion(
        long versionId,
        int versionNumber,
        String versionLabel,
        String publicationState,
        boolean isDemonstration,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt) {
}