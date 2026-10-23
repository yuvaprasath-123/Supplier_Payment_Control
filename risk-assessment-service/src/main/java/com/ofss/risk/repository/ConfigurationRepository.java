package com.ofss.risk.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConfigurationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConfigurationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record RiskRule(
            String ruleCode,
            boolean enabled,
            int weight,
            String parameterSchemaVersion,
            String parametersJson,
            int evaluationOrder
    ) {
    }

    public record RiskBand(
            String bandCode,
            int minScoreInclusive,
            Integer maxScoreInclusive,
            int approvalLevel
    ) {
    }

    public record ApprovalThreshold(
            int approvalLevel,
            BigDecimal minAmountInclusive,
            BigDecimal maxAmountExclusive
    ) {
    }

    public record ActiveConfiguration(
            long versionId,
            int maxFinalScore,
            List<RiskRule> rules,
            List<RiskBand> bands,
            List<ApprovalThreshold> thresholds
    ) {
    }

    public ActiveConfiguration getActiveConfiguration() {

        Long versionId = jdbcTemplate.queryForObject(
                """
                SELECT VERSION_ID
                FROM CURRENT_CONFIGURATION
                WHERE POINTER_ID = 1
                """,
                Long.class
        );

        if (versionId == null) {
            throw new IllegalStateException(
                    "No active business configuration found"
            );
        }

        Integer maxFinalScore = jdbcTemplate.queryForObject(
                """
                SELECT MAX_FINAL_SCORE
                FROM CONFIGURATION_VERSIONS
                WHERE VERSION_ID = ?
                  AND PUBLICATION_STATE = 'PUBLISHED'
                """,
                Integer.class,
                versionId
        );

        if (maxFinalScore == null) {
            throw new IllegalStateException(
                    "Active configuration has no maximum final score"
            );
        }

        List<RiskRule> rules = jdbcTemplate.query(
                """
                SELECT
                    RULE_CODE,
                    ENABLED,
                    WEIGHT,
                    PARAMETER_SCHEMA_VERSION,
                    PARAMETERS_JSON,
                    EVALUATION_ORDER
                FROM RISK_RULE_CONFIGS
                WHERE VERSION_ID = ?
                ORDER BY EVALUATION_ORDER
                """,
                (rs, rowNum) -> new RiskRule(
                        rs.getString("RULE_CODE"),
                        rs.getInt("ENABLED") == 1,
                        rs.getInt("WEIGHT"),
                        rs.getString("PARAMETER_SCHEMA_VERSION"),
                        rs.getString("PARAMETERS_JSON"),
                        rs.getInt("EVALUATION_ORDER")
                ),
                versionId
        );

        List<RiskBand> bands = jdbcTemplate.query(
                """
                SELECT
                    BAND_CODE,
                    MIN_SCORE_INCLUSIVE,
                    MAX_SCORE_INCLUSIVE,
                    APPROVAL_LEVEL
                FROM RISK_BAND_CONFIGS
                WHERE VERSION_ID = ?
                ORDER BY MIN_SCORE_INCLUSIVE
                """,
                (rs, rowNum) -> {
                    int max = rs.getInt("MAX_SCORE_INCLUSIVE");
                    Integer nullableMax = rs.wasNull() ? null : max;

                    return new RiskBand(
                            rs.getString("BAND_CODE"),
                            rs.getInt("MIN_SCORE_INCLUSIVE"),
                            nullableMax,
                            rs.getInt("APPROVAL_LEVEL")
                    );
                },
                versionId
        );

        List<ApprovalThreshold> thresholds = jdbcTemplate.query(
                """
                SELECT
                    APPROVAL_LEVEL,
                    MIN_AMOUNT_INCLUSIVE,
                    MAX_AMOUNT_EXCLUSIVE
                FROM APPROVAL_THRESHOLD_CONFIGS
                WHERE VERSION_ID = ?
                ORDER BY APPROVAL_LEVEL
                """,
                (rs, rowNum) -> new ApprovalThreshold(
                        rs.getInt("APPROVAL_LEVEL"),
                        rs.getBigDecimal("MIN_AMOUNT_INCLUSIVE"),
                        rs.getBigDecimal("MAX_AMOUNT_EXCLUSIVE")
                ),
                versionId
        );

        if (rules.isEmpty()) {
            throw new IllegalStateException(
                    "Active configuration contains no risk rules"
            );
        }

        if (bands.isEmpty()) {
            throw new IllegalStateException(
                    "Active configuration contains no risk bands"
            );
        }

        return new ActiveConfiguration(
                versionId,
                maxFinalScore,
                rules,
                bands,
                thresholds
        );
    }
}