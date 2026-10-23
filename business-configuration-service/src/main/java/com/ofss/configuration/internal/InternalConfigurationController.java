package com.ofss.configuration.internal;

import com.ofss.configuration.activation.ConfigurationActivationService;
import com.ofss.configuration.rule.DraftRiskRuleRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/configuration")
public class InternalConfigurationController {
    private final ConfigurationActivationService service;
    public InternalConfigurationController(ConfigurationActivationService service) { this.service=service; }
    @GetMapping("/active") public ConfigurationActivationService.ActiveSnapshot active() { return service.active(); }
    @GetMapping("/versions/{versionId}") public ConfigurationActivationService.PublishedSnapshot pinned(@PathVariable String versionId) {
        return service.published(DraftRiskRuleRequest.versionId(versionId));
    }
}
