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

    private final ApprovalService
            approvalService;

    private final ApprovalRouteRepository
            routeRepository;

    private final ApprovalTaskRepository
            taskRepository;

    private final TrustedApprovalDataRepository
            trustedData;

    private final EligibleApproverSelector
            approverSelector;


    public InternalApprovalAssignmentService(
            ApprovalService approvalService,
            ApprovalRouteRepository routeRepository,
            ApprovalTaskRepository taskRepository,
            TrustedApprovalDataRepository trustedData,
            EligibleApproverSelector approverSelector
    ) {

        this.approvalService =
                approvalService;

        this.routeRepository =
                routeRepository;

        this.taskRepository =
                taskRepository;

        this.trustedData =
                trustedData;

        this.approverSelector =
                approverSelector;
    }


    /*
     * =========================================================
     * ASSIGN APPROVAL
     * =========================================================
     */
    @Transactional
    public ApprovalWorkflowResponse assign(
            Long paymentId,
            String authorization
    ) {

        /*
         * -----------------------------------------------------
         * REQUEST VALIDATION
         * -----------------------------------------------------
         */
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


        if (
                authorization == null
                ||
                !authorization.startsWith(
                        "Bearer "
                )
                ||
                authorization
                        .substring(7)
                        .isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Machine JWT is required"
            );
        }


        /*
         * -----------------------------------------------------
         * IDEMPOTENCY
         * -----------------------------------------------------
         */
        var existingTask =
                taskRepository
                        .findByPaymentId(
                                paymentId
                        );


        if (
                existingTask.isPresent()
        ) {

            return response(
                    existingTask.get()
            );
        }


        /*
         * -----------------------------------------------------
         * TRUSTED PAYMENT / RISK / SLA DATA
         * -----------------------------------------------------
         *
         * invoiceDueDate is now part of trusted payment
         * data and does not require another HTTP request.
         */
        var payment =
                trustedData
                        .getPaymentData(
                                paymentId
                        );


        if (
                !"PENDING_APPROVAL"
                        .equals(
                                payment.paymentStatus()
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment is not pending approval"
            );
        }


        if (
                !"COMPLETED"
                        .equals(
                                payment.assessmentStatus()
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Risk assessment is not completed"
            );
        }


        if (
                payment.invoiceDueDate() == null
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invoice due date is missing"
            );
        }


        /*
         * -----------------------------------------------------
         * RISK APPROVAL LEVEL
         * -----------------------------------------------------
         */
        RiskBand riskBand;


        try {

            riskBand =
                    RiskBand.valueOf(
                            payment.riskBand()
                    );


        } catch (
                IllegalArgumentException
                |
                NullPointerException ex
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid trusted risk band"
            );
        }


        ApprovalLevel riskLevel =
                switch (
                        riskBand
                ) {

                    case LOW ->
                            ApprovalLevel.L1;

                    case MEDIUM ->
                            ApprovalLevel.L2;

                    case HIGH ->
                            ApprovalLevel.L3;
                };


        int trustedRiskLevel =
                switch (
                        riskLevel
                ) {

                    case L1 ->
                            1;

                    case L2 ->
                            2;

                    case L3 ->
                            3;
                };


        if (
                payment.riskApprovalLevel()
                        != trustedRiskLevel
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Risk band and risk approval "
                            + "level disagree"
            );
        }


        /*
         * -----------------------------------------------------
         * AMOUNT APPROVAL LEVEL
         * -----------------------------------------------------
         */
        if (
                payment.configurationVersionId()
                        == null
                ||
                payment.configurationVersionId()
                        <= 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Valid approval configuration "
                            + "version required"
            );
        }


        int amountLevelNumber =
                trustedData
                        .getAmountApprovalLevel(
                                payment
                                        .configurationVersionId(),

                                payment
                                        .paymentAmount()
                        );


        ApprovalLevel amountLevel =
                switch (
                        amountLevelNumber
                ) {

                    case 1 ->
                            ApprovalLevel.L1;

                    case 2 ->
                            ApprovalLevel.L2;

                    case 3 ->
                            ApprovalLevel.L3;

                    default ->
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "Invalid amount approval level"
                            );
                };


        /*
         * -----------------------------------------------------
         * REQUIRED APPROVAL LEVEL
         * -----------------------------------------------------
         *
         * Invoice due date does NOT alter routing.
         *
         * requiredLevel =
         *
         * MAX(
         *     amount approval level,
         *     risk approval level
         * )
         */
        ApprovalLevel requiredLevel =
                ApprovalService
                        .stricter(
                                amountLevel,
                                riskLevel
                        );


        int requiredLevelNumber =
                switch (
                        requiredLevel
                ) {

                    case L1 ->
                            1;

                    case L2 ->
                            2;

                    case L3 ->
                            3;
                };


        if (
                payment.requiredApprovalLevel()
                        != requiredLevelNumber
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment approval level disagrees "
                            + "with versioned risk "
                            + "and amount policy"
            );
        }


        /*
         * -----------------------------------------------------
         * SELECT INITIAL APPROVER
         * -----------------------------------------------------
         *
         * Normal routing remains unchanged.
         */
        Long approverId =
                approverSelector
                        .selectAtOrAboveLevel(
                                requiredLevel,
                                payment.requesterId(),
                                authorization
                        );


        /*
         * -----------------------------------------------------
         * SLA
         * -----------------------------------------------------
         */
        int slaSeconds =
                payment
                        .slaDurationSeconds();


        if (
                slaSeconds < 60
                ||
                slaSeconds % 60 != 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configured SLA must be a positive "
                            + "whole number of minutes"
            );
        }


        int slaMinutes =
                slaSeconds / 60;


        if (
                slaMinutes > 525600
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configured SLA exceeds "
                            + "supported maximum"
            );
        }


        /*
         * -----------------------------------------------------
         * CREATE ROUTE
         * -----------------------------------------------------
         */
        if (
                routeRepository
                        .findByPaymentId(
                                paymentId
                        )
                        .isEmpty()
        ) {

            approvalService.route(
                    new ApprovalRequests.Route(
                            paymentId,
                            payment.requesterId(),
                            payment.paymentAmount(),
                            riskBand,
                            amountLevel,
                            String.valueOf(
                                    payment
                                            .configurationVersionId()
                            )
                    )
            );
        }


        /*
         * -----------------------------------------------------
         * CREATE APPROVAL TASK
         * -----------------------------------------------------
         *
         * This is the important line:
         *
         * payment.invoiceDueDate()
         *
         * ApprovalService keeps the selected approver
         * unchanged and only caps the deadline.
         */
        ApprovalTask task =
                approvalService.create(
                        new ApprovalRequests.Task(
                                paymentId,
                                approverId,
                                slaMinutes,
                                payment.invoiceDueDate()
                        ),
                        authorization
                );


        return response(
                task
        );
    }


    /*
     * =========================================================
     * RESPONSE
     * =========================================================
     */
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
