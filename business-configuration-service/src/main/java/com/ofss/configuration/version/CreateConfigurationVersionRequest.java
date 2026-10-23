package com.ofss.configuration.version;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

public record CreateConfigurationVersionRequest(String versionLabel, String description,
        boolean isDemonstration, String scorePolicyCode, Integer maxFinalScore) {
    private static final Set<String> FIELDS = Set.of("versionLabel", "description",
            "isDemonstration", "scorePolicyCode", "maxFinalScore");

    // Validate raw JSON types before coercion: strings are not booleans, and
    // floating-point numbers (even 1.0) are not integer score limits.
    public static CreateConfigurationVersionRequest from(Map<String, Object> body) {
        if (body == null || !FIELDS.containsAll(body.keySet())) throw invalid();
        Object label = body.get("versionLabel");
        if (!(label instanceof String labelText) || labelText.isBlank() || length(labelText) > 100) throw invalid();
        Object description = body.get("description");
        if (description != null && (!(description instanceof String descriptionText) || length(descriptionText) > 1000)) {
            throw invalid();
        }
        Object demonstration = body.get("isDemonstration");
        if (!(demonstration instanceof Boolean)) throw invalid();
        Object policy = body.get("scorePolicyCode");
        if (!"SUM".equals(policy) && !"CAP_AT_MAX".equals(policy)) throw invalid();
        Object limit = body.get("maxFinalScore");
        Integer score = null;
        if ("SUM".equals(policy)) {
            if (limit != null) throw invalid();
        } else {
            if (!(limit instanceof Integer || limit instanceof Long || limit instanceof BigInteger)) {
                throw invalid();
            }
            BigInteger value = new BigInteger(limit.toString());
            if (value.signum() <= 0 || value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                throw invalid();
            }
            score = value.intValueExact();
        }
        return new CreateConfigurationVersionRequest((String) label, (String) description,
                (Boolean) demonstration, (String) policy, score);
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    private static InvalidDraftRequestException invalid() {
        return new InvalidDraftRequestException();
    }
}
