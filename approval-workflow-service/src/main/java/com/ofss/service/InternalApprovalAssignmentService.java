
package com.ofss.service;

import com.ofss.dto.ApprovalRequests;
import com.ofss.dto.ApprovalWorkflowResponse;
import com.ofss.entity.ApprovalLevel;
import com.ofss.entity.ApprovalTask;
import com.ofss.entity.RiskBand;
import com.ofss.repository.ApprovalRouteRepository;
import com.ofss.repository.ApprovalTaskRepository;
import com.ofss.repository.TrustedApprovalDataRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InternalApprovalAssignmentService {

    private final ApprovalService approvalService;
    private final ApprovalRouteRepository routeRepository;
    private final ApprovalTaskRepository taskRepository;
    private final TrustedApprovalDataRepository trustedData;
    private final EligibleApproverSelector approverSelector;

    public InternalApprovalAssignmentService(
            ApprovalService approvalService,
            ApprovalRouteRepository routeRepository,
            ApprovalTaskRepository taskRepository,
            TrustedApprovalDataRepository trustedData,
            EligibleApproverSelector approverSelector
    ) {
        this.approvalService = approvalService;
        this.routeRepository = routeRepository;
        this.taskRepository = taskRepository;
        this.trustedData = trustedData;
        this.approverSelector = approverSelector;
    }

    @Transactional
    public ApprovalWorkflowResponse assign(
            Long paymentId,
            String authorization
    ) {

        if (paymentId == null || paymentId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid payment ID required"
            );
        }

        if (authorization == null
                || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Machine JWT is required"
            );
        }

        /*
         * IDEMPOTENCY:
         * Return an existing task instead of creating
         * another task for the same payment.
         */
        var existingTask =
                taskRepository.findByPaymentId(paymentId);

        if (existingTask.isPresent()) {
            return response(existingTask.get());
        }

        /*
         * Load authoritative payment, completed risk
         * assessment and versioned SLA configuration
         * from Oracle.
         */
        var payment =
                trustedData.getPaymentData(paymentId);

        if (!"PENDING_APPROVAL".equals(
                payment.paymentStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment is not pending approval"
            );
        }

        if (!"COMPLETED".equals(
                payment.assessmentStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Risk assessment is not completed"
            );
        }

        /*
         * Convert the trusted Oracle risk band into
         * the exact application enum.
         */
        RiskBand riskBand;

        try {
            riskBand = RiskBand.valueOf(
                    payment.riskBand()
            );

        } catch (IllegalArgumentException |
                 NullPointerException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid trusted risk band"
            );
        }

        /*
         * The risk policy maps:
         * LOW    -> L1
         * MEDIUM -> L2
         * HIGH   -> L3
         */
        ApprovalLevel riskLevel =
                switch (riskBand) {
                    case LOW -> ApprovalLevel.L1;
                    case MEDIUM -> ApprovalLevel.L2;
                    case HIGH -> ApprovalLevel.L3;
                };

        int trustedRiskLevel =
                switch (riskLevel) {
                    case L1 -> 1;
                    case L2 -> 2;
                    case L3 -> 3;
                };

        if (payment.riskApprovalLevel()
                != trustedRiskLevel) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Risk band and risk approval level disagree"
            );
        }

        /*
         * Resolve the amount-based approval level
         * from the SAME published configuration
         * version used by this risk assessment.
         */
        if (payment.configurationVersionId() == null
                || payment.configurationVersionId() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Valid approval configuration version required"
            );
        }

        int amountLevelNumber =
                trustedData.getAmountApprovalLevel(
                        payment.configurationVersionId(),
                        payment.paymentAmount()
                );

        ApprovalLevel amountLevel =
                switch (amountLevelNumber) {
                    case 1 -> ApprovalLevel.L1;
                    case 2 -> ApprovalLevel.L2;
                    case 3 -> ApprovalLevel.L3;
                    default ->
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "Invalid amount approval level"
                            );
                };

        /*
         * The required approval level must be
         * the stricter of amount and risk policy.
         */
        ApprovalLevel requiredLevel =
                ApprovalService.stricter(
                        amountLevel,
                        riskLevel
                );

        int requiredLevelNumber =
                switch (requiredLevel) {
                    case L1 -> 1;
                    case L2 -> 2;
                    case L3 -> 3;
                };

        if (payment.requiredApprovalLevel()
                != requiredLevelNumber) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment approval level disagrees with "
                            + "versioned risk and amount policy"
            );
        }

        /*
         * Preserve eligible-approver selection:
         * prefer the required level and permit
         * authorized higher-level fallback.
         */
        Long approverId =
                approverSelector.selectAtOrAboveLevel(
                        requiredLevel,
                        payment.requesterId(),
                        authorization
                );

        /*
         * The existing SLA must be represented
         * accurately in minutes.
         *
         * Do not silently truncate seconds.
         */
        int slaSeconds =
                payment.slaDurationSeconds();

        if (slaSeconds < 60
                || slaSeconds % 60 != 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configured SLA must be a positive "
                            + "whole number of minutes"
            );
        }

        int slaMinutes = slaSeconds / 60;

        if (slaMinutes > 525600) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configured SLA exceeds supported maximum"
            );
        }

        /*
         * Create the route only when absent.
         *
         * All route fields come from trusted Oracle
         * payment, risk and configuration records.
         *
         * Never pass null for riskBand or
         * amountApprovalLevel.
         */
        if (routeRepository
                .findByPaymentId(paymentId)
                .isEmpty()) {

            approvalService.route(
                    new ApprovalRequests.Route(
                            paymentId,
                            payment.requesterId(),
                            payment.paymentAmount(),
                            riskBand,
                            amountLevel,
                            String.valueOf(
                                    payment.configurationVersionId()
                            )
                    )
            );
        }

        /*
         * Create the task using the versioned SLA.
         *
         * Existing ApprovalService.create()
         * independently checks the assignment
         * and approver authorization.
         *
         * Route and task creation remain atomic
         * under this transaction.
         */
        ApprovalTask task =
                approvalService.create(
                        new ApprovalRequests.Task(
                                paymentId,
                                approverId,
                                slaMinutes
                        ),
                        authorization
                );

        return response(task);
    }

    private ApprovalWorkflowResponse response(
            ApprovalTask task
    ) {
        return new ApprovalWorkflowResponse(
                task.id,
                task.paymentId,
                task.status,
                task.requiredLevel,
                task.decidedBy,
                task.remark,
                task.dueAt
        );
    }
}
