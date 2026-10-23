package com.ofss.configuration.band;

import com.ofss.configuration.rule.DraftRiskRuleRequest;
import com.ofss.configuration.rule.ExactRiskRuleBody;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configuration/versions/{versionId}/risk-bands")
public class DraftRiskBandController {
    private final DraftRiskBandService service;
    public DraftRiskBandController(DraftRiskBandService service) { this.service = service; }
    @GetMapping
    public List<RiskBand.Response> list(@PathVariable String versionId) {
        return service.list(DraftRiskRuleRequest.versionId(versionId));
    }
    @PutMapping("/{bandCode}")
    public DraftRiskBandService.SavedBand put(@PathVariable String versionId, @PathVariable String bandCode,
            @RequestBody ExactRiskRuleBody body) {
        long id = DraftRiskRuleRequest.versionId(versionId);
        DraftRiskBandRequest.validateCode(bandCode);
        return service.put(id, bandCode, DraftRiskBandRequest.from(body.values()));
    }
}
