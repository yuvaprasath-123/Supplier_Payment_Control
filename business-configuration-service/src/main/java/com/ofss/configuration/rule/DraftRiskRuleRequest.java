package com.ofss.configuration.rule;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import com.ofss.risk.contract.RuleCode;

public record DraftRiskRuleRequest(String displayName, boolean enabled, int weight,
        String parameterSchemaVersion, Map<String, Object> parameters,
        int evaluationOrder, long expectedRowVersion) {
    public static final Set<String> RULE_CODES = Arrays.stream(RuleCode.values())
            .map(Enum::name).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> FIELDS = Set.of("displayName", "enabled", "weight",
            "parameterSchemaVersion", "parameters", "evaluationOrder", "expectedRowVersion");

    public static long versionId(String value) {
        long parsed = decimalString(value);
        if (parsed == 0) throw invalid();
        return parsed;
    }

    public static void validateRuleCode(String code) {
        if (!RULE_CODES.contains(code)) throw invalid();
    }

    @SuppressWarnings("unchecked")
    public static DraftRiskRuleRequest from(Map<String, Object> body) {
        if (body == null || !FIELDS.equals(body.keySet())) throw invalid();
        String name = text(body.get("displayName"), 120);
        String schema = text(body.get("parameterSchemaVersion"), 32);
        if (!(body.get("enabled") instanceof Boolean enabled)) throw invalid();
        long weight = integer(body.get("weight"));
        long order = integer(body.get("evaluationOrder"));
        if (weight < 0 || weight > Integer.MAX_VALUE || order < 1 || order > 99999) throw invalid();
        if (!(body.get("parameters") instanceof Map<?, ?> parameters)
                || parameters.keySet().stream().anyMatch(key -> !(key instanceof String))) throw invalid();
        Object expected = body.get("expectedRowVersion");
        long rowVersion = expected instanceof String value ? decimalString(value) : integer(expected);
        if (rowVersion < 0) throw invalid();
        return new DraftRiskRuleRequest(name, enabled, (int) weight, schema,
                (Map<String, Object>) parameters, (int) order, rowVersion);
    }

    private static String text(Object value, int maximum) {
        if (!(value instanceof String text) || text.isBlank()
                || text.codePointCount(0, text.length()) > maximum) throw invalid();
        return text;
    }

    private static long integer(Object value) {
        if (!(value instanceof Integer || value instanceof Long || value instanceof BigInteger)) throw invalid();
        try { return new BigInteger(value.toString()).longValueExact(); }
        catch (ArithmeticException exception) { throw invalid(); }
    }

    private static long decimalString(String value) {
        if (value == null || !value.matches("[0-9]+")) throw invalid();
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw invalid(); }
    }

    private static InvalidConfigurationRequestException invalid() { return new InvalidConfigurationRequestException(); }
}
