package com.ofss.configuration.readiness;

import com.ofss.configuration.rule.DraftRiskRuleRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configuration/versions/{versionId}/publication-readiness")
public class PublicationReadinessController {
    private final PublicationReadinessService service;
    public PublicationReadinessController(PublicationReadinessService service) { this.service = service; }
    @GetMapping
    public PublicationReadiness get(@PathVariable String versionId) {
        return service.get(DraftRiskRuleRequest.versionId(versionId));
    }
}
