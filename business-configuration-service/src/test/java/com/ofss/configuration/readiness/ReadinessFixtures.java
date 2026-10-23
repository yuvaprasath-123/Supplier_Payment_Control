package com.ofss.configuration.readiness;

import com.ofss.configuration.band.RiskBand;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.ApprovalSla;
import com.ofss.configuration.threshold.ApprovalThreshold;
import com.ofss.configuration.version.ConfigurationVersion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/** Test data only; these are not approved business thresholds or seed rows. */
final class ReadinessFixtures {
    static ConfigurationVersionDetails parent(String policy, Integer cap, boolean demo) {
        return new ConfigurationVersionDetails(new ConfigurationVersion(7, 1, "Test fixture", "DRAFT", demo,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), null), null, policy, cap, 8);
    }
    static RiskRule rule(String code, int order, int weight, boolean enabled) {
        return new RiskRule(order, 7, code, "Test rule", enabled, weight, "risk-policy-v1", parameters(code), order);
    }
    static Map<String, Object> parameters(String code) {
        // User-approved demonstration parameter choices, with no seeding or activation.
        return switch (code) {
            case "STRUCTURING" -> Map.of("windowSeconds", 604800, "minimumPayments", 2, "approvalBoundaryLevels", List.of(2, 3), "historyEligibility", "VALIDATED_SUBMISSIONS");
            case "UNUSUAL_FREQUENCY" -> Map.of("windowSeconds", 604800, "maximumSubmissions", 3, "historyEligibility", "VALIDATED_SUBMISSIONS");
            case "UNUSUAL_AMOUNT" -> Map.of("windowSeconds", 7776000, "minimumObservations", 5, "meanMultiplier", "2.00", "zeroBaselinePolicy", "INSUFFICIENT_HISTORY");
            case "PREVIOUS_RISK_FLAGS" -> Map.of("windowSeconds", 7776000, "minimumFlaggedPayments", 1, "qualifyingRuleCodes",
                    List.of("STRUCTURING", "UNUSUAL_FREQUENCY", "UNUSUAL_AMOUNT", "RECENTLY_REACTIVATED_SUPPLIER", "NEW_SUPPLIER", "NEAR_DUPLICATE_INVOICE"));
            case "NEAR_DUPLICATE_INVOICE" -> Map.of("windowSeconds", 2592000, "historyEligibility", "VALIDATED_SUBMISSIONS", "invoiceNumberNormalization", "EXACT");
            default -> Map.of("windowSeconds", 2592000);
        };
    }
    static List<RiskRule> rules() {
        var codes = new TreeSet<>(DraftRiskRuleRequest.RULE_CODES);
        var rules = new ArrayList<RiskRule>();
        for (var code : codes) rules.add(rule(code, rules.size() + 1, 1, true));
        return rules;
    }
    static List<RiskBand> bands() {
        return List.of(new RiskBand(1, 7, "LOW", 0, 0, 1), new RiskBand(2, 7, "MEDIUM", 1, 1, 2),
                new RiskBand(3, 7, "HIGH", 2, null, 3));
    }
    static ApprovalThreshold threshold(int level, String min, String max) {
        return new ApprovalThreshold(level, 7, level, new BigDecimal(min), max == null ? null : new BigDecimal(max));
    }
    static List<ApprovalThreshold> thresholds() {
        return List.of(threshold(1, "0.00", "0.10"), threshold(2, "0.10", "0.20"), threshold(3, "0.20", null));
    }
    static List<ApprovalSla> slas() {
        return List.of(sla(1), sla(2), sla(3));
    }
    static ApprovalSla sla(int level) { return new ApprovalSla(level, 7, level, 1, "APPROVAL_PENDING_V1", level < 3 ? "NEXT_LEVEL_V1" : "SAME_LEVEL_BACKUP_ONCE_V1", level < 3 ? null : "MANUAL_REVIEW_V1"); }
}
