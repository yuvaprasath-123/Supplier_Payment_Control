package com.ofss.configuration.threshold;

import com.ofss.configuration.rule.DraftRiskRuleRequest;
import com.ofss.configuration.rule.ExactRiskRuleBody;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configuration/versions/{versionId}/approval-thresholds")
public class DraftApprovalThresholdController {
    private final DraftApprovalThresholdService service;
    public DraftApprovalThresholdController(DraftApprovalThresholdService service) { this.service = service; }
    @GetMapping
    public List<ApprovalThreshold.Response> list(@PathVariable String versionId) {
        return service.list(DraftRiskRuleRequest.versionId(versionId));
    }
    @PutMapping("/{approvalLevel}")
    public DraftApprovalThresholdService.SavedThreshold put(@PathVariable String versionId,
            @PathVariable String approvalLevel, @RequestBody ExactRiskRuleBody body) {
        return service.put(DraftRiskRuleRequest.versionId(versionId),
                DraftApprovalThresholdRequest.approvalLevel(approvalLevel), DraftApprovalThresholdRequest.from(body.values()));
    }
}
