package com.ofss.service;

import com.ofss.entity.ApprovalAuthority;
import com.ofss.entity.ApprovalLevel;
import com.ofss.repository.ApprovalAuthorityRepository;
import com.ofss.security.AuthUtil;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserProvisioningAuthorizationService {

    private final AuthUtil authUtil;

    private final ApprovalAuthorityRepository
            approvalAuthorityRepository;

    public UserProvisioningAuthorizationService(
            AuthUtil authUtil,
            ApprovalAuthorityRepository
                    approvalAuthorityRepository
    ) {

        this.authUtil =
                authUtil;

        this.approvalAuthorityRepository =
                approvalAuthorityRepository;
    }

    public void verifyCanCreateRequester() {

        Long userId =
                authUtil.getCurrentUserId();

        List<String> roles =
                authUtil.getCurrentUserRoles();

        if (
                roles.contains(
                        "FINANCE_MANAGER"
                )
        ) {

            return;
        }

        if (
                !roles.contains(
                        "FINANCE_APPROVER"
                )
        ) {

            throw new AccessDeniedException(
                    "You are not authorized to create requester accounts"
            );
        }

        ApprovalAuthority authority =
                approvalAuthorityRepository
                        .findByUserId(
                                userId
                        )
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Approval authority is required"
                                        )
                        );

        if (
                !Boolean.TRUE.equals(
                        authority.getActive()
                )
        ) {

            throw new AccessDeniedException(
                    "Approval authority is inactive"
            );
        }

        ApprovalLevel level =
                authority.getApprovalLevel();

        if (
                level != ApprovalLevel.L1
                        &&
                        level != ApprovalLevel.L2
        ) {

            throw new AccessDeniedException(
                    "Only L1, L2 or Finance Manager users may create requester accounts"
            );
        }
    }
}