package com.ofss.configuration.publication;

import com.ofss.configuration.band.*;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.*;
import com.ofss.configuration.threshold.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Every existing application child-write service shares publication's parent lock. */
class PublishedConfigurationImmutabilityTest {
    @ParameterizedTest @ValueSource(strings={"rules","bands","thresholds","slas"})
    void publishedContentCannotBeEditedEvenWithCurrentRevision(String section) {
        var versions=mock(DraftRiskRuleRepository.class);var bands=mock(DraftRiskBandRepository.class);
        var thresholds=mock(DraftApprovalThresholdRepository.class);var slas=mock(DraftApprovalSlaRepository.class);
        when(versions.findVersion(7,true)).thenReturn(Optional.of(PublicationFixtures.parent("PUBLISHED",9)));
        Runnable action=switch(section) {
            case "rules"->()->new DraftRiskRuleService(versions).put(7,"STRUCTURING",new DraftRiskRuleRequest("Draft",true,1,"risk-policy-v1",Map.of(),1,9));
            case "bands"->()->new DraftRiskBandService(versions,bands).put(7,"LOW",new DraftRiskBandRequest(0,29,1,9));
            case "thresholds"->()->new DraftApprovalThresholdService(versions,thresholds).put(7,1,new DraftApprovalThresholdRequest(BigDecimal.ZERO,null,9));
            default->()->new DraftApprovalSlaService(versions,slas).put(7,1,new DraftApprovalSlaRequest(1,"DRAFT_START","DRAFT_ESCALATION",null,9));
        };
        var exception=catchThrowableOfType(action::run,DraftRuleConflictException.class);
        assertThat(exception.reason()).isEqualTo(DraftRuleConflictException.Reason.VERSION_NOT_DRAFT);
        verify(versions).findVersion(7,true);verifyNoMoreInteractions(versions);verifyNoInteractions(bands,thresholds,slas);
    }
}
