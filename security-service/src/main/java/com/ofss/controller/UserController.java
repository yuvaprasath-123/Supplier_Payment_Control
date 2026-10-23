package com.ofss.controller;

import com.ofss.dto.*;

import com.ofss.entity.ApprovalLevel;

import com.ofss.service.UserService;

import jakarta.validation.Valid;



import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")

public class UserController {

    private final UserService userService;
    
    public UserController(UserService userService) {
    	this.userService = userService;
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(
            @Valid
            @RequestBody
            CreateUserRequest request
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        userService.createUser(
                                request
                        )
                );
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getUsers() {

        return ResponseEntity.ok(
                userService.getUsers()
        );
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable
            Long userId
    ) {

        return ResponseEntity.ok(
                userService.getUser(
                        userId
                )
        );
    }

    @PutMapping(
            "/users/{userId}/roles"
    )
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ResponseEntity<UserResponse> assignRoles(

            @PathVariable
            Long userId,

            @Valid
            @RequestBody
            AssignRolesRequest request
    ) {

        return ResponseEntity.ok(
                userService.assignRoles(
                        userId,
                        request
                )
        );
    }

    @GetMapping(
            "/users/{userId}/roles"
    )
    public ResponseEntity<List<String>> getRoles(
            @PathVariable
            Long userId
    ) {

        return ResponseEntity.ok(
                userService.getRoles(
                        userId
                )
        );
    }

    @PutMapping(
            "/users/{userId}/approval-authority"
    )
    @PreAuthorize("hasRole('FINANCE_MANAGER')")
    public ResponseEntity<ApprovalAuthorityResponse>
    assignAuthority(

            @PathVariable
            Long userId,

            @Valid
            @RequestBody
            ApprovalAuthorityRequest request
    ) {

        return ResponseEntity.ok(
                userService.assignApprovalAuthority(
                        userId,
                        request
                )
        );
    }

    @GetMapping("/approvers")
    public ResponseEntity<List<ApprovalAuthorityResponse>>
    getApprovers(

            @RequestParam
            ApprovalLevel approvalLevel
    ) {

        return ResponseEntity.ok(
                userService.findApprovers(
                        approvalLevel
                )
        );
    }
}