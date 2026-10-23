package com.ofss.dto;

import com.ofss.entity.ApprovalLevel;
import jakarta.validation.constraints.NotNull;

public record ApprovalAuthorityRequest(

        @NotNull(message = "Approval level is required")
        ApprovalLevel approvalLevel

) {}
