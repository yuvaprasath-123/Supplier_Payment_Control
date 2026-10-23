package com.ofss.configuration.activation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PinnedPublishedSnapshotTest {
    private ConfigurationActivationServiceTest fixture() {var f=new ConfigurationActivationServiceTest();f.setup();return f;}
    @Test void explicitPublishedSnapshotUsesExistingHashVerifierWithoutPointerOrMutation() {
        var f=fixture();var saved=f.service.published(7);
        assertThat(saved.versionId()).isEqualTo("7");assertThat(saved.versionRowVersion()).isEqualTo("1");assertThat(saved.contentHash()).isEqualTo(f.hash);
        assertThat(saved.configuration()).contains("\"riskRules\":[]","\"riskBands\":[]","\"approvalThresholds\":[]","\"approvalSlas\":[]");
        verify(f.versions).findVersion(7,false);verify(f.repository).metadata(7);verifyNoMoreInteractions(f.repository);
    }
    @ParameterizedTest @ValueSource(strings={"draft","missing","hash","child-content"})
    void unpublishedMissingOrCorruptVersionsNeverReturnASnapshot(String failure) {
        var f=fixture();
        switch(failure) {
            case "draft"->when(f.versions.findVersion(7,false)).thenReturn(java.util.Optional.of(ConfigurationActivationServiceTest.parent("DRAFT",0)));
            case "missing"->when(f.versions.findVersion(7,false)).thenReturn(java.util.Optional.empty());
            case "hash"->when(f.repository.metadata(7)).thenReturn(new ConfigurationActivationRepository.Metadata(41,ConfigurationActivationServiceTest.PUBLISHED,42,"b".repeat(64)));
            case "child-content"->when(f.bands.list(7)).thenReturn(java.util.List.of(new com.ofss.configuration.band.RiskBand(1,7,"LOW",0,29,1)));
        }
        assertThatThrownBy(()->f.service.published(7)).isInstanceOf(RuntimeException.class);verify(f.repository,never()).pointer();verify(f.repository,never()).lockPointer();
    }
}
