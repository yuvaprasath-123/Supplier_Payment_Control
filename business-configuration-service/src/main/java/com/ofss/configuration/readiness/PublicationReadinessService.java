package com.ofss.configuration.readiness;

import com.ofss.configuration.band.DraftRiskBandRepository;
import com.ofss.configuration.rule.ConfigurationVersionNotFoundException;
import com.ofss.configuration.rule.DraftRiskRuleRepository;
import com.ofss.configuration.sla.DraftApprovalSlaRepository;
import com.ofss.configuration.threshold.DraftApprovalThresholdRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicationReadinessService {
    private final DraftRiskRuleRepository versionsAndRules;
    private final PublicationReadinessRuleRepository rules;
    private final DraftRiskBandRepository bands;
    private final DraftApprovalThresholdRepository thresholds;
    private final DraftApprovalSlaRepository slas;
    private final PublicationReadinessValidator validator;
    public PublicationReadinessService(DraftRiskRuleRepository versionsAndRules, PublicationReadinessRuleRepository rules, DraftRiskBandRepository bands,
            DraftApprovalThresholdRepository thresholds, DraftApprovalSlaRepository slas,
            PublicationReadinessValidator validator) {
        this.versionsAndRules = versionsAndRules;
        this.rules = rules;
        this.bands = bands;
        this.thresholds = thresholds;
        this.slas = slas;
        this.validator = validator;
    }
    // Oracle SERIALIZABLE supplies one transaction snapshot across all five SELECTs.
    // readOnly alone is a JDBC hint, not a guarantee of transaction-level consistency.
    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public PublicationReadiness get(long versionId) {
        var parent = versionsAndRules.findVersion(versionId, false)
                .orElseThrow(ConfigurationVersionNotFoundException::new);
        return validator.validate(parent, rules.list(versionId), bands.list(versionId),
                thresholds.list(versionId), slas.list(versionId));
    }
}
