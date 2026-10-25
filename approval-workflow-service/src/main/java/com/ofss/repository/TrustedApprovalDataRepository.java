package com.ofss.repository;

import com.ofss.entity.ApprovalLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
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

        this.jdbcTemplate =
                jdbcTemplate;
    }


    /*
     * =========================================================
     * TRUSTED PAYMENT DATA
     * =========================================================
     *
     * invoiceDueDate comes from PAY_PAYMENT_REQUESTS.
     *
     * It is NOT obtained by joining the supplier-invoice
     * database and it is NOT supplied by the browser.
     */
    public record PaymentData(

            Long paymentId,

            Long invoiceId,

            LocalDate invoiceDueDate,

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

    ) {
    }


    /*
     * =========================================================
     * SLA POLICY
     * =========================================================
     */
    public record SlaPolicy(

            int durationSeconds,

            String escalationPolicyCode,

            String terminalLevelPolicyCode

    ) {
    }


    /*
     * =========================================================
     * GET PAYMENT DATA
     * =========================================================
     */
    public PaymentData getPaymentData(
            Long paymentId
    ) {

        if (
                paymentId == null
                ||
                paymentId <= 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid payment ID required"
            );
        }


        String sql = """
            SELECT
                P.PAYMENT_ID,
                P.INVOICE_ID,
                P.INVOICE_DUE_DATE,
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
                ON R.PAYMENT_ID =
                    P.PAYMENT_ID

            JOIN SLA_CONFIGS S
                ON S.VERSION_ID =
                    R.CONFIGURATION_VERSION_ID
               AND S.APPROVAL_LEVEL =
                    P.REQUIRED_APPROVAL_LEVEL

            WHERE P.PAYMENT_ID = ?
            """;


        List<PaymentData> results =
                jdbcTemplate.query(
                        sql,

                        (rs, rowNum) ->
                                new PaymentData(

                                        rs.getLong(
                                                "PAYMENT_ID"
                                        ),

                                        rs.getLong(
                                                "INVOICE_ID"
                                        ),

                                        rs.getObject(
                                                "INVOICE_DUE_DATE",
                                                LocalDate.class
                                        ),

                                        rs.getLong(
                                                "REQUESTER_ID"
                                        ),

                                        rs.getBigDecimal(
                                                "PAYMENT_AMOUNT"
                                        ),

                                        rs.getString(
                                                "RISK_BAND"
                                        ),

                                        rs.getInt(
                                                "REQUIRED_APPROVAL_LEVEL"
                                        ),

                                        rs.getString(
                                                "PAYMENT_STATUS"
                                        ),

                                        rs.getLong(
                                                "CONFIGURATION_VERSION_ID"
                                        ),

                                        rs.getInt(
                                                "RISK_APPROVAL_LEVEL"
                                        ),

                                        rs.getString(
                                                "ASSESSMENT_STATUS"
                                        ),

                                        rs.getInt(
                                                "DURATION_SECONDS"
                                        ),

                                        rs.getString(
                                                "ESCALATION_POLICY_CODE"
                                        ),

                                        rs.getString(
                                                "TERMINAL_LEVEL_POLICY_CODE"
                                        )
                                ),

                        paymentId
                );


        if (
                results.isEmpty()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment does not have complete "
                            + "risk assessment and SLA data"
            );
        }


        if (
                results.size() != 1
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Multiple approval configurations "
                            + "found for payment"
            );
        }


        PaymentData payment =
                results.get(0);


        if (
                payment.invoiceId() == null
                ||
                payment.invoiceId() <= 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment does not have a valid invoice"
            );
        }


        /*
         * New payment requests must have the due date
         * snapshot populated.
         *
         * Historical rows created before the migration
         * may not have one; those are rejected here rather
         * than silently using an incorrect deadline.
         */
        if (
                payment.invoiceDueDate() == null
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invoice due date is missing "
                            + "from payment data"
            );
        }


        return payment;
    }


    /*
     * =========================================================
     * GET SLA POLICY
     * =========================================================
     */
    public SlaPolicy getSlaPolicy(
            Long configurationVersionId,
            ApprovalLevel approvalLevel
    ) {

        if (
                configurationVersionId == null
                ||
                configurationVersionId <= 0
                ||
                approvalLevel == null
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Valid configuration version "
                            + "and approval level required"
            );
        }


        int levelNumber =
                switch (
                        approvalLevel
                ) {

                    case L1 ->
                            1;

                    case L2 ->
                            2;

                    case L3 ->
                            3;
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


        List<SlaPolicy> policies =
                jdbcTemplate.query(
                        sql,

                        (rs, rowNum) ->
                                new SlaPolicy(

                                        rs.getInt(
                                                "DURATION_SECONDS"
                                        ),

                                        rs.getString(
                                                "ESCALATION_POLICY_CODE"
                                        ),

                                        rs.getString(
                                                "TERMINAL_LEVEL_POLICY_CODE"
                                        )
                                ),

                        configurationVersionId,
                        levelNumber
                );


        if (
                policies.size() != 1
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Expected exactly one SLA policy "
                            + "for approval level"
            );
        }


        SlaPolicy policy =
                policies.get(0);


        if (
                policy.durationSeconds() <= 0
                ||
                policy.escalationPolicyCode() == null
                ||
                policy
                        .escalationPolicyCode()
                        .isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid configured SLA policy"
            );
        }


        return policy;
    }


    /*
     * =========================================================
     * GET AMOUNT APPROVAL LEVEL
     * =========================================================
     */
    public int getAmountApprovalLevel(
            Long configurationVersionId,
            BigDecimal paymentAmount
    ) {

        if (
                configurationVersionId == null
                ||
                configurationVersionId <= 0
                ||
                paymentAmount == null
                ||
                paymentAmount.signum() <= 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Valid configuration version "
                            + "and payment amount required"
            );
        }


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


        List<Integer> levels =
                jdbcTemplate.query(
                        sql,

                        (rs, rowNum) ->
                                rs.getInt(
                                        "APPROVAL_LEVEL"
                                ),

                        configurationVersionId,
                        paymentAmount,
                        paymentAmount
                );


        if (
                levels.size() != 1
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Expected exactly one "
                            + "amount approval threshold"
            );
        }


        return levels.get(0);
    }
}

