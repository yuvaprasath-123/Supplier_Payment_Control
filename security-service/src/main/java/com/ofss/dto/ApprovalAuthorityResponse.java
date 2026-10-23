package com.ofss.dto;

import com.ofss.entity.ApprovalLevel;

public record ApprovalAuthorityResponse(

        Long userId,
        String username,
        String fullName,
        ApprovalLevel approvalLevel,
        Boolean active

) {}