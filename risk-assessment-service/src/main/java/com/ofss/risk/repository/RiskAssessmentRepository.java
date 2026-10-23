package com.ofss.risk.repository;

import com.ofss.risk.dto.RiskAssessmentResponse;
import com.ofss.risk.dto.RiskFactorResponse;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RiskAssessmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public RiskAssessmentRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record PaymentRiskData(

            long paymentId,
            long invoiceId,
            long supplierId,
            long requesterId,
            String invoiceNumber,
            java.math.BigDecimal paymentAmount,
            LocalDateTime supplierCreatedAt,
            LocalDateTime supplierReactivatedAt,
            String paymentStatus

    ) {
    }

    public PaymentRiskData findPaymentRiskData(
            long paymentId
    ) {

        List<PaymentRiskData> rows =
                jdbcTemplate.query(
                        """
                        SELECT
                            P.PAYMENT_ID,
                            P.INVOICE_ID,
                            P.SUPPLIER_ID,
                            P.REQUESTER_ID,
                            P.PAYMENT_AMOUNT,
                            P.STATUS,
                            I.INVOICE_NUMBER,
                            S.CREATED_AT AS SUPPLIER_CREATED_AT,

                            (
                                SELECT MAX(H.CHANGED_AT)
                                FROM SUP_SUPPLIER_STATUS_HISTORY H
                                WHERE H.SUPPLIER_ID = S.SUPPLIER_ID
                                  AND H.NEW_STATUS = 'ACTIVE'
                                  AND (
                                        H.PREVIOUS_STATUS IS NULL
                                        OR H.PREVIOUS_STATUS <> 'ACTIVE'
                                      )
                            ) AS SUPPLIER_REACTIVATED_AT

                        FROM PAY_PAYMENT_REQUESTS P

                        JOIN SUP_INVOICES I
                          ON I.INVOICE_ID = P.INVOICE_ID

                        JOIN SUP_SUPPLIERS S
                          ON S.SUPPLIER_ID = P.SUPPLIER_ID

                        WHERE P.PAYMENT_ID = ?
                        """,

                        (rs, rowNum) -> {

                            Timestamp createdTimestamp =
                                    rs.getTimestamp(
                                            "SUPPLIER_CREATED_AT"
                                    );

                            Timestamp reactivatedTimestamp =
                                    rs.getTimestamp(
                                            "SUPPLIER_REACTIVATED_AT"
                                    );

                            return new PaymentRiskData(
                                    rs.getLong(
                                            "PAYMENT_ID"
                                    ),
                                    rs.getLong(
                                            "INVOICE_ID"
                                    ),
                                    rs.getLong(
                                            "SUPPLIER_ID"
                                    ),
                                    rs.getLong(
                                            "REQUESTER_ID"
                                    ),
                                    rs.getString(
                                            "INVOICE_NUMBER"
                                    ),
                                    rs.getBigDecimal(
                                            "PAYMENT_AMOUNT"
                                    ),
                                    createdTimestamp == null
                                            ? null
                                            : createdTimestamp.toLocalDateTime(),
                                    reactivatedTimestamp == null
                                            ? null
                                            : reactivatedTimestamp.toLocalDateTime(),
                                    rs.getString(
                                            "STATUS"
                                    )
                            );
                        },

                        paymentId
                );

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "Payment request not found: "
                            + paymentId
            );
        }

        return rows.getFirst();
    }

    public Optional<Long> findAssessmentIdByPaymentId(
            long paymentId
    ) {

        List<Long> rows =
                jdbcTemplate.query(
                        """
                        SELECT ASSESSMENT_ID
                        FROM RISK_ASSESSMENTS
                        WHERE PAYMENT_ID = ?
                        """,
                        (rs, rowNum) ->
                                rs.getLong(
                                        "ASSESSMENT_ID"
                                ),
                        paymentId
                );

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
                rows.getFirst()
        );
    }

    public long insertAssessment(
            PaymentRiskData payment,
            String validationReference,
            long configurationVersionId,
            int riskScore,
            String riskBand,
            int riskApprovalLevel
    ) {

        String sql =
                """
                INSERT INTO RISK_ASSESSMENTS
                (
                    PAYMENT_ID,
                    SUPPLIER_ID,
                    INVOICE_ID,
                    REQUESTER_ID,
                    INVOICE_NUMBER,
                    PAYMENT_AMOUNT,
                    CURRENCY_CODE,
                    VALIDATION_REFERENCE,
                    SUPPLIER_CREATED_AT,
                    SUPPLIER_REACTIVATED_AT,
                    CONFIGURATION_VERSION_ID,
                    RISK_SCORE,
                    RISK_BAND,
                    RISK_APPROVAL_LEVEL,
                    STATUS,
                    CREATED_AT,
                    COMPLETED_AT
                )
                VALUES
                (
                    ?, ?, ?, ?, ?, ?, 'INR', ?,
                    ?, ?, ?, ?, ?, ?,
                    'COMPLETED',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """;

        GeneratedKeyHolder keyHolder =
                new GeneratedKeyHolder();

        int inserted =
                jdbcTemplate.update(
                        (PreparedStatementCreator) connection -> {

                            var statement =
                                    connection.prepareStatement(
                                            sql,
                                            new String[]{
                                                    "ASSESSMENT_ID"
                                            }
                                    );

                            statement.setLong(
                                    1,
                                    payment.paymentId()
                            );

                            statement.setLong(
                                    2,
                                    payment.supplierId()
                            );

                            statement.setLong(
                                    3,
                                    payment.invoiceId()
                            );

                            statement.setLong(
                                    4,
                                    payment.requesterId()
                            );

                            statement.setString(
                                    5,
                                    payment.invoiceNumber()
                            );

                            statement.setBigDecimal(
                                    6,
                                    payment.paymentAmount()
                            );

                            statement.setString(
                                    7,
                                    validationReference
                            );

                            if (
                                    payment.supplierCreatedAt()
                                            == null
                            ) {

                                statement.setNull(
                                        8,
                                        java.sql.Types.TIMESTAMP
                                );

                            } else {

                                statement.setTimestamp(
                                        8,
                                        Timestamp.valueOf(
                                                payment.supplierCreatedAt()
                                        )
                                );
                            }

                            if (
                                    payment.supplierReactivatedAt()
                                            == null
                            ) {

                                statement.setNull(
                                        9,
                                        java.sql.Types.TIMESTAMP
                                );

                            } else {

                                statement.setTimestamp(
                                        9,
                                        Timestamp.valueOf(
                                                payment.supplierReactivatedAt()
                                        )
                                );
                            }

                            statement.setLong(
                                    10,
                                    configurationVersionId
                            );

                            statement.setInt(
                                    11,
                                    riskScore
                            );

                            statement.setString(
                                    12,
                                    riskBand
                            );

                            statement.setInt(
                                    13,
                                    riskApprovalLevel
                            );

                            return statement;
                        },
                        keyHolder
                );

        if (inserted != 1) {
            throw new IllegalStateException(
                    "Risk assessment was not saved"
            );
        }

        Number generatedKey =
                keyHolder.getKey();

        if (generatedKey == null) {
            throw new IllegalStateException(
                    "Risk assessment ID was not generated"
            );
        }

        return generatedKey.longValue();
    }

    public void insertFactor(
            long assessmentId,
            String ruleCode,
            boolean triggered,
            int configuredWeight,
            int contribution,
            String explanation
    ) {

        jdbcTemplate.update(
                """
                INSERT INTO RISK_ASSESSMENT_FACTORS
                (
                    ASSESSMENT_ID,
                    RULE_CODE,
                    TRIGGERED,
                    CONFIGURED_WEIGHT,
                    SCORE_CONTRIBUTION,
                    EXPLANATION
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                assessmentId,
                ruleCode,
                triggered ? 1 : 0,
                configuredWeight,
                contribution,
                explanation
        );
    }

    public List<RiskFactorResponse> findFactors(
            long assessmentId
    ) {

        return jdbcTemplate.query(
                """
                SELECT
                    RULE_CODE,
                    TRIGGERED,
                    CONFIGURED_WEIGHT,
                    SCORE_CONTRIBUTION,
                    EXPLANATION
                FROM RISK_ASSESSMENT_FACTORS
                WHERE ASSESSMENT_ID = ?
                ORDER BY FACTOR_ID
                """,
                (rs, rowNum) ->
                        new RiskFactorResponse(
                                rs.getString(
                                        "RULE_CODE"
                                ),
                                rs.getInt(
                                        "TRIGGERED"
                                ) == 1,
                                rs.getInt(
                                        "CONFIGURED_WEIGHT"
                                ),
                                rs.getInt(
                                        "SCORE_CONTRIBUTION"
                                ),
                                rs.getString(
                                        "EXPLANATION"
                                )
                        ),
                assessmentId
        );
    }

    public RiskAssessmentResponse findById(
            long assessmentId
    ) {

        List<RiskAssessmentResponse> rows =
                jdbcTemplate.query(
                        """
                        SELECT
                            ASSESSMENT_ID,
                            PAYMENT_ID,
                            SUPPLIER_ID,
                            INVOICE_ID,
                            PAYMENT_AMOUNT,
                            CONFIGURATION_VERSION_ID,
                            RISK_SCORE,
                            RISK_BAND,
                            RISK_APPROVAL_LEVEL,
                            STATUS,
                            COMPLETED_AT
                        FROM RISK_ASSESSMENTS
                        WHERE ASSESSMENT_ID = ?
                        """,

                        (rs, rowNum) ->
                                new RiskAssessmentResponse(
                                        rs.getLong(
                                                "ASSESSMENT_ID"
                                        ),
                                        rs.getLong(
                                                "PAYMENT_ID"
                                        ),
                                        rs.getLong(
                                                "SUPPLIER_ID"
                                        ),
                                        rs.getLong(
                                                "INVOICE_ID"
                                        ),
                                        rs.getBigDecimal(
                                                "PAYMENT_AMOUNT"
                                        ),
                                        rs.getLong(
                                                "CONFIGURATION_VERSION_ID"
                                        ),
                                        rs.getInt(
                                                "RISK_SCORE"
                                        ),
                                        rs.getString(
                                                "RISK_BAND"
                                        ),
                                        rs.getInt(
                                                "RISK_APPROVAL_LEVEL"
                                        ),
                                        rs.getString(
                                                "STATUS"
                                        ),
                                        rs.getTimestamp(
                                                "COMPLETED_AT"
                                        ).toLocalDateTime(),
                                        List.of()
                                ),

                        assessmentId
                );

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "Risk assessment not found: "
                            + assessmentId
            );
        }

        RiskAssessmentResponse row =
                rows.getFirst();

        return new RiskAssessmentResponse(
                row.assessmentId(),
                row.paymentId(),
                row.supplierId(),
                row.invoiceId(),
                row.paymentAmount(),
                row.configurationVersionId(),
                row.riskScore(),
                row.riskBand(),
                row.riskApprovalLevel(),
                row.status(),
                row.completedAt(),
                findFactors(
                        assessmentId
                )
        );
    }

    public RiskAssessmentResponse findByPaymentId(
            long paymentId
    ) {

        Long assessmentId =
                jdbcTemplate.query(
                                """
                                SELECT ASSESSMENT_ID
                                FROM RISK_ASSESSMENTS
                                WHERE PAYMENT_ID = ?
                                """,
                                (rs, rowNum) ->
                                        rs.getLong(
                                                "ASSESSMENT_ID"
                                        ),
                                paymentId
                        )
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "No risk assessment found for payment "
                                                        + paymentId
                                        )
                        );

        return findById(
                assessmentId
        );
    }

    public void updatePaymentRisk(
            long paymentId,
            int riskScore,
            String riskBand
    ) {

        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE PAY_PAYMENT_REQUESTS
                        SET RISK_SCORE = ?,
                            RISK_BAND = ?,
                            UPDATED_AT = CURRENT_TIMESTAMP
                        WHERE PAYMENT_ID = ?
                        """,
                        riskScore,
                        riskBand,
                        paymentId
                );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Payment request not found while updating risk: "
                            + paymentId
            );
        }
    }

    public void updateRequiredApprovalLevel(
            long paymentId,
            int requiredApprovalLevel
    ) {

        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE PAY_PAYMENT_REQUESTS
                        SET REQUIRED_APPROVAL_LEVEL = ?,
                            UPDATED_AT = CURRENT_TIMESTAMP
                        WHERE PAYMENT_ID = ?
                        """,
                        requiredApprovalLevel,
                        paymentId
                );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Payment request not found while updating approval level: "
                            + paymentId
            );
        }
    }

    public long countSupplierAssessmentsSince(
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
    
    
    public void updatePaymentStatusToPendingApproval(
            long paymentId
    ) {

        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE PAY_PAYMENT_REQUESTS
                        SET STATUS = 'PENDING_APPROVAL',
                            UPDATED_AT = CURRENT_TIMESTAMP
                        WHERE PAYMENT_ID = ?
                          AND STATUS = 'PENDING_VALIDATION'
                        """,
                        paymentId
                );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Payment could not be moved to PENDING_APPROVAL: "
                            + paymentId
            );
        }
    }
}