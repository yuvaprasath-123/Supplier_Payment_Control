package com.ofss.dto;

import com.ofss.entity.ApprovalLevel;
import com.ofss.entity.RiskBand;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ApprovalRequests {

    private ApprovalRequests() {
    }


    /*
     * =========================================================
     * APPROVAL ROUTE REQUEST
     * =========================================================
     *
     * Determines the required approval level using:
     *
     * MAX(
     *     amount approval level,
     *     risk approval level
     * )
     */
    public record Route(

            @NotNull
            @Positive
            Long paymentId,

            @NotNull
            @Positive
            Long requesterId,

            @NotNull
            @DecimalMin("0.01")
            @Digits(
                    integer = 17,
                    fraction = 2
            )
            BigDecimal paymentAmount,

            @NotNull
            RiskBand riskBand,

            @NotNull
            ApprovalLevel amountApprovalLevel,

            @Size(max = 100)
            String configurationVersion

    ) {
    }


    /*
     * =========================================================
     * APPROVAL TASK REQUEST
     * =========================================================
     *
     * invoiceDueDate is used ONLY to determine the
     * effective SLA deadline.
     *
     * It does NOT change:
     *
     * - required approval level
     * - assigned approver
     * - normal amount/risk routing
     *
     * Example:
     *
     * Normal SLA deadline:
     * 28/10/2026 17:00
     *
     * Invoice due date:
     * 27/10/2026
     *
     * Effective deadline:
     * 27/10/2026 17:00
     *
     * Approval level remains unchanged.
     */
    public record Task(

            @NotNull
            @Positive
            Long paymentId,

            @NotNull
            @Positive
            Long assignedApproverId,

            @Min(1)
            @Max(525600)
            Integer slaMinutes,

            @NotNull
            LocalDate invoiceDueDate

    ) {
    }


    /*
     * =========================================================
     * APPROVAL DECISION
     * =========================================================
     */
    public record Decision(

            @NotNull
            @Positive
            Long approverId,

            @Size(max = 2000)
            String remark

    ) {
    }


    /*
     * =========================================================
     * REASSIGN TO BACKUP APPROVER
     * =========================================================
     */
    public record Reassign(

            @Positive
            Long backupApproverId,

            @NotBlank
            @Size(max = 2000)
            String remark

    ) {
    }


    /*
     * =========================================================
     * MANUAL ESCALATION
     * =========================================================
     */
    public record Escalate(

            @Positive
            Long assignedApproverId,

            @NotBlank
            @Size(max = 2000)
            String remark

    ) {
    }
}