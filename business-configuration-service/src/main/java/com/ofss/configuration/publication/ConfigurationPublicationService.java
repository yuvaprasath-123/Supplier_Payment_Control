package com.ofss.configuration.publication;

import com.ofss.configuration.band.DraftRiskBandRepository;
import com.ofss.configuration.readiness.*;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.DraftApprovalSlaRepository;
import com.ofss.configuration.threshold.DraftApprovalThresholdRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigurationPublicationService {
    public record PublishedVersion(String versionId, String rowVersion, String publicationState, String contentHash,
            String hashFormat, String publishedAt, String publishedByUserId) {}
    private final DraftRiskRuleRepository versions;
    private final PublicationReadinessRuleRepository rules;
    private final DraftRiskBandRepository bands;
    private final DraftApprovalThresholdRepository thresholds;
    private final DraftApprovalSlaRepository slas;
    private final PublicationReadinessValidator validator;
    private final ConfigurationContentHash hashes;
    private final ConfigurationPublicationRepository repository;
    private final Clock clock;
    @Autowired
    public ConfigurationPublicationService(DraftRiskRuleRepository versions, PublicationReadinessRuleRepository rules,
            DraftRiskBandRepository bands, DraftApprovalThresholdRepository thresholds, DraftApprovalSlaRepository slas,
            PublicationReadinessValidator validator, ConfigurationContentHash hashes, ConfigurationPublicationRepository repository) {
        this(versions, rules, bands, thresholds, slas, validator, hashes, repository, Clock.systemUTC());
    }
    ConfigurationPublicationService(DraftRiskRuleRepository versions, PublicationReadinessRuleRepository rules,
            DraftRiskBandRepository bands, DraftApprovalThresholdRepository thresholds, DraftApprovalSlaRepository slas,
            PublicationReadinessValidator validator, ConfigurationContentHash hashes, ConfigurationPublicationRepository repository, Clock clock) {
        this.versions = versions; this.rules = rules; this.bands = bands; this.thresholds = thresholds; this.slas = slas;
        this.validator = validator; this.hashes = hashes; this.repository = repository; this.clock = clock;
    }
    /** All application child writers lock this same parent before their checks and writes. */
    @Transactional
    public PublishedVersion publish(long versionId, PublishConfigurationRequest request, long actorId) {
        if (versionId < 1 || actorId < 1 || request == null) throw new InvalidConfigurationRequestException();
        var parent = versions.findVersion(versionId, true).orElseThrow(ConfigurationVersionNotFoundException::new);
        if ("PUBLISHED".equals(parent.version().publicationState())) throw new AlreadyPublishedException();
        if (!"DRAFT".equals(parent.version().publicationState())) throw ConfigurationPublicationRepository.invalid();
        if (parent.rowVersion() != request.expectedRowVersion()) throw new DraftRuleConflictException(DraftRuleConflictException.Reason.STALE_VERSION);
        if (parent.rowVersion() == Long.MAX_VALUE) throw new DraftRuleConflictException(DraftRuleConflictException.Reason.ROW_VERSION_EXHAUSTED);
        var ruleRows = rules.list(versionId); var bandRows = bands.list(versionId);
        var thresholdRows = thresholds.list(versionId); var slaRows = slas.list(versionId);
        var readiness = validator.validate(parent, ruleRows, bandRows, thresholdRows, slaRows);
        if (!Long.toString(versionId).equals(readiness.versionId()) || !Long.toString(parent.rowVersion()).equals(readiness.rowVersion())
                || !"DRAFT".equals(readiness.publicationState())) throw ConfigurationPublicationRepository.invalid();
        if (!readiness.ready()) throw new PublicationNotReadyException(readiness);
        if (!readiness.structurallyValid() || !readiness.issues().isEmpty()) throw ConfigurationPublicationRepository.invalid();
        var before = repository.metadata(versionId);
        if (before.publishedAt() != null || before.publishedByUserId() != null || before.contentHash() != null) throw ConfigurationPublicationRepository.invalid();
        var content = hashes.computePublished(parent, before.createdByUserId(), ruleRows, bandRows, thresholdRows, slaRows);
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (parent.version().createdAt() == null || now.isBefore(parent.version().createdAt())) throw ConfigurationPublicationRepository.invalid();
        repository.publish(versionId, parent.rowVersion(), actorId, now, content.hash());
        var saved = versions.findVersion(versionId, false).orElseThrow(ConfigurationPublicationRepository::invalid);
        var after = repository.metadata(versionId);
        if (!saved.equals(new ConfigurationVersionDetails(new com.ofss.configuration.version.ConfigurationVersion(
                parent.version().versionId(), parent.version().versionNumber(), parent.version().versionLabel(), "PUBLISHED", parent.version().isDemonstration(),
                parent.version().createdAt(), now), parent.description(), parent.scorePolicyCode(), parent.maxFinalScore(), parent.rowVersion() + 1))
                || !after.equals(new ConfigurationPublicationRepository.Metadata(before.createdByUserId(), now, actorId, content.hash())))
            throw ConfigurationPublicationRepository.invalid();
        return new PublishedVersion(Long.toString(versionId), Long.toString(saved.rowVersion()), "PUBLISHED", content.hash(),
                com.ofss.risk.contract.ApprovalPolicyContracts.SNAPSHOT_FORMAT, now.toInstant().toString(), Long.toString(actorId));
    }
}
