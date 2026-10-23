package com.ofss.configuration.sla;

import com.ofss.configuration.rule.DraftRiskRuleRequest;
import com.ofss.configuration.rule.ExactRiskRuleBody;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configuration/versions/{versionId}/approval-slas")
public class DraftApprovalSlaController {
    private final DraftApprovalSlaService service;
    public DraftApprovalSlaController(DraftApprovalSlaService service) { this.service = service; }
    @GetMapping
    public List<ApprovalSla.Response> list(@PathVariable String versionId) {
        return service.list(DraftRiskRuleRequest.versionId(versionId));
    }
    @PutMapping("/{approvalLevel}")
    public DraftApprovalSlaService.SavedSla put(@PathVariable String versionId,
            @PathVariable String approvalLevel, @RequestBody ExactRiskRuleBody body) {
        return service.put(DraftRiskRuleRequest.versionId(versionId), DraftApprovalSlaRequest.approvalLevel(approvalLevel),
                DraftApprovalSlaRequest.from(body.values()));
    }
}
