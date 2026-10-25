package com.ofss.service;

import com.ofss.dto.ApprovalRequests;
import com.ofss.dto.ApprovalWorkflowResponse;
import com.ofss.dto.CreateApprovalWorkflowRequest;
import com.ofss.dto.DecisionRequest;
import com.ofss.dto.EscalateWorkflowRequest;

import com.ofss.entity.ApprovalTask;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * Compatibility adapter.
 *
 * New workflow creation should preferably use the
 * route + task APIs directly.
 */
@Service
public class ApprovalWorkflowService {

    private final ApprovalService service;


    public ApprovalWorkflowService(
            ApprovalService service
    ) {
        this.service = service;
    }


    /*
     * =========================================================
     * CREATE WORKFLOW
     * =========================================================
     */
    @Transactional
    public ApprovalWorkflowResponse create(
            CreateApprovalWorkflowRequest request,
            String token
    ) {

        /*
         * Create approval route.
         *
         * Required approval level remains based on:
         *
         * MAX(
         *     amount approval level,
         *     risk approval level
         * )
         */
        service.route(
                new ApprovalRequests.Route(
                        request.paymentId(),
                        request.requesterId(),
                        request.paymentAmount(),
                        request.riskBand(),
                        request.amountApprovalLevel(),
                        null
                )
        );


        /*
         * IMPORTANT:
         *
         * The legacy CreateApprovalWorkflowRequest
         * currently does not contain invoiceDueDate.
         *
         * Therefore this compatibility endpoint
         * passes null.
         *
         * ApprovalService.calculateEffectiveDueAt(...)
         * must treat null as:
         *
         * "use normal business SLA deadline".
         *
         * The normal internal assignment flow will
         * eventually provide the actual invoice due date.
         */
        ApprovalTask task =
                service.create(
                        new ApprovalRequests.Task(
                                request.paymentId(),
                                request.assignedApproverId(),
                                request.slaMinutes(),
                                null
                        ),
                        token
                );


        return response(
                task
        );
    }


    /*
     * =========================================================
     * GET WORKFLOW
     * =========================================================
     */
    public ApprovalWorkflowResponse get(
            Long id
    ) {

        return response(
                service.get(id)
        );
    }


    /*
     * =========================================================
     * APPROVE
     * =========================================================
     */
    public ApprovalWorkflowResponse approve(
            Long id,
            DecisionRequest request,
            String token
    ) {

        ApprovalTask task =
                service.decide(
                        id,
                        new ApprovalRequests.Decision(
                                request.approverId(),
                                request.remark()
                        ),
                        token,
                        false,
                        false
                );


        return response(
                task
        );
    }


    /*
     * =========================================================
     * REJECT
     * =========================================================
     */
    public ApprovalWorkflowResponse reject(
            Long id,
            DecisionRequest request,
            String token
    ) {

        ApprovalTask task =
                service.decide(
                        id,
                        new ApprovalRequests.Decision(
                                request.approverId(),
                                request.remark()
                        ),
                        token,
                        true,
                        false
                );


        return response(
                task
        );
    }


    /*
     * =========================================================
     * ESCALATE
     * =========================================================
     */
    public ApprovalWorkflowResponse escalate(
            Long id,
            EscalateWorkflowRequest request,
            String token
    ) {

        ApprovalTask task =
                service.escalate(
                        id,
                        new ApprovalRequests.Escalate(
                                request.backupApproverId(),
                                request.remark()
                        ),
                        token
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