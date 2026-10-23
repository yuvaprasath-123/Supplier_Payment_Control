package com.ofss.configuration.readiness;

import java.util.List;

public record PublicationReadiness(String versionId, String rowVersion, String publicationState,
        boolean isDemonstration, boolean structurallyValid, boolean ready, List<Issue> issues) {
    public enum Category { CONFIGURATION_ERROR, POLICY_BLOCKER }
    public record Issue(Category category, String code, String path, String message) {}
}
