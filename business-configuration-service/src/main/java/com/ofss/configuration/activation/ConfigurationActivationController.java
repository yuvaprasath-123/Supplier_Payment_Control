package com.ofss.configuration.activation;

import com.ofss.configuration.rule.*;
import com.ofss.configuration.security.JwtPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configuration")
public class ConfigurationActivationController {
    private final ConfigurationActivationService service;
    public ConfigurationActivationController(ConfigurationActivationService service) { this.service=service; }
    @PostMapping("/versions/{versionId}/activate")
    public ConfigurationActivationService.ActiveSnapshot activate(@PathVariable String versionId,
            @RequestBody ExactRiskRuleBody body, @AuthenticationPrincipal JwtPrincipal actor) {
        return service.activate(DraftRiskRuleRequest.versionId(versionId), ActivationRequest.from(body.values()), actor.userId());
    }
    @GetMapping("/active")
    public ConfigurationActivationService.ActiveSnapshot active() { return service.active(); }
}
