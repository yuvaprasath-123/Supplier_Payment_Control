package com.ofss.service;

import com.ofss.dto.ApprovalRequests.*;
import com.ofss.entity.*;
import com.ofss.event.ApprovalDecidedEvent;
import com.ofss.repository.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ApprovalService {

    private final ApprovalRouteRepository routes;

    private final ApprovalTaskRepository tasks;

    private final ApprovalDecisionRepository decisions;

    private final ApprovalEscalationRepository escalations;

    private final BackupApproverRepository backups;

    private final ApproverAuthorization authorization;

    private final ApprovalOutboxService outboxService;

    /*
     * NEW:
     *
     * Calculates SLA deadlines using:
     *
     * Monday-Friday
     * 09:00-17:00
     * Asia/Kolkata
     *
     * Saturday/Sunday = zero SLA time
     * Configured holidays = zero SLA time
     */
    private final BusinessCalendarService
            businessCalendarService;

    private final int defaultSla;


    public ApprovalService(
            ApprovalRouteRepository routes,
            ApprovalTaskRepository tasks,
            ApprovalDecisionRepository decisions,
            ApprovalEscalationRepository escalations,
            BackupApproverRepository backups,
            ApproverAuthorization authorization,
            ApprovalOutboxService outboxService,
            BusinessCalendarService businessCalendarService,

            @Value("${approval.default-sla-minutes:60}")
            int defaultSla
    ) {

        this.routes = routes;

        this.tasks = tasks;

        this.decisions = decisions;

        this.escalations = escalations;

        this.backups = backups;

        this.authorization = authorization;

        this.outboxService = outboxService;

        this.businessCalendarService =
                businessCalendarService;

        this.defaultSla =
                defaultSla;
    }


    /*
     * =========================================================
     * APPROVAL ROUTE
     * =========================================================
     */

    public ApprovalRoute route(
            Route r
    ) {

        if (routes
                .findByPaymentId(
                        r.paymentId()
                )
                .isPresent()) {

            throw conflict(
                    "Payment already has a route"
            );
        }


        ApprovalRoute v =
                new ApprovalRoute();


        v.paymentId =
                r.paymentId();

        v.requesterId =
                r.requesterId();

        v.paymentAmount =
                r.paymentAmount();

        v.riskBand =
                r.riskBand();

        v.amountApprovalLevel =
                r.amountApprovalLevel();


        v.riskApprovalLevel =
                ApprovalLevel.values()[
                        r.riskBand().ordinal()
                ];


        v.requiredLevel =
                stricter(
                        v.amountApprovalLevel,
                        v.riskApprovalLevel
                );


        v.configurationVersion =
                r.configurationVersion();


        v.createdAt =
                OffsetDateTime.now();


        v.explanation =
                "MAX(amount level "
                + v.amountApprovalLevel
                + ", risk level "
                + v.riskApprovalLevel
                + ") = "
                + v.requiredLevel;


        return routes.save(
                v
        );
    }


    public static ApprovalLevel stricter(
            ApprovalLevel amount,
            ApprovalLevel risk
    ) {

        return amount.ordinal()
                >= risk.ordinal()
                ? amount
                : risk;
    }


    @Transactional(readOnly = true)
    public ApprovalRoute routeForPayment(
            Long id
    ) {

        return routes
                .findByPaymentId(
                        id
                )
                .orElseThrow(
                        () ->
                                missing(
                                        "Approval route not found"
                                )
                );
    }


    /*
     * =========================================================
     * CREATE APPROVAL TASK
     * =========================================================
     *
     * This is the INITIAL SLA calculation.
     *
     * Important:
     *
     * Requests can be created at any time.
     *
     * However, SLA time counts only:
     *
     * Monday-Friday
     * 09:00-17:00 IST
     *
     * If the task is created outside working hours,
     * on a weekend, or on a configured holiday,
     * the SLA waits until the next working period.
     */

    public ApprovalTask create(
            Task r,
            String token
    ) {

        if (tasks
                .findByPaymentId(
                        r.paymentId()
                )
                .isPresent()) {

            throw conflict(
                    "Payment already has an approval task"
            );
        }


        ApprovalRoute route =
                routeForPayment(
                        r.paymentId()
                );


        if (route.requesterId.equals(
                r.assignedApproverId()
        )) {

            throw conflict(
                    "Requester cannot be assigned to their own payment"
            );
        }


        authorization.assignee(
                token,
                r.assignedApproverId(),
                route.requiredLevel
        );


        ApprovalTask t =
                new ApprovalTask();


        t.paymentId =
                route.paymentId;

        t.routeId =
                route.id;

        t.requesterId =
                route.requesterId;

        t.requiredLevel =
                route.requiredLevel;

        t.assignedApproverId =
                r.assignedApproverId();

        t.status =
                ApprovalStatus.PENDING_APPROVAL;


        /*
         * Record the true creation/assignment time.
         */
        t.createdAt =
                OffsetDateTime.now();


        t.slaMinutes =
                r.slaMinutes() == null
                        ? defaultSla
                        : r.slaMinutes();


        if (t.slaMinutes < 1
                || t.slaMinutes > 525600) {

            throw conflict(
                    "SLA must be between 1 and 525600 minutes"
            );
        }


        /*
         * =====================================================
         * HOLIDAY-AWARE INITIAL SLA
         * =====================================================
         *
         * OLD:
         *
         * t.dueAt =
         *     t.createdAt.plusMinutes(
         *         t.slaMinutes
         *     );
         *
         * NEW:
         *
         * Calculate the deadline using business working time.
         */
        t.dueAt =
                businessCalendarService
                        .calculateDueAt(
                                t.createdAt,
                                t.slaMinutes
                        );


        return tasks.save(
                t
        );
    }


    /*
     * =========================================================
     * GET APPROVAL TASK
     * =========================================================
     */

    @Transactional(readOnly = true)
    public ApprovalTask get(
            Long id
    ) {

        return tasks
                .findById(
                        id
                )
                .orElseThrow(
                        () ->
                                missing(
                                        "Approval task not found"
                                )
                );
    }


    /*
     * =========================================================
     * LIST APPROVAL TASKS
     * =========================================================
     */

    @Transactional(readOnly = true)
    public List<ApprovalTask> list(
            Long paymentId,
            Long assignedApproverId,
            ApprovalStatus status
    ) {

        return tasks.findAll(
                (
                        root,
                        query,
                        cb
                ) -> {

                    var predicates =
                            new ArrayList<
                                    jakarta.persistence.criteria.Predicate
                            >();


                    if (paymentId != null) {

                        predicates.add(
                                cb.equal(
                                        root.get(
                                                "paymentId"
                                        ),
                                        paymentId
                                )
                        );
                    }


                    if (assignedApproverId != null) {

                        predicates.add(
                                cb.equal(
                                        root.get(
                                                "assignedApproverId"
                                        ),
                                        assignedApproverId
                                )
                        );
                    }


                    if (status != null) {

                        predicates.add(
                                cb.equal(
                                        root.get(
                                                "status"
                                        ),
                                        status
                                )
                        );
                    }


                    return cb.and(
                            predicates.toArray(
                                    jakarta.persistence.criteria.Predicate[]::new
                            )
                    );
                },

                Sort.by(
                        "createdAt"
                ).descending()
        );
    }


    /*
     * =========================================================
     * APPROVE / REJECT / MANAGER OVERRIDE
     * =========================================================
     *
     * Save the approval decision and corresponding
     * outbox event in ONE Oracle transaction.
     *
     * If inserting the outbox row fails, the
     * approval decision and task status update
     * roll back too.
     */

    public ApprovalTask decide(
            Long id,
            Decision r,
            String token,
            boolean reject,
            boolean override
    ) {

        ApprovalTask t =
                locked(
                        id
                );


        pending(
                t,
                override
        );


        if (t.requesterId.equals(
                r.approverId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Self approval is forbidden"
            );
        }


        if (!override
                && !t.assignedApproverId.equals(
                        r.approverId()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Task is assigned to another approver"
            );
        }


        /*
         * Rejection and manager override both
         * require a justification.
         */
        if ((reject || override)
                && (
                    r.remark() == null
                    || r.remark().isBlank()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection and manager override require a justification"
            );
        }


        authorization.decision(
                token,
                r.approverId(),
                t.requiredLevel,
                override
        );


        /*
         * dueAt is already business-calendar-aware.
         *
         * Once the stored deadline has expired,
         * normal approvers cannot act until the
         * task is reassigned/escalated.
         *
         * Manager override remains allowed.
         */
        if (!override
                && !OffsetDateTime.now()
                        .isBefore(
                                t.dueAt
                        )) {

            throw conflict(
                    "SLA expired; reassign or escalate before deciding"
            );
        }


        t.status =
                reject
                        ? ApprovalStatus.REJECTED
                        : ApprovalStatus.APPROVED;


        t.decidedBy =
                r.approverId();


        t.remark =
                r.remark();


        ApprovalDecision d =
                new ApprovalDecision();


        d.taskId =
                t.id;

        d.paymentId =
                t.paymentId;

        d.approverId =
                r.approverId();


        d.decision =
                override
                        ? "OVERRIDE_APPROVED"
                        : t.status.name();


        d.remark =
                r.remark();


        d.decidedAt =
                OffsetDateTime.now();


        /*
         * Flush the saved decision so its generated ID
         * is available for the outbox foreign key.
         *
         * This flush does NOT commit the transaction.
         */
        d =
                decisions.saveAndFlush(
                        d
                );


        if (d.id == null
                || d.id <= 0) {

            throw new IllegalStateException(
                    "Approval decision ID was not generated"
            );
        }


        ApprovalDecidedEvent event =
                new ApprovalDecidedEvent(
                        d.paymentId,
                        d.taskId,
                        d.id,
                        d.approverId,
                        d.decision,
                        d.decidedAt
                );


        /*
         * ApprovalOutboxService.record() requires
         * an existing transaction (MANDATORY).
         *
         * The approval decision and outbox insertion
         * commit or roll back together.
         */
        outboxService.record(
                event
        );


        return t;
    }


    /*
     * =========================================================
     * MANUAL REASSIGNMENT TO BACKUP APPROVER
     * =========================================================
     */

    public ApprovalTask reassign(
            Long id,
            Reassign r,
            String token
    ) {

        ApprovalTask t =
                locked(
                        id
                );


        pending(
                t,
                false
        );


        expired(
                t
        );


        authorization.manager(
                token
        );


        Long backup =
                r.backupApproverId();


        if (backup == null) {

            backup =
                    backups
                            .findByApproverIdAndApprovalLevel(
                                    t.assignedApproverId,
                                    t.requiredLevel
                            )
                            .orElseThrow(
                                    () ->
                                            missing(
                                                    "No backup mapping; provide backupApproverId"
                                            )
                            )
                            .backupApproverId;
        }


        checkAssignee(
                t,
                backup,
                t.requiredLevel,
                token
        );


        ApprovalEscalation e =
                escalation(
                        t,
                        "REASSIGNED_TO_BACKUP",
                        r.remark()
                );


        t.assignedApproverId =
                backup;


        /*
         * =====================================================
         * HOLIDAY-AWARE BACKUP APPROVER SLA
         * =====================================================
         *
         * The newly assigned approver receives
         * a fresh SLA period.
         *
         * Weekend, holiday and out-of-hours time
         * contributes zero SLA minutes.
         */
        OffsetDateTime reassignedAt =
                OffsetDateTime.now();


        t.dueAt =
                businessCalendarService
                        .calculateDueAt(
                                reassignedAt,
                                t.slaMinutes
                        );


        e.assignedApproverId =
                backup;


        escalations.save(
                e
        );


        return t;
    }


    /*
     * =========================================================
     * MANUAL ESCALATION
     * =========================================================
     */

    public ApprovalTask escalate(
            Long id,
            Escalate r,
            String token
    ) {

        ApprovalTask t =
                locked(
                        id
                );


        pending(
                t,
                false
        );


        expired(
                t
        );


        authorization.manager(
                token
        );


        ApprovalEscalation e =
                escalation(
                        t,
                        "ESCALATED",
                        r.remark()
                );


        /*
         * If already L3, escalation terminates
         * in manual review.
         */
        if (t.requiredLevel
                == ApprovalLevel.L3) {

            t.status =
                    ApprovalStatus.MANUAL_REVIEW;


            e.action =
                    "MANUAL_REVIEW";


        } else {


            ApprovalLevel next =
                    ApprovalLevel.values()[
                            t.requiredLevel.ordinal()
                            + 1
                    ];


            checkAssignee(
                    t,
                    r.assignedApproverId(),
                    next,
                    token
            );


            t.requiredLevel =
                    next;


            t.assignedApproverId =
                    r.assignedApproverId();


            t.status =
                    ApprovalStatus.ESCALATED;


            /*
             * =================================================
             * HOLIDAY-AWARE MANUAL ESCALATION SLA
             * =================================================
             *
             * A newly escalated approver receives
             * a fresh business-time SLA.
             */
            OffsetDateTime escalatedAt =
                    OffsetDateTime.now();


            t.dueAt =
                    businessCalendarService
                            .calculateDueAt(
                                    escalatedAt,
                                    t.slaMinutes
                            );
        }


        e.requiredLevel =
                t.requiredLevel;


        e.assignedApproverId =
                t.assignedApproverId;


        escalations.save(
                e
        );


        return t;
    }


    /*
     * =========================================================
     * HISTORY
     * =========================================================
     */

    @Transactional(readOnly = true)
    public History history(
            Long paymentId
    ) {

        ApprovalTask t =
                tasks
                        .findByPaymentId(
                                paymentId
                        )
                        .orElseThrow(
                                () ->
                                        missing(
                                                "Approval task not found"
                                        )
                        );


        return new History(
                t,

                decisions
                        .findByPaymentIdOrderByDecidedAtAsc(
                                paymentId
                        ),

                escalations
                        .findByPaymentIdOrderByOccurredAtAsc(
                                paymentId
                        )
        );
    }


    /*
     * =========================================================
     * APPROVER VALIDATION
     * =========================================================
     */

    private void checkAssignee(
            ApprovalTask t,
            Long id,
            ApprovalLevel level,
            String token
    ) {

        if (id == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Next approver is required"
            );
        }


        if (t.requesterId.equals(
                id
        )
                || t.assignedApproverId.equals(
                        id
                )) {

            throw conflict(
                    "Choose a different approver who is not the requester"
            );
        }


        authorization.assignee(
                token,
                id,
                level
        );
    }


    /*
     * =========================================================
     * CREATE ESCALATION AUDIT ENTRY
     * =========================================================
     */

    private ApprovalEscalation escalation(
            ApprovalTask t,
            String action,
            String remark
    ) {

        ApprovalEscalation e =
                new ApprovalEscalation();


        e.taskId =
                t.id;

        e.paymentId =
                t.paymentId;


        e.previousApproverId =
                t.assignedApproverId;


        e.previousLevel =
                t.requiredLevel;


        e.requiredLevel =
                t.requiredLevel;


        e.action =
                action;


        e.remark =
                remark;


        e.occurredAt =
                OffsetDateTime.now();


        return e;
    }


    /*
     * =========================================================
     * PESSIMISTIC TASK LOCK
     * =========================================================
     */

    private ApprovalTask locked(
            Long id
    ) {

        return tasks
                .findForUpdate(
                        id
                )
                .orElseThrow(
                        () ->
                                missing(
                                        "Approval task not found"
                                )
                );
    }


    /*
     * =========================================================
     * PENDING STATE CHECK
     * =========================================================
     */

    private void pending(
            ApprovalTask t,
            boolean override
    ) {

        if (t.status
                != ApprovalStatus.PENDING_APPROVAL

                && t.status
                != ApprovalStatus.ESCALATED

                && !(
                    override
                    && t.status
                        == ApprovalStatus.MANUAL_REVIEW
                )) {

            throw conflict(
                    "Task is closed or awaiting manual review"
            );
        }
    }


    /*
     * =========================================================
     * SLA EXPIRY CHECK
     * =========================================================
     *
     * No separate weekend/holiday check is required here.
     *
     * dueAt was already calculated using the
     * BusinessCalendarService.
     */

    private void expired(
            ApprovalTask t
    ) {

        if (OffsetDateTime.now()
                .isBefore(
                        t.dueAt
                )) {

            throw conflict(
                    "SLA has not expired"
            );
        }
    }


    /*
     * =========================================================
     * HTTP HELPERS
     * =========================================================
     */

    private ResponseStatusException conflict(
            String reason
    ) {

        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                reason
        );
    }


    private ResponseStatusException missing(
            String reason
    ) {

        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                reason
        );
    }


    /*
     * =========================================================
     * HISTORY RESPONSE
     * =========================================================
     */

    public record History(
            ApprovalTask task,
            List<ApprovalDecision> decisions,
            List<ApprovalEscalation> escalations
    ) {
    }
}

