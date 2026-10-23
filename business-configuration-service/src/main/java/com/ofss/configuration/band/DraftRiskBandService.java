package com.ofss.configuration.band;

import com.ofss.configuration.rule.ConfigurationVersionNotFoundException;
import com.ofss.configuration.rule.DraftRiskRuleRepository;
import com.ofss.configuration.rule.DraftRuleConflictException;
import java.util.List;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftRiskBandService {
    public record SavedBand(RiskBand.Response band, String rowVersion) {}
    private final DraftRiskRuleRepository versions;
    private final DraftRiskBandRepository bands;
    public DraftRiskBandService(DraftRiskRuleRepository versions, DraftRiskBandRepository bands) {
        this.versions = versions;
        this.bands = bands;
    }

    @Transactional(readOnly = true)
    public List<RiskBand.Response> list(long versionId) {
        versions.findVersion(versionId, false).orElseThrow(ConfigurationVersionNotFoundException::new);
        return bands.list(versionId).stream().map(RiskBand::response).toList();
    }

    @Transactional
    public SavedBand put(long versionId, String code, DraftRiskBandRequest request) {
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
        var existing = bands.find(versionId, code).orElse(null);
        long id = bands.save(versionId, code, request, existing);
        versions.incrementParent(versionId, parent.rowVersion());
        var saved = bands.find(versionId, code).orElseThrow(() -> new DataRetrievalFailureException("Saved band was not found"));
        if (saved.bandConfigId() != id) throw new DataRetrievalFailureException("Saved band identity changed");
        return new SavedBand(saved.response(), Long.toString(parent.rowVersion() + 1));
    }
}
