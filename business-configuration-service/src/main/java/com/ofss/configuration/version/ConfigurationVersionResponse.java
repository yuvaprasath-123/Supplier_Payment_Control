package com.ofss.configuration.version;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record ConfigurationVersionResponse(
        String versionId,
        int versionNumber,
        String versionLabel,
        String publicationState,
        boolean isDemonstration,
        String createdAt,
        String publishedAt) {

    public static ConfigurationVersionResponse from(
            ConfigurationVersion version) {

        return new ConfigurationVersionResponse(
                Long.toString(
                        version.versionId()
                ),
                version.versionNumber(),
                version.versionLabel(),
                version.publicationState(),
                version.isDemonstration(),
                format(version.createdAt()),
                format(version.publishedAt())
        );
    }

    private static String format(
            OffsetDateTime timestamp) {

        if (timestamp == null) {
            return null;
        }

        return timestamp
                .withOffsetSameInstant(
                        ZoneOffset.UTC
                )
                .format(
                        DateTimeFormatter
                                .ISO_OFFSET_DATE_TIME
                );
    }
}