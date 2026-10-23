package com.ofss.configuration.readiness;

import com.ofss.configuration.rule.RiskRule;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.ofss.configuration.readiness.ReadinessFixtures.*;
import static org.assertj.core.api.Assertions.*;

class TypedRuleReadinessTest {
    private PublicationReadiness check(List<RiskRule> rules) {
        return new PublicationReadinessValidator().validate(parent("CAP_AT_MAX", 100, true), rules, bands(), thresholds(), slas());
    }
    private List<RiskRule> replace(String code, String schema, Map<String, Object> parameters, boolean enabled) {
        return rules().stream().map(r -> r.ruleCode().equals(code) ? new RiskRule(r.ruleConfigId(), r.versionId(), code, r.displayName(),
                enabled, r.weight(), schema, parameters, r.evaluationOrder()) : r).toList();
    }
    @ParameterizedTest @ValueSource(strings = {"STRUCTURING", "UNUSUAL_FREQUENCY", "UNUSUAL_AMOUNT", "RECENTLY_REACTIVATED_SUPPLIER", "PREVIOUS_RISK_FLAGS", "NEW_SUPPLIER", "NEAR_DUPLICATE_INVOICE"})
    void incompleteContractsAreErrorsForEveryRuleEvenWhenDisabled(String code) {
        var result = check(replace(code, "risk-policy-v1", Map.of(), false));
        assertThat(result.issues()).anyMatch(i -> i.code().equals("INVALID_RULE_PARAMETER_CONTRACT") && i.path().contains(code));
        assertThat(result.structurallyValid()).isFalse(); assertThat(result.ready()).isFalse();
    }
    @Test void validTypedContractsAndApprovalContractsAreReady() {
        var result = check(rules());
        assertThat(result.structurallyValid()).isTrue(); assertThat(result.ready()).isTrue();
        assertThat(result.issues()).noneMatch(i -> i.code().equals("RULE_PARAMETER_CONTRACTS_UNRESOLVED") || i.code().equals("SCORING_CONTRACT_UNRESOLVED"));
        assertThat(result.issues()).isEmpty();
    }
    @Test void unresolvedNearDuplicateAndZeroBaselineRemainSpecificPolicyBlockers() {
        var near = new HashMap<>(parameters("NEAR_DUPLICATE_INVOICE"));
        near.put("historyEligibility", "UNRESOLVED"); near.put("invoiceNumberNormalization", "UNRESOLVED");
        var result = check(replace("NEAR_DUPLICATE_INVOICE", "risk-policy-v1", near, true));
        assertThat(result.issues()).anyMatch(i -> i.code().equals("RULE_POLICY_UNRESOLVED") && i.path().contains("NEAR_DUPLICATE_INVOICE"));
        var amount = new HashMap<>(parameters("UNUSUAL_AMOUNT")); amount.put("zeroBaselinePolicy", "UNRESOLVED");
        assertThat(check(replace("UNUSUAL_AMOUNT", "risk-policy-v1", amount, true)).issues()).anyMatch(i -> i.code().equals("RULE_POLICY_UNRESOLVED"));
        assertThat(check(replace("NEAR_DUPLICATE_INVOICE", "risk-policy-v1", near, false)).issues()).anyMatch(i -> i.code().equals("RULE_POLICY_UNRESOLVED"));
    }
    @Test void arbitrarySchemasAndRecursivePreviousFlagsCannotPass() {
        assertThat(check(replace("NEW_SUPPLIER", "made-up-approved", parameters("NEW_SUPPLIER"), true)).structurallyValid()).isFalse();
        var previous = new HashMap<>(parameters("PREVIOUS_RISK_FLAGS")); previous.put("qualifyingRuleCodes", List.of("PREVIOUS_RISK_FLAGS"));
        assertThat(check(replace("PREVIOUS_RISK_FLAGS", "risk-policy-v1", previous, true)).structurallyValid()).isFalse();
    }
}
