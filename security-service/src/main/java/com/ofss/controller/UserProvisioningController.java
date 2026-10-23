package com.ofss.controller;

import com.ofss.dto.CreateRequesterRequest;
import com.ofss.dto.UserResponse;

import com.ofss.service.UserProvisioningAuthorizationService;
import com.ofss.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-provisioning")
public class UserProvisioningController {

    private final UserService
            userService;

    private final UserProvisioningAuthorizationService
            authorizationService;

    public UserProvisioningController(
            UserService userService,
            UserProvisioningAuthorizationService authorizationService
    ) {

        this.userService =
                userService;

        this.authorizationService =
                authorizationService;
    }

    @PostMapping("/requesters")
    public ResponseEntity<UserResponse>
    createRequester(
            @Valid
            @RequestBody
            CreateRequesterRequest request
    ) {

        authorizationService
                .verifyCanCreateRequester();

        UserResponse response =
                userService
                        .createRequester(
                                request
                        );

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        response
                );
    }
}