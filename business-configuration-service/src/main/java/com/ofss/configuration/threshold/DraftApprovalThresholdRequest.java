package com.ofss.configuration.threshold;

import com.ofss.configuration.rule.InvalidConfigurationRequestException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

public record DraftApprovalThresholdRequest(BigDecimal minAmountInclusive,
        BigDecimal maxAmountExclusive, long expectedRowVersion) {
    private static final BigDecimal LIMIT = new BigDecimal("9999999999999.99");
    private static final Set<String> FIELDS = Set.of("minAmountInclusive", "maxAmountExclusive", "expectedRowVersion");

    public static int approvalLevel(String value) {
        if (!value.matches("[1-3]")) throw invalid();
        return Integer.parseInt(value);
    }

    public static DraftApprovalThresholdRequest from(Map<String, Object> body) {
        if (body == null || !FIELDS.containsAll(body.keySet())
                || !body.containsKey("minAmountInclusive") || !body.containsKey("expectedRowVersion")) throw invalid();
        BigDecimal minimum = money(body.get("minAmountInclusive"));
        Object upper = body.get("maxAmountExclusive");
        BigDecimal maximum = upper == null ? null : money(upper);
        if (maximum != null && maximum.compareTo(minimum) <= 0) throw invalid();
        Object expected = body.get("expectedRowVersion");
        long rowVersion;
        try {
            if (expected instanceof String value && value.matches("[0-9]+")) rowVersion = Long.parseLong(value);
            else if (expected instanceof Integer || expected instanceof Long || expected instanceof BigInteger) {
                rowVersion = new BigInteger(expected.toString()).longValueExact();
            } else throw invalid();
        } catch (NumberFormatException | ArithmeticException exception) { throw invalid(); }
        if (rowVersion < 0) throw invalid();
        return new DraftApprovalThresholdRequest(minimum, maximum, rowVersion);
    }

    // Money is a plain decimal JSON string: never decode or bind through Double.
    private static BigDecimal money(Object value) {
        if (!(value instanceof String text) || !text.matches("[0-9]+(?:\\.[0-9]+)?")) throw invalid();
        try {
            BigDecimal amount = new BigDecimal(text);
            if (amount.compareTo(LIMIT) > 0) throw invalid();
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException exception) { throw invalid(); }
    }
    private static InvalidConfigurationRequestException invalid() { return new InvalidConfigurationRequestException(); }
}
