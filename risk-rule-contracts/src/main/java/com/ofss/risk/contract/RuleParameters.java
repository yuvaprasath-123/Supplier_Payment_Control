package com.ofss.risk.contract;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Immutable v1 contracts. UNRESOLVED is explicit policy data, never a scoring default. */
public sealed interface RuleParameters {
    long windowSeconds();
    enum HistoryEligibility { UNRESOLVED, VALIDATED_SUBMISSIONS, COMPLETED_PAYMENTS }
    enum ZeroBaselinePolicy { UNRESOLVED, INSUFFICIENT_HISTORY, COMPARE_TO_ZERO }
    enum InvoiceNumberNormalization { UNRESOLVED, EXACT, TRIM_UPPERCASE }

    record Structuring(long windowSeconds, int minimumPayments, List<Integer> approvalBoundaryLevels,
            HistoryEligibility historyEligibility) implements RuleParameters {
        public Structuring {
            window(windowSeconds); positive(minimumPayments);
            if (minimumPayments < 2) throw new IllegalArgumentException("minimumPayments must be at least two");
            approvalBoundaryLevels = List.copyOf(approvalBoundaryLevels);
            if (approvalBoundaryLevels.isEmpty() || approvalBoundaryLevels.stream().anyMatch(l -> l < 2 || l > 3)
                    || approvalBoundaryLevels.stream().distinct().count() != approvalBoundaryLevels.size())
                throw new IllegalArgumentException("approvalBoundaryLevels must contain distinct levels 2 or 3");
            Objects.requireNonNull(historyEligibility);
        }
    }
    record Frequency(long windowSeconds, int maximumSubmissions, HistoryEligibility historyEligibility) implements RuleParameters {
        public Frequency { window(windowSeconds); positive(maximumSubmissions); Objects.requireNonNull(historyEligibility); }
    }
    record Amount(long windowSeconds, int minimumObservations, BigDecimal meanMultiplier,
            ZeroBaselinePolicy zeroBaselinePolicy) implements RuleParameters {
        public Amount {
            window(windowSeconds); positive(minimumObservations); Objects.requireNonNull(meanMultiplier);
            if (meanMultiplier.signum() <= 0) throw new IllegalArgumentException("meanMultiplier must be positive");
            var exact = meanMultiplier.stripTrailingZeros();
            if (exact.scale() > 16 || (long) exact.precision() - exact.scale() > 13)
                throw new IllegalArgumentException("meanMultiplier exceeds 13 integer/16 fractional digits");
            Objects.requireNonNull(zeroBaselinePolicy);
        }
    }
    record Reactivation(long windowSeconds) implements RuleParameters { public Reactivation { window(windowSeconds); } }
    record PreviousFlags(long windowSeconds, int minimumFlaggedPayments, List<RuleCode> qualifyingRuleCodes) implements RuleParameters {
        public PreviousFlags {
            window(windowSeconds); positive(minimumFlaggedPayments); qualifyingRuleCodes = List.copyOf(qualifyingRuleCodes);
            if (qualifyingRuleCodes.isEmpty() || qualifyingRuleCodes.contains(RuleCode.PREVIOUS_RISK_FLAGS)
                    || qualifyingRuleCodes.stream().distinct().count() != qualifyingRuleCodes.size())
                throw new IllegalArgumentException("qualifyingRuleCodes must be distinct and exclude PREVIOUS_RISK_FLAGS");
        }
    }
    record NewSupplier(long windowSeconds) implements RuleParameters { public NewSupplier { window(windowSeconds); } }
    record NearDuplicate(long windowSeconds, HistoryEligibility historyEligibility,
            InvoiceNumberNormalization invoiceNumberNormalization) implements RuleParameters {
        public NearDuplicate { window(windowSeconds); Objects.requireNonNull(historyEligibility); Objects.requireNonNull(invoiceNumberNormalization); }
    }
    private static void window(long value) {
        // One contractual, documented range prevents Duration/Instant overflow and unbounded evidence requests.
        if (value < 1 || value > 3155760000L) throw new IllegalArgumentException("windowSeconds must be 1..3155760000 (100 years)");
    }
    private static void positive(int value) { if (value < 1) throw new IllegalArgumentException("count must be positive"); }
}
