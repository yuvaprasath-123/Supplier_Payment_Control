
package com.ofss.risk.service;

import com.ofss.risk.dto.RiskAssessmentRequest;
import com.ofss.risk.dto.RiskAssessmentResponse;
import com.ofss.risk.repository.ApprovalAssignmentOutboxRepository;
import com.ofss.risk.repository.ConfigurationRepository;
import com.ofss.risk.repository.RiskAssessmentRepository;
import com.ofss.risk.repository.RiskAssessmentRepository.PaymentRiskData;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskAssessmentService {

    private final ConfigurationRepository configurationRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final RiskScoringService riskScoringService;
    private final ApprovalAssignmentOutboxRepository approvalAssignmentOutboxRepository;

    public RiskAssessmentService(
            ConfigurationRepository configurationRepository,
            RiskAssessmentRepository riskAssessmentRepository,
            RiskScoringService riskScoringService,
            ApprovalAssignmentOutboxRepository approvalAssignmentOutboxRepository
    ) {
        this.configurationRepository = configurationRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.riskScoringService = riskScoringService;
        this.approvalAssignmentOutboxRepository = approvalAssignmentOutboxRepository;
    }

    @Transactional
    public RiskAssessmentResponse assess(
            RiskAssessmentRequest request
    ) {

        /*
         * Idempotency:
         * One payment should have only one risk assessment.
         */
        var existingAssessment =
                riskAssessmentRepository
                        .findAssessmentIdByPaymentId(
                                request.paymentId()
                        );

        if (existingAssessment.isPresent()) {

            /*
             * Recovery:
             * If risk was assessed earlier and the payment is
             * still waiting for approval, ensure the assignment
             * event exists.
             *
             * Do not enqueue payments that have already moved
             * beyond PENDING_APPROVAL.
             */
            PaymentRiskData existingPayment =
                    riskAssessmentRepository
                            .findPaymentRiskData(
                                    request.paymentId()
                            );

            if ("PENDING_APPROVAL".equals(
                    existingPayment.paymentStatus())) {

                approvalAssignmentOutboxRepository
                        .recordPendingAssignment(
                                request.paymentId()
                        );
            }

            return riskAssessmentRepository
                    .findById(
                            existingAssessment.get()
                    );
        }

        /*
         * Load trusted payment, invoice and supplier data
         * directly from Oracle.
         */
        PaymentRiskData payment =
                riskAssessmentRepository
                        .findPaymentRiskData(
                                request.paymentId()
                        );

        /*
         * Preserve existing validation-stage checks.
         */
        if (
                !"PENDING_VALIDATION".equals(
                        payment.paymentStatus()
                )
                &&
                !"PENDING_APPROVAL".equals(
                        payment.paymentStatus()
                )
        ) {

            throw new IllegalStateException(
                    "Payment "
                            + payment.paymentId()
                            + " cannot be risk assessed while in status "
                            + payment.paymentStatus()
            );
        }

        /*
         * Load active business configuration.
         */
        ConfigurationRepository.ActiveConfiguration configuration =
                configurationRepository
                        .getActiveConfiguration();

        /*
         * Calculate risk using trusted Oracle data.
         */
        RiskScoringService.ScoreResult result =
                riskScoringService
                        .calculate(
                                payment,
                                configuration
                        );

        /*
         * Determine amount-based approval level.
         */
        int amountApprovalLevel =
                determineAmountApprovalLevel(
                        payment.paymentAmount(),
                        configuration
                );

        /*
         * Final required approval level is the higher
         * of amount-based and risk-based requirements.
         */
        int finalApprovalLevel =
                Math.max(
                        amountApprovalLevel,
                        result.riskApprovalLevel()
                );

        /*
         * Save the risk assessment.
         */
        long assessmentId =
                riskAssessmentRepository
                        .insertAssessment(
                                payment,
                                request.validationReference(),
                                configuration.versionId(),
                                result.riskScore(),
                                result.riskBand(),
                                result.riskApprovalLevel()
                        );

        /*
         * Save the risk-factor breakdown.
         */
        for (
                RiskScoringService.FactorDecision factor
                : result.factors()
        ) {

            riskAssessmentRepository
                    .insertFactor(
                            assessmentId,
                            factor.ruleCode(),
                            factor.triggered(),
                            factor.configuredWeight(),
                            factor.contribution(),
                            factor.explanation()
                    );
        }

        /*
         * Update payment risk score and band.
         */
        riskAssessmentRepository
                .updatePaymentRisk(
                        payment.paymentId(),
                        result.riskScore(),
                        result.riskBand()
                );

        /*
         * Update required approval level.
         */
        riskAssessmentRepository
                .updateRequiredApprovalLevel(
                        payment.paymentId(),
                        finalApprovalLevel
                );

        /*
         * Move payment to PENDING_APPROVAL.
         */
        riskAssessmentRepository
                .updatePaymentStatusToPendingApproval(
                        payment.paymentId()
                );

        /*
         * NEW:
         * Record an approval-assignment event in the
         * SAME Oracle transaction as risk assessment.
         *
         * If this insert fails, the transaction rolls back.
         * If the transaction commits, the assignment event
         * is available for reliable background processing.
         *
         * This does NOT create an approval task yet.
         */
        approvalAssignmentOutboxRepository
                .recordPendingAssignment(
                        payment.paymentId()
                );

        /*
         * Return the saved risk assessment.
         */
        return riskAssessmentRepository
                .findById(
                        assessmentId
                );
    }

    @Transactional(readOnly = true)
    public RiskAssessmentResponse getAssessment(
            long assessmentId
    ) {

        return riskAssessmentRepository
                .findById(
                        assessmentId
                );
    }

    @Transactional(readOnly = true)
    public RiskAssessmentResponse getAssessmentByPayment(
            long paymentId
    ) {

        return riskAssessmentRepository
                .findByPaymentId(
                        paymentId
                );
    }

    private int determineAmountApprovalLevel(
            BigDecimal paymentAmount,
            ConfigurationRepository.ActiveConfiguration configuration
    ) {

        return configuration.thresholds()
                .stream()
                .filter(
                        threshold -> {

                            boolean minimumMatches =
                                    paymentAmount.compareTo(
                                            threshold.minAmountInclusive()
                                    ) >= 0;

                            boolean maximumMatches =
                                    threshold.maxAmountExclusive() == null
                                            ||
                                            paymentAmount.compareTo(
                                                    threshold.maxAmountExclusive()
                                            ) < 0;

                            return minimumMatches
                                    && maximumMatches;
                        }
                )
                .map(
                        ConfigurationRepository.ApprovalThreshold::approvalLevel
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No approval threshold configured for payment amount "
                                                + paymentAmount
                                )
                );
    }
}
