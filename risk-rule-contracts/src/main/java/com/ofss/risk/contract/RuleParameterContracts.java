package com.ofss.risk.contract;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import static com.ofss.risk.contract.RuleParameters.*;

/** Strict semantic parsing is used for readiness and engine snapshots, not incomplete draft PUTs. */
public final class RuleParameterContracts {
    public static final String VERSION = "risk-policy-v1";
    private RuleParameterContracts() {}

    public static RuleParameters parse(RuleCode code, String version, Map<String, ?> json) {
        if (!VERSION.equals(version)) throw new IllegalArgumentException("Unsupported parameterSchemaVersion; expected risk-policy-v1");
        Objects.requireNonNull(code);
        if (json == null) throw new IllegalArgumentException("Parameters must be an object");
        Set<String> keys = switch (code) {
            case STRUCTURING -> Set.of("windowSeconds", "minimumPayments", "approvalBoundaryLevels", "historyEligibility");
            case UNUSUAL_FREQUENCY -> Set.of("windowSeconds", "maximumSubmissions", "historyEligibility");
            case UNUSUAL_AMOUNT -> Set.of("windowSeconds", "minimumObservations", "meanMultiplier", "zeroBaselinePolicy");
            case PREVIOUS_RISK_FLAGS -> Set.of("windowSeconds", "minimumFlaggedPayments", "qualifyingRuleCodes");
            case NEAR_DUPLICATE_INVOICE -> Set.of("windowSeconds", "historyEligibility", "invoiceNumberNormalization");
            default -> Set.of("windowSeconds");
        };
        if (!keys.equals(json.keySet())) throw new IllegalArgumentException("Missing or unknown parameter fields for " + code);
        long window = integer(json.get("windowSeconds"), "windowSeconds");
        return switch (code) {
            case STRUCTURING -> new Structuring(window, count(json, "minimumPayments"),
                    array(json, "approvalBoundaryLevels").stream().map(v -> exactInt(v, "approvalBoundaryLevels")).toList(), eligibility(json));
            case UNUSUAL_FREQUENCY -> new Frequency(window, count(json, "maximumSubmissions"), eligibility(json));
            case UNUSUAL_AMOUNT -> new Amount(window, count(json, "minimumObservations"), decimal(json.get("meanMultiplier")),
                    enumeration(json, "zeroBaselinePolicy", ZeroBaselinePolicy.class));
            case RECENTLY_REACTIVATED_SUPPLIER -> new Reactivation(window);
            case PREVIOUS_RISK_FLAGS -> new PreviousFlags(window, count(json, "minimumFlaggedPayments"),
                    array(json, "qualifyingRuleCodes").stream().map(v -> enumValue(v, RuleCode.class)).toList());
            case NEW_SUPPLIER -> new NewSupplier(window);
            case NEAR_DUPLICATE_INVOICE -> new NearDuplicate(window, eligibility(json),
                    enumeration(json, "invoiceNumberNormalization", InvoiceNumberNormalization.class));
        };
    }

    public static List<String> unresolved(RuleParameters parameters) {
        var issues = new ArrayList<String>();
        if ((parameters instanceof Structuring structuring && structuring.historyEligibility() == HistoryEligibility.UNRESOLVED)
                || (parameters instanceof Frequency frequency && frequency.historyEligibility() == HistoryEligibility.UNRESOLVED)
                || (parameters instanceof NearDuplicate near && near.historyEligibility() == HistoryEligibility.UNRESOLVED))
            issues.add("History eligibility has not been agreed");
        if (parameters instanceof Amount p && p.zeroBaselinePolicy() == ZeroBaselinePolicy.UNRESOLVED)
            issues.add("Zero-baseline semantics have not been agreed");
        if (parameters instanceof NearDuplicate p && p.invoiceNumberNormalization() == InvoiceNumberNormalization.UNRESOLVED)
            issues.add("Invoice-number normalization has not been agreed");
        return List.copyOf(issues);
    }

    public static boolean matches(RuleCode code, RuleParameters parameters) {
        return switch (code) {
            case STRUCTURING -> parameters instanceof Structuring;
            case UNUSUAL_FREQUENCY -> parameters instanceof Frequency;
            case UNUSUAL_AMOUNT -> parameters instanceof Amount;
            case RECENTLY_REACTIVATED_SUPPLIER -> parameters instanceof Reactivation;
            case PREVIOUS_RISK_FLAGS -> parameters instanceof PreviousFlags;
            case NEW_SUPPLIER -> parameters instanceof NewSupplier;
            case NEAR_DUPLICATE_INVOICE -> parameters instanceof NearDuplicate;
        };
    }
    private static long integer(Object value, String name) {
        if (!(value instanceof Integer || value instanceof Long || value instanceof BigInteger))
            throw new IllegalArgumentException(name + " must be an exact JSON integer");
        try { return new BigInteger(value.toString()).longValueExact(); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException(name + " exceeds integer limits"); }
    }
    private static int exactInt(Object value, String name) {
        long result = integer(value, name);
        if (result < 1 || result > Integer.MAX_VALUE) throw new IllegalArgumentException(name + " must be a positive int");
        return (int) result;
    }
    private static int count(Map<String, ?> json, String name) { return exactInt(json.get(name), name); }
    private static List<?> array(Map<String, ?> json, String name) {
        if (!(json.get(name) instanceof List<?> list)) throw new IllegalArgumentException(name + " must be an array");
        return list;
    }
    private static BigDecimal decimal(Object value) {
        if (!(value instanceof String text) || !text.matches("[0-9]{1,13}(\\.[0-9]{1,16})?"))
            throw new IllegalArgumentException("meanMultiplier must be a positive plain decimal string (up to 13 integer/16 fractional digits)");
        return new BigDecimal(text);
    }
    private static HistoryEligibility eligibility(Map<String, ?> json) { return enumeration(json, "historyEligibility", HistoryEligibility.class); }
    private static <E extends Enum<E>> E enumeration(Map<String, ?> json, String key, Class<E> type) { return enumValue(json.get(key), type); }
    private static <E extends Enum<E>> E enumValue(Object value, Class<E> type) {
        if (!(value instanceof String text)) throw new IllegalArgumentException("Policy/code must be a string");
        try { return Enum.valueOf(type, text); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unsupported policy/code in " + type.getSimpleName()); }
    }
}
