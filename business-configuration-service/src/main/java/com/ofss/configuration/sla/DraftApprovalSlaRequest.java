package com.ofss.configuration.sla;

import com.ofss.configuration.rule.InvalidConfigurationRequestException;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

public record DraftApprovalSlaRequest(long durationSeconds, String startEventCode,
        String escalationPolicyCode, String terminalLevelPolicyCode, long expectedRowVersion) {
    private static final Set<String> FIELDS = Set.of("durationSeconds", "startEventCode",
            "escalationPolicyCode", "terminalLevelPolicyCode", "expectedRowVersion");
    private static final Set<String> REQUIRED = Set.of("durationSeconds", "startEventCode",
            "escalationPolicyCode", "expectedRowVersion");
    public static int approvalLevel(String value) {
        if (!value.matches("[1-3]")) throw invalid();
        return Integer.parseInt(value);
    }
    public static DraftApprovalSlaRequest from(Map<String, Object> body) {
        if (body == null || !FIELDS.containsAll(body.keySet()) || !body.keySet().containsAll(REQUIRED)) throw invalid();
        long duration = integer(body.get("durationSeconds"));
        if (duration < 1 || duration > 999999999999L) throw invalid();
        String start = text(body.get("startEventCode"));
        String escalation = text(body.get("escalationPolicyCode"));
        Object terminalValue = body.get("terminalLevelPolicyCode");
        String terminal = terminalValue == null ? null : text(terminalValue);
        Object expected = body.get("expectedRowVersion");
        long rowVersion;
        if (expected instanceof String value) {
            if (!value.matches("[0-9]+")) throw invalid();
            try { rowVersion = Long.parseLong(value); }
            catch (NumberFormatException exception) { throw invalid(); }
        } else rowVersion = integer(expected);
        if (rowVersion < 0) throw invalid();
        return new DraftApprovalSlaRequest(duration, start, escalation, terminal, rowVersion);
    }
    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank() || text.codePointCount(0, text.length()) > 32
                || text.codePoints().anyMatch(point -> point >= 0xD800 && point <= 0xDFFF)) throw invalid();
        return text;
    }
    private static long integer(Object value) {
        if (!(value instanceof Integer || value instanceof Long || value instanceof BigInteger)) throw invalid();
        try { return new BigInteger(value.toString()).longValueExact(); }
        catch (ArithmeticException exception) { throw invalid(); }
    }
    private static InvalidConfigurationRequestException invalid() { return new InvalidConfigurationRequestException(); }
}
