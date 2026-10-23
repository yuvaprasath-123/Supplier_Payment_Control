
package com.ofss.controller;

import com.ofss.dto.ApprovalWorkflowResponse;
import com.ofss.service.InternalApprovalAssignmentService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/approval-assignments")
public class InternalApprovalAssignmentController {

    private final InternalApprovalAssignmentService service;

    public InternalApprovalAssignmentController(
            InternalApprovalAssignmentService service
    ) {
        this.service = service;
    }

    @PostMapping("/{paymentId}")
    public ResponseEntity<ApprovalWorkflowResponse> assign(
            @PathVariable Long paymentId,
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization,
            Authentication authentication
    ) {

        /*
         * Do not trust the Authorization header alone.
         * Spring Security must first authenticate
         * the dedicated assignment-service identity.
         */
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getAuthorities()
                        .stream()
                        .noneMatch(authority ->
                                "ROLE_APPROVAL_ASSIGNMENT_SERVICE"
                                        .equals(
                                                authority.getAuthority()
                                        )
                        )) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Approval assignment service identity required"
            );
        }

        ApprovalWorkflowResponse result =
                service.assign(
                        paymentId,
                        authorization
                );

        return ResponseEntity.ok(result);
    }
}
