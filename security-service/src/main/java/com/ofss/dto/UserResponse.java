package com.ofss.dto;

import com.ofss.entity.UserStatus;

import java.util.List;

public record UserResponse(

        Long userId,
        String username,
        String fullName,
        String email,
        UserStatus status,
        List<String> roles

) {
	
}