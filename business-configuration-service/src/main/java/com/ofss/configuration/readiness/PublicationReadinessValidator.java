package com.ofss.configuration.readiness;

import com.ofss.configuration.band.RiskBand;
import com.ofss.configuration.rule.ConfigurationVersionDetails;
import com.ofss.configuration.rule.DraftRiskRuleRequest;
import com.ofss.configuration.rule.RiskRule;
import com.ofss.configuration.sla.ApprovalSla;
import com.ofss.configuration.threshold.ApprovalThreshold;
import com.ofss.risk.contract.RuleCode;
import com.ofss.risk.contract.RuleParameterContracts;
import com.ofss.risk.contract.ApprovalPolicyContracts;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.ofss.configuration.readiness.PublicationReadiness.Category.*;

@Component
public class PublicationReadinessValidator {
    private static final List<String> BAND_CODES = List.of("LOW", "MEDIUM", "HIGH");
    private static final Set<Integer> LEVELS = Set.of(1, 2, 3);
    private static final BigDecimal MONEY_LIMIT = new BigDecimal("9999999999999.99");

    public PublicationReadiness validate(ConfigurationVersionDetails parent, List<RiskRule> rules,
            List<RiskBand> bands, List<ApprovalThreshold> thresholds, List<ApprovalSla> slas) {
        var issues = new ArrayList<PublicationReadiness.Issue>();
        Integer maximum = rulesAndScore(parent, rules, issues);
        bands(bands, maximum, issues);
        thresholds(thresholds, issues);
        slas(slas, issues);
        issues.sort(Comparator.comparing((PublicationReadiness.Issue issue) -> issue.category().ordinal())
                .thenComparing(PublicationReadiness.Issue::path).thenComparing(PublicationReadiness.Issue::code));
        boolean structural = issues.stream().noneMatch(issue -> issue.category() == CONFIGURATION_ERROR);
        return new PublicationReadiness(Long.toString(parent.version().versionId()), Long.toString(parent.rowVersion()),
                parent.version().publicationState(), parent.version().isDemonstration(), structural,
                issues.isEmpty(), List.copyOf(issues));
    }

    private Integer rulesAndScore(ConfigurationVersionDetails parent, List<RiskRule> rules,
            List<PublicationReadiness.Issue> issues) {
        if (rules.isEmpty()) error(issues, "SECTION_MISSING", "/risk-rules", "Risk-rule configuration is missing");
        var seen = new HashSet<String>();
        var orders = new HashSet<Integer>();
        BigInteger aggregate = BigInteger.ZERO;
        for (var rule : rules.stream().sorted(Comparator.comparing(RiskRule::ruleCode).thenComparingLong(RiskRule::ruleConfigId)).toList()) {
            String path = "/risk-rules/" + rule.ruleCode();
            if (!DraftRiskRuleRequest.RULE_CODES.contains(rule.ruleCode())) {
                error(issues, "UNSUPPORTED_RULE_CODE", path, "Rule is outside the supported inventory");
            }
            if (!seen.add(rule.ruleCode())) error(issues, "DUPLICATE_RULE_CODE", path, "Rule code must be unique within a version");
            if (!text(rule.displayName(), 120) || !text(rule.parameterSchemaVersion(), 32)) {
                error(issues, "INVALID_RULE_STRUCTURE", path, "Rule name or parameter schema is invalid");
            }
            if (rule.parameters() == null) error(issues, "INVALID_RULE_PARAMETER_OBJECT", path + "/parameters", "Rule parameters must be a JSON object");
            if (DraftRiskRuleRequest.RULE_CODES.contains(rule.ruleCode()) && rule.parameters() != null) {
                try {
                    var typed = RuleParameterContracts.parse(RuleCode.valueOf(rule.ruleCode()), rule.parameterSchemaVersion(), rule.parameters());
                    {
                        var unresolved = RuleParameterContracts.unresolved(typed);
                        if (!unresolved.isEmpty()) blocker(issues, "RULE_POLICY_UNRESOLVED", path + "/parameters", String.join("; ", unresolved));
                    }
                } catch (IllegalArgumentException exception) {
                    error(issues, "INVALID_RULE_PARAMETER_CONTRACT", path + "/parameters", exception.getMessage());
                }
            }
            if (rule.weight() < 0 || rule.evaluationOrder() < 1 || rule.evaluationOrder() > 99999) {
                error(issues, "INVALID_RULE_LIMITS", path, "Rule weight or evaluation order is outside the supported limits");
            }
            if (!orders.add(rule.evaluationOrder())) error(issues, "DUPLICATE_RULE_ORDER", path, "Evaluation order must be unique within a version");
            if (rule.enabled() && rule.weight() >= 0) aggregate = aggregate.add(BigInteger.valueOf(rule.weight()));
        }
        for (String code : new TreeSet<>(DraftRiskRuleRequest.RULE_CODES)) {
            if (!seen.contains(code)) error(issues, "REQUIRED_RULE_MISSING", "/risk-rules/" + code, "Required supported rule entry is missing");
        }
        if ("SUM".equals(parent.scorePolicyCode())) {
            if (parent.maxFinalScore() != null) {
                error(issues, "INVALID_SCORE_POLICY", "/score-policy", "SUM must not define a maximum final score");
            }
            if (aggregate.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                error(issues, "AGGREGATE_SCORE_OVERFLOW", "/score-policy", "Enabled weight sum exceeds the supported final-score range");
                return null;
            }
            if (aggregate.signum() == 0) error(issues, "INVALID_SCORE_POLICY", "/score-policy", "SUM requires a positive supported score maximum");
            return aggregate.intValueExact();
        }
        if ("CAP_AT_MAX".equals(parent.scorePolicyCode()) && parent.maxFinalScore() != null && parent.maxFinalScore() > 0) {
            return parent.maxFinalScore();
        }
        error(issues, "INVALID_SCORE_POLICY", "/score-policy", "Score policy or required positive cap is invalid");
        return null;
    }

    private void bands(List<RiskBand> bands, Integer maximum, List<PublicationReadiness.Issue> issues) {
        if (bands.isEmpty()) error(issues, "SECTION_MISSING", "/risk-bands", "Risk-band configuration is missing");
        var byCode = new HashMap<String, RiskBand>();
        for (var band : bands.stream().sorted(Comparator.comparing(RiskBand::bandCode).thenComparingLong(RiskBand::bandConfigId)).toList()) {
            String path = "/risk-bands/" + band.bandCode();
            if (!BAND_CODES.contains(band.bandCode())) error(issues, "UNSUPPORTED_BAND_CODE", path, "Unsupported risk band");
            if (byCode.putIfAbsent(band.bandCode(), band) != null) error(issues, "DUPLICATE_BAND_CODE", path, "Band code must be unique within a version");
            if (BAND_CODES.contains(band.bandCode()) && !Objects.equals(ApprovalPolicyContracts.BAND_LEVELS.get(band.bandCode()), band.approvalLevel()))
                error(issues, "INVALID_AUTHORITY_MAPPING", path, "approval-policy-v1 requires LOW/L1, MEDIUM/L2, HIGH/L3");
            if (band.minScoreInclusive() < 0 || (band.maxScoreInclusive() != null && band.maxScoreInclusive() < band.minScoreInclusive())
                    || !LEVELS.contains(band.approvalLevel())) {
                error(issues, "INVALID_BAND_STRUCTURE", path, "Band bounds or approval level are invalid");
            }
        }
        for (String code : BAND_CODES) {
            if (!byCode.containsKey(code)) error(issues, "REQUIRED_BAND_MISSING", "/risk-bands/" + code, "Required risk band is missing");
        }
        if (!byCode.keySet().containsAll(BAND_CODES)) return;
        var low = byCode.get("LOW");
        if (low.minScoreInclusive() != 0) error(issues, "BAND_START_NOT_ZERO", "/risk-bands/LOW", "Integer score coverage must start at zero");
        for (int index = 0; index < 2; index++) {
            var current = byCode.get(BAND_CODES.get(index));
            var next = byCode.get(BAND_CODES.get(index + 1));
            String path = "/risk-bands/" + current.bandCode();
            if (current.maxScoreInclusive() == null) {
                error(issues, "NONFINAL_BAND_UNBOUNDED", path, "Only HIGH may have an unbounded maximum");
            } else {
                long expected = (long) current.maxScoreInclusive() + 1;
                if (next.minScoreInclusive() > expected) error(issues, "BAND_GAP", path, "Adjacent integer bands leave a gap");
                if (next.minScoreInclusive() < expected) error(issues, "BAND_OVERLAP", path, "Adjacent integer bands overlap or are out of order");
            }
        }
        var high = byCode.get("HIGH");
        if (maximum != null && high.maxScoreInclusive() != null && high.maxScoreInclusive() < maximum) {
            error(issues, "BAND_COVERAGE_INCOMPLETE", "/risk-bands/HIGH", "HIGH does not cover the configured score upper bound");
        }
    }

    private void thresholds(List<ApprovalThreshold> thresholds, List<PublicationReadiness.Issue> issues) {
        if (thresholds.isEmpty()) error(issues, "SECTION_MISSING", "/approval-thresholds", "Approval-threshold configuration is missing");
        var byLevel = new HashMap<Integer, ApprovalThreshold>();
        for (var threshold : thresholds.stream().sorted(Comparator.comparingInt(ApprovalThreshold::approvalLevel)
                .thenComparingLong(ApprovalThreshold::thresholdConfigId)).toList()) {
            String path = "/approval-thresholds/" + threshold.approvalLevel();
            if (!LEVELS.contains(threshold.approvalLevel())) error(issues, "UNSUPPORTED_APPROVAL_LEVEL", path, "Approval level must be 1, 2 or 3");
            if (byLevel.putIfAbsent(threshold.approvalLevel(), threshold) != null) error(issues, "DUPLICATE_THRESHOLD_LEVEL", path, "Threshold level must be unique within a version");
            if (!money(threshold.minAmountInclusive()) || (threshold.maxAmountExclusive() != null
                    && (!money(threshold.maxAmountExclusive()) || threshold.minAmountInclusive() == null
                    || threshold.maxAmountExclusive().compareTo(threshold.minAmountInclusive()) <= 0))) {
                error(issues, "INVALID_THRESHOLD_BOUNDS", path, "Amounts must fit NUMBER(15,2) and the maximum must exceed the minimum");
            }
        }
        for (int level = 1; level <= 3; level++) {
            if (!byLevel.containsKey(level)) error(issues, "REQUIRED_THRESHOLD_MISSING", "/approval-thresholds/" + level, "Required approval threshold is missing");
        }
        if (!byLevel.keySet().containsAll(LEVELS)) return;
        var first = byLevel.get(1);
        if (first.minAmountInclusive() != null && first.minAmountInclusive().compareTo(BigDecimal.ZERO) != 0) {
            error(issues, "THRESHOLD_START_NOT_ZERO", "/approval-thresholds/1", "Amount coverage must start at zero");
        }
        for (int level = 1; level < 3; level++) {
            var current = byLevel.get(level);
            var next = byLevel.get(level + 1);
            String path = "/approval-thresholds/" + level;
            if (current.maxAmountExclusive() == null) error(issues, "NONFINAL_THRESHOLD_UNBOUNDED", path, "Only level 3 may be unbounded");
            else if (next.minAmountInclusive() != null) {
                int comparison = next.minAmountInclusive().compareTo(current.maxAmountExclusive());
                if (comparison > 0) error(issues, "THRESHOLD_GAP", path, "Adjacent exclusive amount tiers leave a gap");
                if (comparison < 0) error(issues, "THRESHOLD_OVERLAP", path, "Adjacent amount tiers overlap or are out of order");
            }
        }
        if (byLevel.get(3).maxAmountExclusive() != null) {
            error(issues, "FINAL_THRESHOLD_NOT_UNBOUNDED", "/approval-thresholds/3", "Level 3 must have an unbounded maximum");
        }
    }

    private void slas(List<ApprovalSla> slas, List<PublicationReadiness.Issue> issues) {
        if (slas.isEmpty()) error(issues, "SECTION_MISSING", "/approval-slas", "Approval-SLA configuration is missing");
        var seen = new HashSet<Integer>();
        for (var sla : slas.stream().sorted(Comparator.comparingInt(ApprovalSla::approvalLevel).thenComparingLong(ApprovalSla::slaConfigId)).toList()) {
            String path = "/approval-slas/" + sla.approvalLevel();
            if (!LEVELS.contains(sla.approvalLevel())) error(issues, "UNSUPPORTED_APPROVAL_LEVEL", path, "Approval level must be 1, 2 or 3");
            if (!seen.add(sla.approvalLevel())) error(issues, "DUPLICATE_SLA_LEVEL", path, "SLA level must be unique within a version");
            if (sla.durationSeconds() < 1 || sla.durationSeconds() > 999999999999L
                    || !text(sla.startEventCode(), 32) || !text(sla.escalationPolicyCode(), 32)
                    || (sla.terminalLevelPolicyCode() != null && !text(sla.terminalLevelPolicyCode(), 32))) {
                error(issues, "INVALID_SLA_STRUCTURE", path, "SLA duration or policy text is invalid");
            }
            try {
                ApprovalPolicyContracts.validateSla(new ApprovalPolicyContracts.Sla(sla.approvalLevel(), sla.durationSeconds(),
                        sla.startEventCode(), sla.escalationPolicyCode(), sla.terminalLevelPolicyCode()));
            } catch (IllegalArgumentException exception) {
                error(issues, "INVALID_APPROVAL_POLICY_CONTRACT", path, exception.getMessage());
            }
        }
        for (int level = 1; level <= 3; level++) {
            if (!seen.contains(level)) error(issues, "REQUIRED_SLA_MISSING", "/approval-slas/" + level, "Required approval SLA is missing");
        }
    }

    private boolean money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(MONEY_LIMIT) > 0) return false;
        try { value.setScale(2, RoundingMode.UNNECESSARY); return true; }
        catch (ArithmeticException exception) { return false; }
    }
    private boolean text(String value, int limit) {
        return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= limit
                && value.codePoints().noneMatch(point -> point >= 0xD800 && point <= 0xDFFF);
    }
    private void error(List<PublicationReadiness.Issue> issues, String code, String path, String message) {
        issues.add(new PublicationReadiness.Issue(CONFIGURATION_ERROR, code, path, message));
    }
    private void blocker(List<PublicationReadiness.Issue> issues, String code, String path, String message) {
        issues.add(new PublicationReadiness.Issue(POLICY_BLOCKER, code, path, message));
    }
}
