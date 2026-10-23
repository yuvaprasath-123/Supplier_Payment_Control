package com.ofss.configuration.activation;

import com.ofss.configuration.band.*;
import com.ofss.configuration.publication.ConfigurationContentHash;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.*;
import com.ofss.configuration.threshold.*;
import com.ofss.configuration.version.ConfigurationVersion;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ConfigurationActivationServiceTest {
    static final OffsetDateTime PUBLISHED=OffsetDateTime.parse("2026-01-01T00:00:00Z");
    static final OffsetDateTime NOW=PUBLISHED.plusDays(1);
    final ConfigurationActivationRepository repository=mock(ConfigurationActivationRepository.class);
    final DraftRiskRuleRepository versions=mock(DraftRiskRuleRepository.class);
    final DraftRiskBandRepository bands=mock(DraftRiskBandRepository.class);
    final DraftApprovalThresholdRepository thresholds=mock(DraftApprovalThresholdRepository.class);
    final DraftApprovalSlaRepository slas=mock(DraftApprovalSlaRepository.class);
    final ConfigurationContentHash hashes=new ConfigurationContentHash(JsonMapper.builder().build());
    ConfigurationActivationService service;
    ConfigurationVersionDetails parent;
    String hash;
    @BeforeEach void setup() {
        parent=parent("PUBLISHED",1);
        when(versions.findVersion(7,true)).thenReturn(Optional.of(parent));
        when(versions.findVersion(7,false)).thenReturn(Optional.of(parent));
        when(versions.listRules(7)).thenReturn(List.of());when(bands.list(7)).thenReturn(List.of());
        when(thresholds.list(7)).thenReturn(List.of());when(slas.list(7)).thenReturn(List.of());
        hash=hashes.compute(parent,41,List.of(),List.of(),List.of(),List.of()).hash();
        when(repository.metadata(7)).thenReturn(new ConfigurationActivationRepository.Metadata(41,PUBLISHED,42,hash));
        service=new ConfigurationActivationService(repository,versions,bands,thresholds,slas,hashes,Clock.fixed(NOW.toInstant(),ZoneOffset.UTC));
    }
    static ConfigurationVersionDetails parent(String state,long revision) {
        return new ConfigurationVersionDetails(new ConfigurationVersion(7,1,"mechanics fixture",state,true,PUBLISHED.minusDays(1),PUBLISHED),null,"SUM",null,revision);
    }
    ConfigurationActivationRepository.Pointer pointer(long id,long revision) { return new ConfigurationActivationRepository.Pointer(id,NOW,43,revision); }
    @Test void historicalHashNeverGainsNewPolicyFieldsOrFormat(){
        when(repository.pointer()).thenReturn(Optional.of(pointer(7,0)));
        var historical=service.active();assertThat(historical.hashFormat()).isEqualTo("configuration-content-v1");
        assertThat(historical.contentHash()).isEqualTo(hash);assertThat(historical.configuration()).doesNotContain("\"approvalPolicy\"");
        assertThat(service.published(7).hashFormat()).isEqualTo("configuration-content-v1");
        verify(repository,never()).save(any(),anyLong(),anyLong(),any());
    }
    @Test void firstActivationVerifiesHashWritesAndReadsBackBeforeReturning() {
        when(repository.pointer()).thenReturn(Optional.empty(),Optional.of(pointer(7,0)));
        var result=service.activate(7,new ActivationRequest(null),43);
        assertThat(result.pointerRowVersion()).isEqualTo("0");assertThat(result.versionRowVersion()).isEqualTo("1");
        assertThat(result.contentHash()).isEqualTo(hash);assertThat(result.configuration()).contains("\"riskRules\":[]","\"approvalSlas\":[]");
        var order=inOrder(repository,versions);order.verify(repository).lockPointer();order.verify(repository).pointer();
        order.verify(versions).findVersion(7,true);order.verify(repository).metadata(7);order.verify(versions).listRules(7);
        order.verify(repository).save(null,7,43,NOW);order.verify(repository).pointer();
    }
    @Test void switchAdvancesOnlyPointerRevision() {
        var previous=pointer(8,9);when(repository.pointer()).thenReturn(Optional.of(previous),Optional.of(pointer(7,10)));
        assertThat(service.activate(7,new ActivationRequest(9L),43).pointerRowVersion()).isEqualTo("10");
        verify(repository).save(previous,7,43,NOW);verify(versions,never()).incrementParent(anyLong(),anyLong());
    }
    @Test void repeatedActivationWithCurrentRevisionPreservesActorTimeAndRevision() {
        var previous=new ConfigurationActivationRepository.Pointer(7,PUBLISHED.plusHours(1),44,9);
        when(repository.pointer()).thenReturn(Optional.of(previous));
        var result=service.activate(7,new ActivationRequest(9L),43);
        assertThat(result.activatedAt()).isEqualTo(previous.activatedAt().toInstant().toString());assertThat(result.activatedByUserId()).isEqualTo("44");
        assertThat(result.pointerRowVersion()).isEqualTo("9");verify(repository,never()).save(any(),anyLong(),anyLong(),any());
    }
    @ParameterizedTest @ValueSource(strings={"absent","present","different","overflow"})
    void pointerPreconditions(String kind) {
        when(repository.pointer()).thenReturn(kind.equals("absent")?Optional.empty():Optional.of(pointer(8,kind.equals("overflow")?Long.MAX_VALUE:1)));
        Long expected=switch(kind){case "absent"->0L;case "present"->null;case "overflow"->Long.MAX_VALUE;default->2L;};
        assertThatThrownBy(()->service.activate(7,new ActivationRequest(expected),43)).isInstanceOf(ActivationFailure.class);
        verify(repository,never()).save(any(),anyLong(),anyLong(),any());
    }
    @Test void draftsAndMissingVersionsRejected() {
        when(repository.pointer()).thenReturn(Optional.empty());when(versions.findVersion(7,true)).thenReturn(Optional.of(parent("DRAFT",0)));
        assertThatThrownBy(()->service.activate(7,new ActivationRequest(null),43)).isInstanceOf(ActivationFailure.class);
        when(versions.findVersion(7,true)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.activate(7,new ActivationRequest(null),43)).isInstanceOf(ConfigurationVersionNotFoundException.class);
        verify(repository,never()).save(any(),anyLong(),anyLong(),any());
    }
    @ParameterizedTest @ValueSource(strings={"hash","publication-time","zero-revision","missing-readback","wrong-readback","future-publication"})
    void corruptStateFailsClosed(String kind) {
        when(repository.pointer()).thenReturn(Optional.empty(),Optional.of(pointer(7,0)));
        switch(kind) {
            case "hash" -> when(repository.metadata(7)).thenReturn(new ConfigurationActivationRepository.Metadata(41,PUBLISHED,42,"f".repeat(64)));
            case "publication-time" -> when(repository.metadata(7)).thenReturn(new ConfigurationActivationRepository.Metadata(41,PUBLISHED.plusSeconds(1),42,hash));
            case "zero-revision" -> when(versions.findVersion(7,true)).thenReturn(Optional.of(parent("PUBLISHED",0)));
            case "missing-readback" -> when(repository.pointer()).thenReturn(Optional.empty(),Optional.empty());
            case "wrong-readback" -> when(repository.pointer()).thenReturn(Optional.empty(),Optional.of(pointer(7,1)));
            case "future-publication" -> service=new ConfigurationActivationService(repository,versions,bands,thresholds,slas,hashes,Clock.fixed(PUBLISHED.minusSeconds(1).toInstant(),ZoneOffset.UTC));
        }
        assertThatThrownBy(()->service.activate(7,new ActivationRequest(null),43)).isInstanceOf(DataAccessException.class);
    }
    @Test void activeReadsOneVerifiedSnapshotAndNoWrites() {
        when(repository.pointer()).thenReturn(Optional.of(pointer(7,2)));
        assertThat(service.active().contentHash()).isEqualTo(hash);
        verify(versions).findVersion(7,false);verify(repository,never()).lockPointer();verify(repository,never()).save(any(),anyLong(),anyLong(),any());
    }
    @Test void noPointerAndDanglingPointerFailClearly() {
        when(repository.pointer()).thenReturn(Optional.empty());
        assertThatThrownBy(service::active).isInstanceOf(ActivationFailure.class);
        when(repository.pointer()).thenReturn(Optional.of(pointer(7,2)));when(versions.findVersion(7,false)).thenReturn(Optional.empty());
        assertThatThrownBy(service::active).isInstanceOf(DataAccessException.class);
    }
    @Test void fullSnapshotPreservesExactParametersAndAllFourSectionsAndDetectsChildTampering() {
        var rule=new RiskRule(1,7,"STRUCTURING","Synthetic rule",false,35,"risk-policy-v1",
                Map.of("precision",new java.math.BigDecimal("1.0000000000000001"),"large",new java.math.BigInteger("9223372036854775808")),1);
        var band=new RiskBand(2,7,"LOW",0,29,1);
        var tier=new ApprovalThreshold(3,7,1,new java.math.BigDecimal("10.01"),null);
        var sla=new ApprovalSla(4,7,1,86400,"UNAGREED_START","UNAGREED_ESCALATION",null);
        var content=hashes.compute(parent,41,List.of(rule),List.of(band),List.of(tier),List.of(sla));
        when(repository.pointer()).thenReturn(Optional.of(pointer(7,0)));
        when(repository.metadata(7)).thenReturn(new ConfigurationActivationRepository.Metadata(41,PUBLISHED,42,content.hash()));
        when(versions.listRules(7)).thenReturn(List.of(rule));when(bands.list(7)).thenReturn(List.of(band));
        when(thresholds.list(7)).thenReturn(List.of(tier));when(slas.list(7)).thenReturn(List.of(sla));
        assertThat(service.active().configuration()).contains("1.0000000000000001","9223372036854775808","\"minAmountInclusive\":\"10.01\"","UNAGREED_START");
        when(bands.list(7)).thenReturn(List.of());
        assertThatThrownBy(service::active).isInstanceOf(DataAccessException.class);
    }
}
