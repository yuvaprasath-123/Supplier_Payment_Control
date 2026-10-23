package com.ofss.configuration.sla;

import com.ofss.configuration.rule.ConfigurationVersionNotFoundException;
import com.ofss.configuration.rule.DraftRiskRuleRepository;
import com.ofss.configuration.rule.DraftRuleConflictException;
import java.util.List;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftApprovalSlaService {
    public record SavedSla(ApprovalSla.Response sla, String rowVersion) {}
    private final DraftRiskRuleRepository versions;
    private final DraftApprovalSlaRepository slas;
    public DraftApprovalSlaService(DraftRiskRuleRepository versions, DraftApprovalSlaRepository slas) {
        this.versions = versions;
        this.slas = slas;
    }
    @Transactional(readOnly = true)
    public List<ApprovalSla.Response> list(long versionId) {
        versions.findVersion(versionId, false).orElseThrow(ConfigurationVersionNotFoundException::new);
        return slas.list(versionId).stream().map(ApprovalSla::response).toList();
    }
    @Transactional
    public SavedSla put(long versionId, int level, DraftApprovalSlaRequest request) {
        var parent = versions.findVersion(versionId, true).orElseThrow(ConfigurationVersionNotFoundException::new);
        if (!"DRAFT".equals(parent.version().publicationState())) {
            throw new DraftRuleConflictException(DraftRuleConflictException.Reason.VERSION_NOT_DRAFT);
        }
        if (parent.rowVersion() != request.expectedRowVersion()) {
            throw new DraftRuleConflictException(DraftRuleConflictException.Reason.STALE_VERSION);
        }
        if (parent.rowVersion() == Long.MAX_VALUE) {
            throw new DraftRuleConflictException(DraftRuleConflictException.Reason.ROW_VERSION_EXHAUSTED);
        }
        var existing = slas.find(versionId, level).orElse(null);
        long id = slas.save(versionId, level, request, existing);
        versions.incrementParent(versionId, parent.rowVersion());
        var saved = slas.find(versionId, level).orElseThrow(() -> new DataRetrievalFailureException("Saved sla was not found"));
        if (saved.slaConfigId() != id) throw new DataRetrievalFailureException("Saved sla identity changed");
        return new SavedSla(saved.response(), Long.toString(parent.rowVersion() + 1));
    }
}
