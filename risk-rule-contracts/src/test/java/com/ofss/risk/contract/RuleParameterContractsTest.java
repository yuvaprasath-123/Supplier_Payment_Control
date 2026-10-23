package com.ofss.risk.contract;

import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleParameterContractsTest {
    private static Map<String, Object> frequency(Object window, Object count) {
        return Map.of("windowSeconds", window, "maximumSubmissions", count, "historyEligibility", "VALIDATED_SUBMISSIONS");
    }
    @Test void exactTypedContractsDoNotRound() {
        var p = (RuleParameters.Amount) RuleParameterContracts.parse(RuleCode.UNUSUAL_AMOUNT, RuleParameterContracts.VERSION,
                Map.of("windowSeconds", new BigInteger("7776000"), "minimumObservations", 5,
                        "meanMultiplier", "2.0000000000000001", "zeroBaselinePolicy", "UNRESOLVED"));
        assertEquals(new BigDecimal("2.0000000000000001"), p.meanMultiplier());
        assertFalse(RuleParameterContracts.unresolved(p).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "3155760001", "9223372036854775808"})
    void invalidWindowsFail(String value) {
        assertThrows(IllegalArgumentException.class, () -> RuleParameterContracts.parse(RuleCode.UNUSUAL_FREQUENCY,
                RuleParameterContracts.VERSION, frequency(new BigInteger(value), 3)));
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "2147483648"})
    void invalidCountsFail(String value) {
        assertThrows(IllegalArgumentException.class, () -> RuleParameterContracts.parse(RuleCode.UNUSUAL_FREQUENCY,
                RuleParameterContracts.VERSION, frequency(1, new BigInteger(value))));
    }
    @Test void integerCoercionMissingUnknownFieldsAndVersionsAreRejected() {
        for (Object value : List.of(3.0, new BigDecimal("3.0"), "3", true)) {
            assertThrows(IllegalArgumentException.class, () -> RuleParameterContracts.parse(RuleCode.UNUSUAL_FREQUENCY,
                    RuleParameterContracts.VERSION, frequency(1, value)));
        }
        assertThrows(IllegalArgumentException.class, () -> RuleParameterContracts.parse(RuleCode.NEW_SUPPLIER, "unknown", Map.of("windowSeconds", 1)));
        assertThrows(IllegalArgumentException.class, () -> RuleParameterContracts.parse(RuleCode.NEW_SUPPLIER, RuleParameterContracts.VERSION, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> RuleParameterContracts.parse(RuleCode.NEW_SUPPLIER, RuleParameterContracts.VERSION, Map.of("windowSeconds", 1, "extra", true)));
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "2e0", "NaN", "2.12345678901234567", "10000000000000"})
    void invalidMultipliersFail(String multiplier) {
        assertThrows(IllegalArgumentException.class, () -> RuleParameterContracts.parse(RuleCode.UNUSUAL_AMOUNT, RuleParameterContracts.VERSION,
                Map.of("windowSeconds", 1, "minimumObservations", 5, "meanMultiplier", multiplier, "zeroBaselinePolicy", "UNRESOLVED")));
    }
    @Test void recursiveFlagsAndInvalidBoundarySelectionsFail() {
        assertThrows(IllegalArgumentException.class, () -> new RuleParameters.PreviousFlags(1, 1, List.of(RuleCode.PREVIOUS_RISK_FLAGS)));
        assertThrows(IllegalArgumentException.class, () -> new RuleParameters.PreviousFlags(1, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RuleParameters.Structuring(1, 2, List.of(1), RuleParameters.HistoryEligibility.VALIDATED_SUBMISSIONS));
        assertThrows(IllegalArgumentException.class, () -> new RuleParameters.Structuring(1, 2, List.of(2, 2), RuleParameters.HistoryEligibility.VALIDATED_SUBMISSIONS));
    }
    @Test void listsAreDefensivelyCopiedAndUnresolvedChoicesStayExplicit() {
        var levels = new ArrayList<>(List.of(2));
        var p = new RuleParameters.Structuring(1, 2, levels, RuleParameters.HistoryEligibility.UNRESOLVED);
        levels.clear(); assertEquals(List.of(2), p.approvalBoundaryLevels());
        assertThrows(UnsupportedOperationException.class, () -> p.approvalBoundaryLevels().add(3));
        var near = new RuleParameters.NearDuplicate(1, RuleParameters.HistoryEligibility.UNRESOLVED, RuleParameters.InvoiceNumberNormalization.UNRESOLVED);
        assertEquals(2, RuleParameterContracts.unresolved(near).size());
    }
}
