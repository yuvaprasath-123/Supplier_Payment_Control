package com.ofss.configuration.readiness;

import com.ofss.configuration.band.RiskBand;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.ApprovalSla;
import com.ofss.configuration.threshold.ApprovalThreshold;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.ofss.configuration.readiness.ReadinessFixtures.*;
import static com.ofss.configuration.readiness.PublicationReadiness.Category.*;

class PublicationReadinessValidatorTest {
    private final PublicationReadinessValidator validator = new PublicationReadinessValidator();
    private PublicationReadiness check(ConfigurationVersionDetails parent, List<RiskRule> rules, List<RiskBand> bands,
            List<ApprovalThreshold> thresholds, List<ApprovalSla> slas) {
        return validator.validate(parent, rules, bands, thresholds, slas);
    }
    private void error(PublicationReadiness result, String code) {
        assertThat(result.ready()).isFalse();
        assertThat(result.structurallyValid()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.category() == CONFIGURATION_ERROR && issue.code().equals(code));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void completeSupportedPoliciesAreReadyWithoutUpstreamAvailabilityCheck(boolean demo) {
        var result = check(parent("SUM", null, demo), rules(), bands(), thresholds(), slas());
        assertThat(result.structurallyValid()).isTrue(); assertThat(result.ready()).isTrue();
        assertThat(result.isDemonstration()).isEqualTo(demo); assertThat(result.issues()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"RULES", "BANDS", "THRESHOLDS", "SLAS"})
    void absentSectionsAndTheirRequiredEntriesAreErrors(String section) {
        var result = check(parent("SUM", null, true), section.equals("RULES") ? List.of() : rules(),
                section.equals("BANDS") ? List.of() : bands(), section.equals("THRESHOLDS") ? List.of() : thresholds(),
                section.equals("SLAS") ? List.of() : slas());
        error(result, "SECTION_MISSING");
        error(result, "REQUIRED_" + switch (section) { case "RULES" -> "RULE"; case "BANDS" -> "BAND"; case "THRESHOLDS" -> "THRESHOLD"; default -> "SLA"; } + "_MISSING");
    }
    @ParameterizedTest @ValueSource(strings = {"STRUCTURING", "UNUSUAL_FREQUENCY", "UNUSUAL_AMOUNT", "RECENTLY_REACTIVATED_SUPPLIER", "PREVIOUS_RISK_FLAGS", "NEW_SUPPLIER", "NEAR_DUPLICATE_INVOICE"})
    void everySupportedFactorRequiresAnEntry(String code) {
        error(check(parent("SUM", null, true), rules().stream().filter(rule -> !rule.ruleCode().equals(code)).toList(),
                bands(), thresholds(), slas()), "REQUIRED_RULE_MISSING");
    }
    @Test void oldAmbiguousNearDuplicateCodeIsStillUnsupported() {
        var rules = new ArrayList<>(rules());
        rules.add(rule("NEAR_DUPLICATE", 7, 1, false));
        error(check(parent("SUM", null, true), rules, bands(), thresholds(), slas()), "UNSUPPORTED_RULE_CODE");
    }
    @Test void sumOverflowIsDetectedExactlyWhileCapBoundsFinalScore() {
        var large = rules().stream().map(rule -> rule(rule.ruleCode(), rule.evaluationOrder(), Integer.MAX_VALUE, true)).toList();
        error(check(parent("SUM", null, true), large, bands(), thresholds(), slas()), "AGGREGATE_SCORE_OVERFLOW");
        var capped = check(parent("CAP_AT_MAX", 100, true), large, bands(), thresholds(), slas());
        assertThat(capped.structurallyValid()).isTrue();
        assertThat(capped.ready()).isTrue();
    }
    @Test void disabledWeightsDoNotInflateSumAndArbitraryJsonCannotApproveSemantics() {
        var rules = rules().stream().map(rule -> new RiskRule(rule.ruleConfigId(), 7, rule.ruleCode(), "Test", rule.evaluationOrder() == 1,
                rule.evaluationOrder() == 1 ? 5 : Integer.MAX_VALUE, "made-up-approved", Map.of("approved", true), rule.evaluationOrder())).toList();
        var result = check(parent("SUM", null, false), rules, bands(), thresholds(), slas());
        assertThat(result.structurallyValid()).isFalse();
        assertThat(result.ready()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("INVALID_RULE_PARAMETER_CONTRACT"));
    }
    @ParameterizedTest @ValueSource(strings = {"UNKNOWN", "SUM_WITH_CAP", "CAP_NULL", "CAP_ZERO", "CAP_NEGATIVE"})
    void invalidScorePolicyIsAnError(String scenario) {
        var parent = switch (scenario) {
            case "SUM_WITH_CAP" -> parent("SUM", 1, true);
            case "CAP_NULL" -> parent("CAP_AT_MAX", null, true);
            case "CAP_ZERO" -> parent("CAP_AT_MAX", 0, true);
            case "CAP_NEGATIVE" -> parent("CAP_AT_MAX", -1, true);
            default -> parent("UNKNOWN", null, true);
        };
        error(check(parent, rules(), bands(), thresholds(), slas()), "INVALID_SCORE_POLICY");
    }
    @ParameterizedTest @ValueSource(strings = {"BAND_START_NOT_ZERO", "BAND_GAP", "BAND_OVERLAP", "NONFINAL_BAND_UNBOUNDED",
            "BAND_COVERAGE_INCOMPLETE", "INVALID_BAND_STRUCTURE", "REQUIRED_BAND_MISSING"})
    void integerCoverageChecksUseInclusiveEndpointsAndConfiguredMaximum(String code) {
        var bands = new ArrayList<>(bands());
        switch (code) {
            case "BAND_START_NOT_ZERO" -> bands.set(0, new RiskBand(1, 7, "LOW", 1, 1, 1));
            case "BAND_GAP" -> bands.set(1, new RiskBand(2, 7, "MEDIUM", 2, 2, 1));
            case "BAND_OVERLAP" -> bands.set(1, new RiskBand(2, 7, "MEDIUM", 0, 1, 1));
            case "NONFINAL_BAND_UNBOUNDED" -> bands.set(0, new RiskBand(1, 7, "LOW", 0, null, 1));
            case "BAND_COVERAGE_INCOMPLETE" -> bands.set(2, new RiskBand(3, 7, "HIGH", 2, 4, 1));
            case "INVALID_BAND_STRUCTURE" -> bands.set(2, new RiskBand(3, 7, "HIGH", 2, 1, 4));
            case "REQUIRED_BAND_MISSING" -> bands.remove(2);
        }
        error(check(parent("SUM", null, true), rules(), bands, thresholds(), slas()), code);
    }
    @Test void capRatherThanRawSumDeterminesRequiredBandCoverage() {
        var finite = new ArrayList<>(bands());
        finite.set(2, new RiskBand(3, 7, "HIGH", 2, 7, 3));
        assertThat(check(parent("SUM", null, true), rules(), finite, thresholds(), slas()).structurallyValid()).isTrue();
        error(check(parent("CAP_AT_MAX", 8, true), rules(), finite, thresholds(), slas()), "BAND_COVERAGE_INCOMPLETE");
    }
    @ParameterizedTest @ValueSource(strings = {"SUM_SMALL", "CAP_SMALL"})
    void coverageDoesNotRequireEveryBandToBeReachable(String scenario) {
        var rules = rules().stream().map(rule -> rule(rule.ruleCode(), rule.evaluationOrder(), 1, !scenario.equals("SUM_ZERO"))).toList();
        var bands = List.of(new RiskBand(1, 7, "LOW", 0, 9, 1), new RiskBand(2, 7, "MEDIUM", 10, 19, 2),
                new RiskBand(3, 7, "HIGH", 20, null, 3));
        var result = check(parent(scenario.equals("CAP_SMALL") ? "CAP_AT_MAX" : "SUM", scenario.equals("CAP_SMALL") ? 1 : null, true),
                rules, bands, thresholds(), slas());
        assertThat(result.structurallyValid()).isTrue();
        assertThat(result.ready()).isTrue();
        assertThat(result.issues()).isEmpty();
    }
    @Test void integerMaxBoundaryDoesNotOverflowCoverageArithmetic() {
        var bands = List.of(new RiskBand(1, 7, "LOW", 0, Integer.MAX_VALUE, 1),
                new RiskBand(2, 7, "MEDIUM", Integer.MAX_VALUE, Integer.MAX_VALUE, 2),
                new RiskBand(3, 7, "HIGH", Integer.MAX_VALUE, null, 3));
        error(check(parent("CAP_AT_MAX", Integer.MAX_VALUE, true), rules(), bands, thresholds(), slas()), "BAND_OVERLAP");
    }
    @ParameterizedTest @ValueSource(strings = {"THRESHOLD_START_NOT_ZERO", "THRESHOLD_GAP", "THRESHOLD_OVERLAP",
            "NONFINAL_THRESHOLD_UNBOUNDED", "FINAL_THRESHOLD_NOT_UNBOUNDED", "INVALID_THRESHOLD_BOUNDS", "REQUIRED_THRESHOLD_MISSING"})
    void exactAmountTiersUseExclusiveMaximumWithoutEpsilon(String code) {
        var tiers = new ArrayList<>(thresholds());
        switch (code) {
            case "THRESHOLD_START_NOT_ZERO" -> tiers.set(0, threshold(1, "0.01", "0.10"));
            case "THRESHOLD_GAP" -> tiers.set(1, threshold(2, "0.11", "0.20"));
            case "THRESHOLD_OVERLAP" -> tiers.set(1, threshold(2, "0.09", "0.20"));
            case "NONFINAL_THRESHOLD_UNBOUNDED" -> tiers.set(0, threshold(1, "0", null));
            case "FINAL_THRESHOLD_NOT_UNBOUNDED" -> tiers.set(2, threshold(3, "0.20", "0.30"));
            case "INVALID_THRESHOLD_BOUNDS" -> tiers.set(1, threshold(2, "0.10", "0.10"));
            case "REQUIRED_THRESHOLD_MISSING" -> tiers.remove(1);
        }
        error(check(parent("SUM", null, true), rules(), bands(), tiers, slas()), code);
    }
    @ParameterizedTest @ValueSource(strings = {"-0.01", "0.201", "10000000000000.00"})
    void invalidMoneyCannotBeRoundedOrExceedPrecision(String minimum) {
        var tiers = new ArrayList<>(thresholds());
        tiers.set(2, threshold(3, minimum, null));
        error(check(parent("SUM", null, true), rules(), bands(), tiers, slas()), "INVALID_THRESHOLD_BOUNDS");
    }
    @Test void decimalScaleAndLargeExactBoundariesDoNotCreateFalseGaps() {
        var tiers = List.of(threshold(1, "0", "0.100"), threshold(2, "0.10", "9999999999999.99"), threshold(3, "9999999999999.99", null));
        assertThat(check(parent("SUM", null, true), rules(), bands(), tiers, slas()).structurallyValid()).isTrue();
    }
    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void eachSlaLevelIsRequired(int level) {
        error(check(parent("SUM", null, true), rules(), bands(), thresholds(), slas().stream().filter(sla -> sla.approvalLevel() != level).toList()), "REQUIRED_SLA_MISSING");
    }
    @ParameterizedTest @ValueSource(strings = {"ZERO", "OVERFLOW", "START", "ESCALATION", "TERMINAL", "SURROGATE"})
    void slaStructureIsCheckedWithoutInventingPolicyMeaning(String scenario) {
        var slas = new ArrayList<>(slas());
        slas.set(0, new ApprovalSla(1, 7, 1, scenario.equals("ZERO") ? 0 : scenario.equals("OVERFLOW") ? 1000000000000L : 1,
                scenario.equals("START") ? " " : scenario.equals("SURROGATE") ? "\uD800" : "UNAGREED",
                scenario.equals("ESCALATION") ? "A".repeat(33) : "UNAGREED", scenario.equals("TERMINAL") ? "" : null));
        error(check(parent("SUM", null, true), rules(), bands(), thresholds(), slas), "INVALID_SLA_STRUCTURE");
    }
    @Test void duplicateAndInvalidRuleMetadataAreConfigurationErrors() {
        var rules = new ArrayList<>(rules());
        rules.add(new RiskRule(99, 7, "STRUCTURING", "", true, -1, "", null, 1));
        var result = check(parent("SUM", null, true), rules, bands(), thresholds(), slas());
        for (var code : List.of("DUPLICATE_RULE_CODE", "DUPLICATE_RULE_ORDER", "INVALID_RULE_STRUCTURE", "INVALID_RULE_LIMITS")) error(result, code);
    }
    @Test void issuesAreDeterministicAcrossReorderedInputsIncludingDuplicateRows() {
        var rules = new ArrayList<>(rules()); rules.add(rule("OTHER", 1, 1, false));
        var bands = new ArrayList<>(bands()); bands.add(new RiskBand(99, 7, "LOW", 1, 4, 1));
        var tiers = new ArrayList<>(thresholds()); tiers.add(new ApprovalThreshold(99, 7, 1, java.math.BigDecimal.ONE, null));
        var slas = new ArrayList<>(slas()); slas.add(sla(1));
        var result = check(parent("SUM", null, true), rules, bands, tiers, slas);
        for (int seed = 0; seed < 5; seed++) {
            Collections.shuffle(rules, new Random(seed)); Collections.shuffle(bands, new Random(seed));
            Collections.shuffle(tiers, new Random(seed)); Collections.shuffle(slas, new Random(seed));
            assertThat(check(parent("SUM", null, true), rules, bands, tiers, slas)).isEqualTo(result);
        }
        assertThat(result.issues()).isSortedAccordingTo(Comparator.comparing((PublicationReadiness.Issue issue) -> issue.category().ordinal())
                .thenComparing(PublicationReadiness.Issue::path).thenComparing(PublicationReadiness.Issue::code));
    }
}
