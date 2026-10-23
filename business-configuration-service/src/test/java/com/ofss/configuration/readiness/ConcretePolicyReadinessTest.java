package com.ofss.configuration.readiness;

import com.ofss.configuration.band.RiskBand;
import com.ofss.configuration.sla.ApprovalSla;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.ofss.configuration.readiness.ReadinessFixtures.*;

class ConcretePolicyReadinessTest {
    final PublicationReadinessValidator validator=new PublicationReadinessValidator();
    @ParameterizedTest @ValueSource(strings={"unknown-start","unknown-version","auto","backup-loop","missing-terminal","reversed-mapping"})
    void noFlagOrDemonstrationLabelCanBypassUnsupportedSemantics(String fault){
        var bands=new ArrayList<>(bands());var slas=new ArrayList<>(slas());
        if(fault.equals("reversed-mapping"))bands.set(0,new RiskBand(1,7,"LOW",0,0,3));
        else {var old=slas.get(2);slas.set(2,new ApprovalSla(3,7,3,86400,
            fault.equals("unknown-start")?"VIEWED":fault.equals("unknown-version")?"APPROVAL_PENDING_V2":old.startEventCode(),
            fault.equals("auto")?"AUTO_APPROVE":fault.equals("backup-loop")?"NEXT_LEVEL_V1":old.escalationPolicyCode(),
            fault.equals("missing-terminal")?null:old.terminalLevelPolicyCode()));}
        var r=validator.validate(parent("CAP_AT_MAX",100,true),rules(),bands,thresholds(),slas);
        assertThat(r.ready()).isFalse();assertThat(r.issues()).anyMatch(i->i.code().equals(fault.equals("reversed-mapping")?"INVALID_AUTHORITY_MAPPING":"INVALID_APPROVAL_POLICY_CONTRACT"));
    }
    @Test void allDisabledSumCannotPublishAnEngineIncompatibleZeroMaximum(){
        var r=validator.validate(parent("SUM",null,true),rules().stream().map(x->rule(x.ruleCode(),x.evaluationOrder(),x.weight(),false)).toList(),bands(),thresholds(),slas());
        assertThat(r.ready()).isFalse();assertThat(r.issues()).anyMatch(i->i.code().equals("INVALID_SCORE_POLICY"));
    }
    @Test void unresolvedDraftChoiceIsStillABlockerButUpstreamAvailabilityIsNot(){
        var all=new ArrayList<>(rules());var old=all.stream().filter(x->x.ruleCode().equals("NEAR_DUPLICATE_INVOICE")).findFirst().orElseThrow();
        var params=new HashMap<>(old.parameters());params.put("invoiceNumberNormalization","UNRESOLVED");
        all.set(all.indexOf(old),new com.ofss.configuration.rule.RiskRule(old.ruleConfigId(),7,old.ruleCode(),old.displayName(),true,old.weight(),old.parameterSchemaVersion(),params,old.evaluationOrder()));
        assertThat(validator.validate(parent("CAP_AT_MAX",100,true),all,bands(),thresholds(),slas()).issues()).anyMatch(i->i.category()==PublicationReadiness.Category.POLICY_BLOCKER);
        assertThat(validator.validate(parent("CAP_AT_MAX",100,true),rules(),bands(),thresholds(),slas()).ready()).isTrue();
    }
}
