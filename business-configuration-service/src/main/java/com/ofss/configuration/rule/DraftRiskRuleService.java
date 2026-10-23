package com.ofss.configuration.rule;

import java.util.List;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftRiskRuleService {
    public record SavedRule(RiskRule.Response rule, String rowVersion) {}
    private final DraftRiskRuleRepository repository;
    public DraftRiskRuleService(DraftRiskRuleRepository repository) { this.repository = repository; }
    private ConfigurationVersionDetails version(long id, boolean lock) {
        return repository.findVersion(id, lock).orElseThrow(ConfigurationVersionNotFoundException::new);
    }
    @Transactional(readOnly = true)
    public ConfigurationVersionDetails.Response details(long id) { return version(id, false).response(); }
    @Transactional(readOnly = true)
    public List<RiskRule.Response> rules(long id) {
        version(id, false);
        return repository.listRules(id).stream().map(RiskRule::response).toList();
    }
    @Transactional
    public SavedRule put(long id, String code, DraftRiskRuleRequest request) {
        var parent = version(id, true);
        if (!"DRAFT".equals(parent.version().publicationState())) {
            throw new DraftRuleConflictException(DraftRuleConflictException.Reason.VERSION_NOT_DRAFT);
        }
        if (parent.rowVersion() != request.expectedRowVersion()) {
            throw new DraftRuleConflictException(DraftRuleConflictException.Reason.STALE_VERSION);
        }
        if (parent.rowVersion() == Long.MAX_VALUE) {
            throw new DraftRuleConflictException(DraftRuleConflictException.Reason.ROW_VERSION_EXHAUSTED);
        }
        var existing = repository.findRule(id, code).orElse(null);
        long ruleId = repository.saveRule(id, code, request, existing);
        repository.incrementParent(id, parent.rowVersion());
        var saved = repository.findRule(id, code)
                .orElseThrow(() -> new DataRetrievalFailureException("Saved rule was not found"));
        if (saved.ruleConfigId() != ruleId) throw new DataRetrievalFailureException("Saved rule identity changed");
        return new SavedRule(saved.response(), Long.toString(parent.rowVersion() + 1));
    }
}
