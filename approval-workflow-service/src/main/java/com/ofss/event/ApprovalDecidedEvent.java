
package com.ofss.event;

import java.time.OffsetDateTime;

public record ApprovalDecidedEvent(

        Long paymentId,

        Long approvalTaskId,

        Long approvalDecisionId,

        Long approverId,

        String decision,

        OffsetDateTime decidedAt

) {

    public ApprovalDecidedEvent {

        if (paymentId == null || paymentId <= 0) {
            throw new IllegalArgumentException(
                    "Valid payment ID is required"
            );
        }

        if (approvalTaskId == null || approvalTaskId <= 0) {
            throw new IllegalArgumentException(
                    "Valid approval task ID is required"
            );
        }

        if (approvalDecisionId == null
                || approvalDecisionId <= 0) {
            throw new IllegalArgumentException(
                    "Valid approval decision ID is required"
            );
        }

        if (approverId == null || approverId <= 0) {
            throw new IllegalArgumentException(
                    "Valid approver ID is required"
            );
        }

        if (decision == null || decision.isBlank()) {
            throw new IllegalArgumentException(
                    "Approval decision is required"
            );
        }

        if (decidedAt == null) {
            throw new IllegalArgumentException(
                    "Approval decision timestamp is required"
            );
        }
    }
}
