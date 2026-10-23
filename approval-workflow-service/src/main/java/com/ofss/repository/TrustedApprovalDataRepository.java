
package com.ofss.repository;

import com.ofss.entity.ApprovalLevel;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class TrustedApprovalDataRepository {

    private final JdbcTemplate jdbcTemplate;

    public TrustedApprovalDataRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record PaymentData(
            Long paymentId,
            Long requesterId,
            BigDecimal paymentAmount,
            String riskBand,
            int requiredApprovalLevel,
            String paymentStatus,
            Long configurationVersionId,
            int riskApprovalLevel,
            String assessmentStatus,
            int slaDurationSeconds,
            String escalationPolicyCode,
            String terminalLevelPolicyCode
    ) {}

    /*
     * Policy for a particular approval level
     * within the payment's configuration version.
     */
    public record SlaPolicy(
            int durationSeconds,
            String escalationPolicyCode,
            String terminalLevelPolicyCode
    ) {}

    public PaymentData getPaymentData(Long paymentId) {

        String sql = """
            SELECT
                P.PAYMENT_ID,
                P.REQUESTER_ID,
                P.PAYMENT_AMOUNT,
                P.RISK_BAND,
                P.REQUIRED_APPROVAL_LEVEL,
                P.STATUS AS PAYMENT_STATUS,
                R.CONFIGURATION_VERSION_ID,
                R.RISK_APPROVAL_LEVEL,
                R.STATUS AS ASSESSMENT_STATUS,
                S.DURATION_SECONDS,
                S.ESCALATION_POLICY_CODE,
                S.TERMINAL_LEVEL_POLICY_CODE
            FROM PAY_PAYMENT_REQUESTS P
            JOIN RISK_ASSESSMENTS R
                ON R.PAYMENT_ID = P.PAYMENT_ID
            JOIN SLA_CONFIGS S
                ON S.VERSION_ID = R.CONFIGURATION_VERSION_ID
               AND S.APPROVAL_LEVEL = P.REQUIRED_APPROVAL_LEVEL
            WHERE P.PAYMENT_ID = ?
            """;

        List<PaymentData> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new PaymentData(
                        rs.getLong("PAYMENT_ID"),
                        rs.getLong("REQUESTER_ID"),
                        rs.getBigDecimal("PAYMENT_AMOUNT"),
                        rs.getString("RISK_BAND"),
                        rs.getInt("REQUIRED_APPROVAL_LEVEL"),
                        rs.getString("PAYMENT_STATUS"),
                        rs.getLong("CONFIGURATION_VERSION_ID"),
                        rs.getInt("RISK_APPROVAL_LEVEL"),
                        rs.getString("ASSESSMENT_STATUS"),
                        rs.getInt("DURATION_SECONDS"),
                        rs.getString("ESCALATION_POLICY_CODE"),
                        rs.getString("TERMINAL_LEVEL_POLICY_CODE")
                ),
                paymentId
        );

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment does not have complete risk assessment and SLA data"
            );
        }

        if (results.size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Multiple approval configurations found for payment"
            );
        }

        return results.get(0);
    }

    /*
     * NEW: Read the SLA policy for the CURRENT
     * approval task level.
     *
     * The configuration version belongs to the
     * payment's completed risk assessment.
     *
     * This is important when a payment originally
     * required L1 but was escalated to L2 or L3.
     *
     * Never substitute the active global version.
     */
    public SlaPolicy getSlaPolicy(
            Long configurationVersionId,
            ApprovalLevel approvalLevel
    ) {

        if (configurationVersionId == null
                || configurationVersionId <= 0
                || approvalLevel == null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Valid configuration version and approval level required"
            );
        }

        int levelNumber = switch (approvalLevel) {
            case L1 -> 1;
            case L2 -> 2;
            case L3 -> 3;
        };

        String sql = """
            SELECT
                DURATION_SECONDS,
                ESCALATION_POLICY_CODE,
                TERMINAL_LEVEL_POLICY_CODE
            FROM SLA_CONFIGS
            WHERE VERSION_ID = ?
              AND APPROVAL_LEVEL = ?
            """;

        List<SlaPolicy> policies = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SlaPolicy(
                        rs.getInt("DURATION_SECONDS"),
                        rs.getString("ESCALATION_POLICY_CODE"),
                        rs.getString("TERMINAL_LEVEL_POLICY_CODE")
                ),
                configurationVersionId,
                levelNumber
        );

        if (policies.size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Expected exactly one SLA policy for approval level"
            );
        }

        SlaPolicy policy = policies.get(0);

        if (policy.durationSeconds() <= 0
                || policy.escalationPolicyCode() == null
                || policy.escalationPolicyCode().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid configured SLA policy"
            );
        }

        return policy;
    }

    public int getAmountApprovalLevel(
            Long configurationVersionId,
            BigDecimal paymentAmount
    ) {

        String sql = """
            SELECT APPROVAL_LEVEL
            FROM APPROVAL_THRESHOLD_CONFIGS
            WHERE VERSION_ID = ?
              AND MIN_AMOUNT_INCLUSIVE <= ?
              AND (
                  MAX_AMOUNT_EXCLUSIVE IS NULL
                  OR ? < MAX_AMOUNT_EXCLUSIVE
              )
            """;

        List<Integer> levels = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getInt("APPROVAL_LEVEL"),
                configurationVersionId,
                paymentAmount,
                paymentAmount
        );

        if (levels.size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Expected exactly one amount approval threshold"
            );
        }

        return levels.get(0);
    }
}


