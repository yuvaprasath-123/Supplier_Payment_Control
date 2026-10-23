package com.ofss.dto;

import java.util.List;

public record LoginResponse(

        String accessToken,
        String tokenType,
        Long userId,
        String username,
        String fullName,
        List<String> roles

) {}