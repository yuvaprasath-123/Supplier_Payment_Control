package com.ofss.dto;

public record DuplicateCheckResponse(

        boolean duplicate,

        String message

) {
}