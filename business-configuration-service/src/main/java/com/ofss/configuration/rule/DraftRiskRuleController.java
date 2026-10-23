package com.ofss.configuration.rule;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configuration/versions/{versionId}")
public class DraftRiskRuleController {
    private final DraftRiskRuleService service;
    public DraftRiskRuleController(DraftRiskRuleService service) { this.service = service; }
    @GetMapping
    public ConfigurationVersionDetails.Response details(@PathVariable String versionId) {
        return service.details(DraftRiskRuleRequest.versionId(versionId));
    }
    @GetMapping("/risk-rules")
    public List<RiskRule.Response> rules(@PathVariable String versionId) {
        return service.rules(DraftRiskRuleRequest.versionId(versionId));
    }
    @PutMapping("/risk-rules/{ruleCode}")
    public DraftRiskRuleService.SavedRule put(@PathVariable String versionId, @PathVariable String ruleCode,
            @RequestBody ExactRiskRuleBody body) {
        long id = DraftRiskRuleRequest.versionId(versionId);
        DraftRiskRuleRequest.validateRuleCode(ruleCode);
        return service.put(id, ruleCode, DraftRiskRuleRequest.from(body.values()));
    }
}
