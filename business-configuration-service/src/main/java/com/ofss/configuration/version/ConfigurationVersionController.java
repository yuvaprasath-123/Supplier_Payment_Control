package com.ofss.configuration.version;

import java.util.List;
import java.util.Map;

import com.ofss.configuration.security.JwtPrincipal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigurationVersionController {

    public static final String PATH =
            "/api/v1/configuration/versions";

    private final ConfigurationVersionService service;

    public ConfigurationVersionController(
            ConfigurationVersionService service) {
        this.service = service;
    }

    @GetMapping(PATH)
    public List<ConfigurationVersionResponse> listVersions() {
        return service.listVersions();
    }

    @PostMapping(PATH)
    public ResponseEntity<ConfigurationVersionResponse> createVersion(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal JwtPrincipal actor) {

        var request =
                CreateConfigurationVersionRequest.from(body);

        return ResponseEntity
                .status(201)
                .body(
                        service.createDraft(
                                request,
                                actor.userId()
                        )
                );
    }
}