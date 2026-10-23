package com.ofss.configuration.band;

import com.ofss.configuration.rule.InvalidConfigurationRequestException;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

public record DraftRiskBandRequest(int minScoreInclusive, Integer maxScoreInclusive,
        int approvalLevel, long expectedRowVersion) {
    private static final Set<String> CODES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> FIELDS = Set.of("minScoreInclusive", "maxScoreInclusive",
            "approvalLevel", "expectedRowVersion");
    private static final Set<String> REQUIRED = Set.of("minScoreInclusive", "approvalLevel", "expectedRowVersion");

    public static void validateCode(String code) {
        if (!CODES.contains(code)) throw invalid();
    }

    public static DraftRiskBandRequest from(Map<String, Object> body) {
        if (body == null || !FIELDS.containsAll(body.keySet()) || !body.keySet().containsAll(REQUIRED)) throw invalid();
        long minimum = integer(body.get("minScoreInclusive"));
        Object upper = body.get("maxScoreInclusive");
        Long maximum = upper == null ? null : integer(upper);
        long level = integer(body.get("approvalLevel"));
        Object expected = body.get("expectedRowVersion");
        long rowVersion;
        if (expected instanceof String value) {
            if (!value.matches("[0-9]+")) throw invalid();
            try { rowVersion = Long.parseLong(value); }
            catch (NumberFormatException exception) { throw invalid(); }
        } else rowVersion = integer(expected);
        if (minimum < 0 || minimum > Integer.MAX_VALUE
                || (maximum != null && (maximum < minimum || maximum > Integer.MAX_VALUE))
                || level < 1 || level > 3 || rowVersion < 0) throw invalid();
        return new DraftRiskBandRequest((int) minimum, maximum == null ? null : maximum.intValue(),
                (int) level, rowVersion);
    }

    private static long integer(Object value) {
        if (!(value instanceof Integer || value instanceof Long || value instanceof BigInteger)) throw invalid();
        try { return new BigInteger(value.toString()).longValueExact(); }
        catch (ArithmeticException exception) { throw invalid(); }
    }

    private static InvalidConfigurationRequestException invalid() { return new InvalidConfigurationRequestException(); }
}
