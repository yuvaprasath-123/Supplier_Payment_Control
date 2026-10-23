package com.ofss.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRequesterRequest(

        @NotBlank(
                message = "Username is required"
        )
        @Size(
                min = 4,
                max = 50,
                message = "Username must be between 4 and 50 characters"
        )
        String username,

        @NotBlank(
                message = "Password is required"
        )
        @Size(
                min = 8,
                max = 100,
                message = "Password must be at least 8 characters"
        )
        String password,

        @NotBlank(
                message = "Full name is required"
        )
        String fullName,

        @NotBlank(
                message = "Email is required"
        )
        @Email(
                message = "Email must be valid"
        )
        String email
) {
}