package com.ofss.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/v1/validation"
)
public class ValidationHealthController {

    @GetMapping(
            "/health"
    )
    public ResponseEntity<
            Map<String, String>
            > health() {

        return ResponseEntity.ok(
                Map.of(
                        "service",
                        "validation-service",
                        "status",
                        "UP"
                )
        );
    }
}