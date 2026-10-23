package com.ofss.configuration.threshold;

import com.ofss.configuration.rule.ConfigurationVersionNotFoundException;
import com.ofss.configuration.rule.DraftRiskRuleRepository;
import com.ofss.configuration.rule.DraftRuleConflictException;
import java.util.List;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftApprovalThresholdService {
    public record SavedThreshold(ApprovalThreshold.Response threshold, String rowVersion) {}
    private final DraftRiskRuleRepository versions;
    private final DraftApprovalThresholdRepository thresholds;
    public DraftApprovalThresholdService(DraftRiskRuleRepository versions, DraftApprovalThresholdRepository thresholds) {
        this.versions = versions;
        this.thresholds = thresholds;
    }
    @Transactional(readOnly = true)
    public List<ApprovalThreshold.Response> list(long versionId) {
        versions.findVersion(versionId, false).orElseThrow(ConfigurationVersionNotFoundException::new);
        return thresholds.list(versionId).stream().map(ApprovalThreshold::response).toList();
    }
    @Transactional
    public SavedThreshold put(long versionId, int level, DraftApprovalThresholdRequest request) {
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
        var existing = thresholds.find(versionId, level).orElse(null);
        long id = thresholds.save(versionId, level, request, existing);
        versions.incrementParent(versionId, parent.rowVersion());
        var saved = thresholds.find(versionId, level).orElseThrow(() -> new DataRetrievalFailureException("Saved threshold was not found"));
        if (saved.thresholdConfigId() != id) throw new DataRetrievalFailureException("Saved threshold identity changed");
        return new SavedThreshold(saved.response(), Long.toString(parent.rowVersion() + 1));
    }
}
