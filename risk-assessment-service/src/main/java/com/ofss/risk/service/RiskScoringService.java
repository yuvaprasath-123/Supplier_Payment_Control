package com.ofss.risk.service;

import com.ofss.risk.repository.ConfigurationRepository;
import com.ofss.risk.repository.RiskAssessmentRepository.PaymentRiskData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

@Service
public class RiskScoringService {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public RiskScoringService(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;

        this.jsonMapper =
                JsonMapper.builder()
                        .build();
    }

    public record FactorDecision(
            String ruleCode,
            boolean triggered,
            int configuredWeight,
            int contribution,
            String explanation
    ) {
    }

    public record ScoreResult(
            int riskScore,
            String riskBand,
            int riskApprovalLevel,
            List<FactorDecision> factors
    ) {
    }

    private record PreviousAssessment(
            BigDecimal paymentAmount,
            String invoiceNumber
    ) {
    }

    public ScoreResult calculate(
            PaymentRiskData payment,
            ConfigurationRepository.ActiveConfiguration configuration
    ) {

        List<FactorDecision> factors =
                new ArrayList<>();

        int totalScore = 0;

        LocalDateTime now =
                LocalDateTime.now();

        for (
                ConfigurationRepository.RiskRule rule
                : configuration.rules()
        ) {

            FactorDecision decision;

            if (!rule.enabled()) {

                decision =
                        new FactorDecision(
                                rule.ruleCode(),
                                false,
                                rule.weight(),
                                0,
                                "Rule is disabled"
                        );

            } else {

                Map<String, Object> parameters =
                        readParameters(
                                rule.parametersJson()
                        );

                decision =
                        evaluateRule(
                                rule,
                                payment,
                                configuration,
                                parameters,
                                now
                        );
            }

            factors.add(
                    decision
            );

            totalScore +=
                    decision.contribution();
        }

        int finalScore =
                Math.min(
                        totalScore,
                        configuration.maxFinalScore()
                );

        ConfigurationRepository.RiskBand selectedBand =
                configuration.bands()
                        .stream()
                        .filter(
                                band ->
                                        finalScore
                                                >= band.minScoreInclusive()
                                        &&
                                        (
                                                band.maxScoreInclusive()
                                                        == null
                                                ||
                                                finalScore
                                                        <= band.maxScoreInclusive()
                                        )
                        )
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No risk band configured for score "
                                                        + finalScore
                                        )
                        );

        return new ScoreResult(
                finalScore,
                selectedBand.bandCode(),
                selectedBand.approvalLevel(),
                List.copyOf(
                        factors
                )
        );
    }

    private FactorDecision evaluateRule(
            ConfigurationRepository.RiskRule rule,
            PaymentRiskData payment,
            ConfigurationRepository.ActiveConfiguration configuration,
            Map<String, Object> parameters,
            LocalDateTime now
    ) {

        return switch (rule.ruleCode()) {

            case "STRUCTURING" ->
                    evaluateStructuring(
                            rule,
                            payment,
                            configuration,
                            parameters,
                            now
                    );

            case "UNUSUAL_FREQUENCY" ->
                    evaluateFrequency(
                            rule,
                            payment,
                            parameters,
                            now
                    );

            case "UNUSUAL_AMOUNT" ->
                    evaluateAmount(
                            rule,
                            payment,
                            parameters,
                            now
                    );

            case "RECENTLY_REACTIVATED_SUPPLIER" ->
                    evaluateReactivatedSupplier(
                            rule,
                            payment,
                            parameters,
                            now
                    );

            case "PREVIOUS_RISK_FLAGS" ->
                    evaluatePreviousFlags(
                            rule,
                            payment,
                            parameters,
                            now
                    );

            case "NEW_SUPPLIER" ->
                    evaluateNewSupplier(
                            rule,
                            payment,
                            parameters,
                            now
                    );

            case "NEAR_DUPLICATE_INVOICE" ->
                    evaluateNearDuplicate(
                            rule,
                            payment,
                            parameters,
                            now
                    );

            default ->
                    new FactorDecision(
                            rule.ruleCode(),
                            false,
                            rule.weight(),
                            0,
                            "Unsupported risk rule"
                    );
        };
    }

    private FactorDecision evaluateStructuring(
            ConfigurationRepository.RiskRule rule,
            PaymentRiskData payment,
            ConfigurationRepository.ActiveConfiguration configuration,
            Map<String, Object> parameters,
            LocalDateTime now
    ) {

        long windowSeconds =
                longValue(
                        parameters,
                        "windowSeconds"
                );

        int minimumPayments =
                intValue(
                        parameters,
                        "minimumPayments"
                );

        LocalDateTime since =
                now.minusSeconds(
                        windowSeconds
                );

        List<PreviousAssessment> previous =
                findPreviousAssessments(
                        payment.supplierId(),
                        since
                );

        boolean triggered = false;

        for (
                ConfigurationRepository.ApprovalThreshold threshold
                : configuration.thresholds()
        ) {

            if (
                    threshold.approvalLevel() <= 1
            ) {
                continue;
            }

            BigDecimal boundary =
                    threshold.minAmountInclusive();

            if (
                    payment.paymentAmount()
                            .compareTo(boundary)
                            >= 0
            ) {
                continue;
            }

            List<PreviousAssessment> belowBoundary =
                    previous.stream()
                            .filter(
                                    item ->
                                            item.paymentAmount()
                                                    .compareTo(
                                                            boundary
                                                    ) < 0
                            )
                            .toList();

            if (
                    belowBoundary.size() + 1
                            < minimumPayments
            ) {
                continue;
            }

            BigDecimal combinedAmount =
                    payment.paymentAmount();

            for (
                    PreviousAssessment item
                    : belowBoundary
            ) {

                combinedAmount =
                        combinedAmount.add(
                                item.paymentAmount()
                        );
            }

            if (
                    combinedAmount.compareTo(
                            boundary
                    ) >= 0
            ) {

                triggered = true;
                break;
            }
        }

        return decision(
                rule,
                triggered,
                triggered
                        ? "Multiple lower-value payments together cross an approval threshold"
                        : "No payment structuring pattern detected"
        );
    }

    private FactorDecision evaluateFrequency(
            ConfigurationRepository.RiskRule rule,
            PaymentRiskData payment,
            Map<String, Object> parameters,
            LocalDateTime now
    ) {

        long windowSeconds =
                longValue(
                        parameters,
                        "windowSeconds"
                );

        int maximumSubmissions =
                intValue(
                        parameters,
                        "maximumSubmissions"
                );

        long previousCount =
                countAssessments(
                        payment.supplierId(),
                        now.minusSeconds(
                                windowSeconds
                        )
                );

        boolean triggered =
                previousCount + 1
                        > maximumSubmissions;

        return decision(
                rule,
                triggered,
                triggered
                        ? "Payment frequency exceeds the configured limit"
                        : "Payment frequency is within the configured limit"
        );
    }

    private FactorDecision evaluateAmount(
            ConfigurationRepository.RiskRule rule,
            PaymentRiskData payment,
            Map<String, Object> parameters,
            LocalDateTime now
    ) {

        long windowSeconds =
                longValue(
                        parameters,
                        "windowSeconds"
                );

        int minimumObservations =
                intValue(
                        parameters,
                        "minimumObservations"
                );

        BigDecimal multiplier =
                new BigDecimal(
                        String.valueOf(
                                parameters.get(
                                        "meanMultiplier"
                                )
                        )
                );

        List<PreviousAssessment> previous =
                findPreviousAssessments(
                        payment.supplierId(),
                        now.minusSeconds(
                                windowSeconds
                        )
                );

        if (
                previous.size()
                        < minimumObservations
        ) {

            return decision(
                    rule,
                    false,
                    "Insufficient supplier payment history for unusual amount detection"
            );
        }

        BigDecimal total =
                previous.stream()
                        .map(
                                PreviousAssessment::paymentAmount
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal average =
                total.divide(
                        BigDecimal.valueOf(
                                previous.size()
                        ),
                        2,
                        RoundingMode.HALF_UP
                );

        BigDecimal unusualAmountThreshold =
                average.multiply(
                        multiplier
                );

        boolean triggered =
                payment.paymentAmount()
                        .compareTo(
                                unusualAmountThreshold
                        ) > 0;

        return decision(
                rule,
                triggered,
                triggered
                        ? "Payment amount is unusually high compared with supplier history"
                        : "Payment amount is consistent with supplier history"
        );
    }

    private FactorDecision evaluateReactivatedSupplier(
            ConfigurationRepository.RiskRule rule,
            PaymentRiskData payment,
            Map<String, Object> parameters,
            LocalDateTime now
    ) {

        long windowSeconds =
                longValue(
                        parameters,
                        "windowSeconds"
                );

        LocalDateTime reactivatedAt =
                payment.supplierReactivatedAt();

        boolean triggered =
                reactivatedAt != null
                        &&
                        !reactivatedAt.isBefore(
                                now.minusSeconds(
                                        windowSeconds
                                )
                        );

        return decision(
                rule,
                triggered,
                triggered
                        ? "Supplier was recently reactivated"
                        : "Supplier was not recently reactivated"
        );
    }

    private FactorDecision evaluatePreviousFlags(
            ConfigurationRepository.RiskRule rule,
            PaymentRiskData payment,
            Map<String, Object> parameters,
            LocalDateTime now
    ) {

        long windowSeconds =
                longValue(
                        parameters,
                        "windowSeconds"
                );

        int minimumFlaggedPayments =
                intValue(
                        parameters,
                        "minimumFlaggedPayments"
                );

        Set<String> qualifyingRuleCodes =
                stringSet(
                        parameters.get(
                                "qualifyingRuleCodes"
                        )
                );

        if (
                qualifyingRuleCodes.isEmpty()
        ) {

            return decision(
                    rule,
                    false,
                    "No qualifying previous risk rules are configured"
            );
        }

        int count =
                countPreviousFlaggedAssessments(
                        payment.supplierId(),
                        now.minusSeconds(
                                windowSeconds
                        ),
                        qualifyingRuleCodes
                );

        boolean triggered =
                count
                        >= minimumFlaggedPayments;

        return decision(
                rule,
                triggered,
                triggered
                        ? "Supplier has previous qualifying risk flags"
                        : "No significant previous risk flags were found"
        );
    }

    private FactorDecision evaluateNewSupplier(
            ConfigurationRepository.RiskRule rule,
            PaymentRiskData payment,
            Map<String, Object> parameters,
            LocalDateTime now
    ) {

        long windowSeconds =
                longValue(
                        parameters,
                        "windowSeconds"
                );

        LocalDateTime supplierCreatedAt =
                payment.supplierCreatedAt();

        boolean triggered =
                supplierCreatedAt != null
                        &&
                        !supplierCreatedAt.isBefore(
                                now.minusSeconds(
                                        windowSeconds
                                )
                        );

        return decision(
                rule,
                triggered,
                triggered
                        ? "Supplier was created recently"
                        : "Supplier is not considered new"
        );
    }

    private FactorDecision evaluateNearDuplicate(
            ConfigurationRepository.RiskRule rule,
            PaymentRiskData payment,
            Map<String, Object> parameters,
            LocalDateTime now
    ) {

        long windowSeconds =
                longValue(
                        parameters,
                        "windowSeconds"
                );

        String normalization =
                String.valueOf(
                        parameters.get(
                                "invoiceNumberNormalization"
                        )
                );

        List<PreviousAssessment> previous =
                findPreviousAssessments(
                        payment.supplierId(),
                        now.minusSeconds(
                                windowSeconds
                        )
                );

        String currentInvoice =
                normalizeInvoiceNumber(
                        payment.invoiceNumber(),
                        normalization
                );

        boolean triggered =
                previous.stream()
                        .anyMatch(
                                item -> {

                                    String previousInvoice =
                                            normalizeInvoiceNumber(
                                                    item.invoiceNumber(),
                                                    normalization
                                            );

                                    boolean sameInvoice =
                                            currentInvoice.equals(
                                                    previousInvoice
                                            );

                                    boolean sameAmount =
                                            payment.paymentAmount()
                                                    .compareTo(
                                                            item.paymentAmount()
                                                    ) == 0;

                                    return sameInvoice
                                            || sameAmount;
                                }
                        );

        return decision(
                rule,
                triggered,
                triggered
                        ? "A similar previous supplier invoice or payment amount was detected"
                        : "No near-duplicate invoice pattern detected"
        );
    }

    private List<PreviousAssessment> findPreviousAssessments(
            long supplierId,
            LocalDateTime since
    ) {

        return jdbcTemplate.query(
                """
                SELECT
                    PAYMENT_AMOUNT,
                    INVOICE_NUMBER
                FROM RISK_ASSESSMENTS
                WHERE SUPPLIER_ID = ?
                  AND STATUS = 'COMPLETED'
                  AND COMPLETED_AT >= ?
                ORDER BY COMPLETED_AT DESC
                """,

                (rs, rowNum) ->
                        new PreviousAssessment(
                                rs.getBigDecimal(
                                        "PAYMENT_AMOUNT"
                                ),
                                rs.getString(
                                        "INVOICE_NUMBER"
                                )
                        ),

                supplierId,
                Timestamp.valueOf(
                        since
                )
        );
    }

    private long countAssessments(
            long supplierId,
            LocalDateTime since
    ) {

        Long count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM RISK_ASSESSMENTS
                        WHERE SUPPLIER_ID = ?
                          AND STATUS = 'COMPLETED'
                          AND COMPLETED_AT >= ?
                        """,
                        Long.class,
                        supplierId,
                        Timestamp.valueOf(
                                since
                        )
                );

        return count == null
                ? 0
                : count;
    }

    private int countPreviousFlaggedAssessments(
            long supplierId,
            LocalDateTime since,
            Set<String> qualifyingRuleCodes
    ) {

        String placeholders =
                String.join(
                        ",",
                        java.util.Collections.nCopies(
                                qualifyingRuleCodes.size(),
                                "?"
                        )
                );

        String sql =
                """
                SELECT COUNT(DISTINCT A.ASSESSMENT_ID)
                FROM RISK_ASSESSMENTS A
                JOIN RISK_ASSESSMENT_FACTORS F
                  ON F.ASSESSMENT_ID = A.ASSESSMENT_ID
                WHERE A.SUPPLIER_ID = ?
                  AND A.STATUS = 'COMPLETED'
                  AND A.COMPLETED_AT >= ?
                  AND F.TRIGGERED = 1
                  AND F.RULE_CODE IN (
                """
                        + placeholders
                        + ")";

        List<Object> arguments =
                new ArrayList<>();

        arguments.add(
                supplierId
        );

        arguments.add(
                Timestamp.valueOf(
                        since
                )
        );

        arguments.addAll(
                qualifyingRuleCodes
        );

        Integer count =
                jdbcTemplate.queryForObject(
                        sql,
                        Integer.class,
                        arguments.toArray()
                );

        return count == null
                ? 0
                : count;
    }

    private FactorDecision decision(
            ConfigurationRepository.RiskRule rule,
            boolean triggered,
            String explanation
    ) {

        return new FactorDecision(
                rule.ruleCode(),
                triggered,
                rule.weight(),
                triggered
                        ? rule.weight()
                        : 0,
                explanation
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readParameters(
            String json
    ) {

        try {

            Object value =
                    jsonMapper.readValue(
                            json,
                            Map.class
                    );

            if (
                    !(value instanceof Map<?, ?>)
            ) {

                throw new IllegalStateException(
                        "Risk rule parameters must be a JSON object"
                );
            }

            return (Map<String, Object>) value;

        } catch (
                Exception exception
        ) {

            throw new IllegalStateException(
                    "Invalid risk rule parameters",
                    exception
            );
        }
    }

    private long longValue(
            Map<String, Object> values,
            String key
    ) {

        Object value =
                values.get(
                        key
                );

        if (
                value instanceof Number number
        ) {

            return number.longValue();
        }

        if (
                value != null
        ) {

            return Long.parseLong(
                    value.toString()
            );
        }

        throw new IllegalStateException(
                "Missing risk configuration parameter: "
                        + key
        );
    }

    private int intValue(
            Map<String, Object> values,
            String key
    ) {

        return Math.toIntExact(
                longValue(
                        values,
                        key
                )
        );
    }

    private Set<String> stringSet(
            Object value
    ) {

        Set<String> result =
                new HashSet<>();

        if (
                value instanceof List<?> list
        ) {

            for (
                    Object item
                    : list
            ) {

                if (
                        item != null
                ) {

                    result.add(
                            item.toString()
                    );
                }
            }
        }

        return result;
    }

    private String normalizeInvoiceNumber(
            String invoiceNumber,
            String policy
    ) {

        if (
                invoiceNumber == null
        ) {
            return "";
        }

        if (
                "TRIM_UPPERCASE"
                        .equalsIgnoreCase(
                                policy
                        )
        ) {

            return invoiceNumber
                    .trim()
                    .toUpperCase();
        }

        return invoiceNumber;
    }
}