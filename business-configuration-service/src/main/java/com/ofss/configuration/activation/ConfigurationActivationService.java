package com.ofss.configuration.activation;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.ofss.configuration.band.DraftRiskBandRepository;
import com.ofss.configuration.publication.ConfigurationContentHash;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.DraftApprovalSlaRepository;
import com.ofss.configuration.threshold.DraftApprovalThresholdRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ConfigurationActivationService {
    public record ActiveSnapshot(String versionId, String versionRowVersion, String pointerRowVersion,
            String activatedAt, String activatedByUserId, String publishedAt, String publishedByUserId,
            String contentHash, String hashFormat, @JsonRawValue String configuration) {}
    public record PublishedSnapshot(String versionId, String versionRowVersion, String publishedAt, String publishedByUserId,
            String contentHash, String hashFormat, @JsonRawValue String configuration) {}
    private final ConfigurationActivationRepository repository;
    private final DraftRiskRuleRepository versions;
    private final DraftRiskBandRepository bands;
    private final DraftApprovalThresholdRepository thresholds;
    private final DraftApprovalSlaRepository slas;
    private final ConfigurationContentHash hashes;
    private final Clock clock;
    @Autowired
    public ConfigurationActivationService(ConfigurationActivationRepository repository, DraftRiskRuleRepository versions,
            DraftRiskBandRepository bands, DraftApprovalThresholdRepository thresholds, DraftApprovalSlaRepository slas, ConfigurationContentHash hashes) {
        this(repository, versions, bands, thresholds, slas, hashes, Clock.systemUTC());
    }
    ConfigurationActivationService(ConfigurationActivationRepository repository, DraftRiskRuleRepository versions,
            DraftRiskBandRepository bands, DraftApprovalThresholdRepository thresholds, DraftApprovalSlaRepository slas, ConfigurationContentHash hashes, Clock clock) {
        this.repository=repository; this.versions=versions; this.bands=bands; this.thresholds=thresholds; this.slas=slas; this.hashes=hashes; this.clock=clock;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED, timeout=15)
    public ActiveSnapshot activate(long versionId, ActivationRequest request, long actorId) {
        if (versionId < 1 || request == null || actorId < 1) throw new InvalidConfigurationRequestException();
        // Always acquire pointer serialization before a version lock; consistent ordering avoids deadlocks.
        repository.lockPointer();
        var before = repository.pointer().orElse(null);
        Long revision = before == null ? null : before.rowVersion();
        if (!Objects.equals(revision, request.expectedPointerRowVersion())) throw new ActivationFailure(ActivationFailure.Reason.STALE_ACTIVE_CONFIGURATION);
        var parent = versions.findVersion(versionId, true).orElseThrow(ConfigurationVersionNotFoundException::new);
        if (!"PUBLISHED".equals(parent.version().publicationState())) throw new ActivationFailure(ActivationFailure.Reason.VERSION_NOT_PUBLISHED);
        var verified = verify(parent);
        if (before != null && before.versionId() == versionId) return response(before, parent, verified);
        if (before != null && before.rowVersion() == Long.MAX_VALUE) throw new ActivationFailure(ActivationFailure.Reason.ACTIVATION_REVISION_EXHAUSTED);
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (now.isBefore(verified.metadata().publishedAt()) || (before != null && now.isBefore(before.activatedAt()))) throw ConfigurationActivationRepository.invalid();
        repository.save(before, versionId, actorId, now);
        var saved = repository.pointer().orElseThrow(ConfigurationActivationRepository::invalid);
        var expected = new ConfigurationActivationRepository.Pointer(versionId, now, actorId, before == null ? 0 : before.rowVersion()+1);
        if (!expected.equals(saved)) throw ConfigurationActivationRepository.invalid();
        return response(saved, parent, verified);
    }
    @Transactional(readOnly=true, isolation=Isolation.SERIALIZABLE)
    public ActiveSnapshot active() {
        var pointer = repository.pointer().orElseThrow(() -> new ActivationFailure(ActivationFailure.Reason.NO_ACTIVE_CONFIGURATION));
        var parent = versions.findVersion(pointer.versionId(), false).orElseThrow(ConfigurationActivationRepository::invalid);
        return response(pointer, parent, verify(parent));
    }
    /** Pinned reads never consult the mutable active pointer or select a newer version. */
    @Transactional(readOnly=true, isolation=Isolation.SERIALIZABLE)
    public PublishedSnapshot published(long versionId) {
        if (versionId < 1) throw new InvalidConfigurationRequestException();
        var parent=versions.findVersion(versionId,false).orElseThrow(ConfigurationVersionNotFoundException::new);
        if (!"PUBLISHED".equals(parent.version().publicationState())) throw new ActivationFailure(ActivationFailure.Reason.VERSION_NOT_PUBLISHED);
        var verified=verify(parent);
        return new PublishedSnapshot(Long.toString(versionId),Long.toString(parent.rowVersion()),verified.metadata().publishedAt().toInstant().toString(),
                Long.toString(verified.metadata().publisher()),verified.content().hash(),verified.format(),verified.content().json());
    }
    private record Verified(ConfigurationActivationRepository.Metadata metadata, ConfigurationContentHash.Document content,String format) {}
    private Verified verify(ConfigurationVersionDetails parent) {
        long id = parent.version().versionId();
        var metadata = repository.metadata(id);
        if (!"PUBLISHED".equals(parent.version().publicationState()) || parent.rowVersion() < 1 || metadata.publishedAt() == null
                || metadata.hash() == null || !metadata.hash().matches("[0-9a-f]{64}") || metadata.publisher() < 1
                || parent.version().createdAt() == null || metadata.publishedAt().isBefore(parent.version().createdAt())
                || !metadata.publishedAt().equals(parent.version().publishedAt())) throw ConfigurationActivationRepository.invalid();
        var rules=versions.listRules(id);var bandRows=bands.list(id);var tiers=thresholds.list(id);var slaRows=slas.list(id);
        var content = hashes.compute(parent, metadata.creator(), rules,bandRows,tiers,slaRows);
        String format=ConfigurationContentHash.FORMAT;
        if(!content.hash().equals(metadata.hash())) {
            try{content=hashes.computePublished(parent,metadata.creator(),rules,bandRows,tiers,slaRows);}
            catch(RuntimeException invalid){throw ConfigurationActivationRepository.invalid();}
            format=com.ofss.risk.contract.ApprovalPolicyContracts.SNAPSHOT_FORMAT;
        }
        if (!content.hash().equals(metadata.hash())) throw ConfigurationActivationRepository.invalid();
        return new Verified(metadata, content,format);
    }
    private ActiveSnapshot response(ConfigurationActivationRepository.Pointer pointer, ConfigurationVersionDetails parent, Verified verified) {
        if (pointer.versionId() != parent.version().versionId() || pointer.activatedAt().isBefore(verified.metadata().publishedAt())) throw ConfigurationActivationRepository.invalid();
        return new ActiveSnapshot(Long.toString(pointer.versionId()), Long.toString(parent.rowVersion()), Long.toString(pointer.rowVersion()),
                pointer.activatedAt().toInstant().toString(), Long.toString(pointer.activatedBy()), verified.metadata().publishedAt().toInstant().toString(),
                Long.toString(verified.metadata().publisher()), verified.content().hash(), verified.format(), verified.content().json());
    }
}
