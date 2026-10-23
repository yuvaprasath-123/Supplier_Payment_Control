package com.ofss.controller;

import com.ofss.dto.ApprovalRequests.Decision;
import com.ofss.dto.ApprovalRequests.Escalate;
import com.ofss.dto.ApprovalRequests.Reassign;
import com.ofss.dto.ApprovalRequests.Route;
import com.ofss.dto.ApprovalRequests.Task;

import com.ofss.entity.ApprovalLevel;
import com.ofss.entity.ApprovalRoute;
import com.ofss.entity.ApprovalStatus;
import com.ofss.entity.ApprovalTask;

import com.ofss.service.ApprovalService;

import jakarta.validation.Valid;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1")
public class ApprovalController {

    private final ApprovalService service;


    public ApprovalController(
            ApprovalService service
    ) {

        this.service =
                service;
    }


    /*
     * =========================================================
     * APPROVAL ROUTE
     * =========================================================
     */

    @PostMapping("/approval-routes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalRoute createRoute(
            @Valid
            @RequestBody
            Route request
    ) {

        return service.route(
                request
        );
    }


    @GetMapping(
            "/approval-routes/payment/{paymentId}"
    )
    public ApprovalRoute getRouteForPayment(
            @PathVariable
            Long paymentId
    ) {

        return service.routeForPayment(
                paymentId
        );
    }


    /*
     * Provides the explainable approval-routing result:
     *
     * MAX(
     *     amount approval level,
     *     risk approval level
     * )
     */
    @GetMapping(
            "/approval-routes/payment/{paymentId}/decision-explanation"
    )
    public Explanation getDecisionExplanation(
            @PathVariable
            Long paymentId
    ) {

        ApprovalRoute route =
                service.routeForPayment(
                        paymentId
                );


        return new Explanation(
                route.paymentId,
                route.amountApprovalLevel,
                route.riskApprovalLevel,
                route.requiredLevel,
                route.configurationVersion,
                route.explanation
        );
    }


    /*
     * =========================================================
     * APPROVAL TASK
     * =========================================================
     */

    @PostMapping("/approval-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalTask createTask(
            @Valid
            @RequestBody
            Task request,

            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String token
    ) {

        return service.create(
                request,
                token
        );
    }


    @GetMapping("/approval-tasks")
    public List<ApprovalTask> listTasks(

            @RequestParam(
                    required = false
            )
            Long paymentId,

            @RequestParam(
                    required = false
            )
            Long assignedApproverId,

            @RequestParam(
                    required = false
            )
            ApprovalStatus status
    ) {

        return service.list(
                paymentId,
                assignedApproverId,
                status
        );
    }


    @GetMapping(
            "/approval-tasks/{approvalTaskId}"
    )
    public ApprovalTask getTask(
            @PathVariable
            Long approvalTaskId
    ) {

        return service.get(
                approvalTaskId
        );
    }


    /*
     * =========================================================
     * APPROVAL DECISIONS
     * =========================================================
     */

    @PostMapping(
            "/approval-tasks/{approvalTaskId}/approve"
    )
    public ApprovalTask approve(
            @PathVariable
            Long approvalTaskId,

            @Valid
            @RequestBody
            Decision request,

            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String token
    ) {

        return service.decide(
                approvalTaskId,
                request,
                token,
                false,
                false
        );
    }


    @PostMapping(
            "/approval-tasks/{approvalTaskId}/reject"
    )
    public ApprovalTask reject(
            @PathVariable
            Long approvalTaskId,

            @Valid
            @RequestBody
            Decision request,

            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String token
    ) {

        return service.decide(
                approvalTaskId,
                request,
                token,
                true,
                false
        );
    }


    @PostMapping(
            "/approval-tasks/{approvalTaskId}/override-and-approve"
    )
    public ApprovalTask overrideAndApprove(
            @PathVariable
            Long approvalTaskId,

            @Valid
            @RequestBody
            Decision request,

            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String token
    ) {

        return service.decide(
                approvalTaskId,
                request,
                token,
                false,
                true
        );
    }


    /*
     * =========================================================
     * APPROVAL HISTORY
     * =========================================================
     */

    @GetMapping(
            "/approval-tasks/payment/{paymentId}/history"
    )
    public ApprovalService.History getHistory(
            @PathVariable
            Long paymentId
    ) {

        return service.history(
                paymentId
        );
    }


    /*
     * =========================================================
     * SLA STATUS
     * =========================================================
     *
     * dueAt is already calculated by BusinessCalendarService.
     *
     * Therefore this endpoint does not need to independently
     * calculate weekends, holidays, or working hours.
     *
     * It simply checks whether the business-calendar-aware
     * stored deadline has passed.
     */
    @GetMapping(
            "/approval-tasks/{approvalTaskId}/sla"
    )
    public Sla getSla(
            @PathVariable
            Long approvalTaskId
    ) {

        ApprovalTask task =
                service.get(
                        approvalTaskId
                );


        boolean expired =
                task.dueAt != null
                        && !OffsetDateTime.now()
                                .isBefore(
                                        task.dueAt
                                );


        return new Sla(
                task.id,
                task.slaMinutes,
                task.dueAt,
                expired,
                task.status
        );
    }


    /*
     * =========================================================
     * BACKUP REASSIGNMENT
     * =========================================================
     */

    @PostMapping(
            "/approval-tasks/{approvalTaskId}/reassign-to-backup"
    )
    public ApprovalTask reassignToBackup(
            @PathVariable
            Long approvalTaskId,

            @Valid
            @RequestBody
            Reassign request,

            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String token
    ) {

        return service.reassign(
                approvalTaskId,
                request,
                token
        );
    }


    /*
     * =========================================================
     * MANUAL ESCALATION
     * =========================================================
     */

    @PostMapping(
            "/approval-tasks/{approvalTaskId}/escalate-to-next-level"
    )
    public ApprovalTask escalateToNextLevel(
            @PathVariable
            Long approvalTaskId,

            @Valid
            @RequestBody
            Escalate request,

            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String token
    ) {

        return service.escalate(
                approvalTaskId,
                request,
                token
        );
    }


    /*
     * =========================================================
     * RESPONSE DTOs
     * =========================================================
     */

    public record Explanation(
            Long paymentId,
            ApprovalLevel amountLevel,
            ApprovalLevel riskLevel,
            ApprovalLevel requiredLevel,
            String configurationVersion,
            String explanation
    ) {
    }


    public record Sla(
            Long approvalTaskId,
            int slaMinutes,
            OffsetDateTime dueAt,
            boolean expired,
            ApprovalStatus status
    ) {
    }
}