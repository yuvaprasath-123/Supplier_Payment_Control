package com.ofss.configuration.publication;

import com.ofss.configuration.rule.DraftRiskRuleRequest;
import com.ofss.configuration.rule.ExactRiskRuleBody;
import com.ofss.configuration.security.JwtPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configuration/versions/{versionId}/publish")
public class ConfigurationPublicationController {
    private final ConfigurationPublicationService service;
    public ConfigurationPublicationController(ConfigurationPublicationService service) { this.service = service; }
    @PostMapping
    public ConfigurationPublicationService.PublishedVersion publish(@PathVariable String versionId,
            @RequestBody ExactRiskRuleBody body, @AuthenticationPrincipal JwtPrincipal actor) {
        return service.publish(DraftRiskRuleRequest.versionId(versionId), PublishConfigurationRequest.from(body.values()), actor.userId());
    }
}
