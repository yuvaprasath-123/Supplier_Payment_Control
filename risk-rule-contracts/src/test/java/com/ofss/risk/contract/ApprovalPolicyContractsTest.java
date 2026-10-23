package com.ofss.risk.contract;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.ofss.risk.contract.ApprovalPolicyContracts.*;

class ApprovalPolicyContractsTest {
    static Sla sla(int level,long duration){return new Sla(level,duration,START,level<3?NEXT:BACKUP,level<3?null:MANUAL);}
    @Test void hashedCodesExpandToEveryApprovedObligationWithoutDefaults(){
        var p=resolve(BAND_LEVELS,List.of(sla(3,9),sla(1,86400),sla(2,999999999999L)));
        assertEquals("approval-policy-v1",p.version());assertEquals("INR",p.currency());
        assertEquals("MAX_AMOUNT_TIER_AND_RISK_LEVEL",p.authority());assertEquals(Set.of(1,2,3),p.humanAuthorizationLevels());
        assertEquals("TASK_ENTERS_PENDING",p.timerStart());assertFalse(p.viewingResetsDeadline());assertFalse(p.ordinaryReassignmentResetsDeadline());
        assertEquals(List.of(1,2,3),p.escalationOrder());assertEquals(1,p.maximumSameLevelBackups());
        assertEquals(Map.of(1,"ESCALATE_TO_L2",2,"ESCALATE_TO_L3",3,"ONE_ELIGIBLE_L3_BACKUP_OR_MANUAL_REVIEW"),p.primaryExpiryActions());
        assertEquals("DESTINATION_LEVEL_CONFIGURED_DURATION",p.newTaskDuration());
        assertEquals("DEVELOPER3_CONFIGURED_ELIGIBLE_SAME_LEVEL_BACKUP",p.backupEligibility());
        assertEquals("MANUAL_REVIEW",p.absentBackupAction());assertEquals("MANUAL_REVIEW",p.backupExpiryAction());
        assertEquals(Set.of("FORMAL_ESCALATION_TASK","NEW_BACKUP_TASK"),p.newTimerEvents());
        assertEquals(Set.of("ESCALATION","REASSIGNMENT","EXPIRY","MANUAL_REVIEW"),p.developer3AuditEvents());
        assertFalse(p.automaticApproval());assertFalse(p.automaticPaymentProcessing());
        assertEquals(List.of(86400L,999999999999L,9L),p.slas().stream().map(Sla::durationSeconds).toList());
        assertThrows(UnsupportedOperationException.class,()->p.riskLevels().put("LOW",3));
        assertThrows(UnsupportedOperationException.class,()->p.slas().clear());
    }
    @ParameterizedTest @ValueSource(strings={"start","future","l1-backup","l3-next","l1-terminal","l3-null","auto","zero","overflow"})
    void unknownOrIncompatiblePolicyCombinationsFailClosed(String fault){
        var s=sla(fault.startsWith("l3")?3:1,86400);
        s=switch(fault){
            case "start" -> new Sla(1,86400,"VIEWED",NEXT,null);
            case "future" -> new Sla(1,86400,"APPROVAL_PENDING_V2",NEXT,null);
            case "l1-backup" -> new Sla(1,86400,START,BACKUP,null);
            case "l3-next" -> new Sla(3,86400,START,NEXT,MANUAL);
            case "l1-terminal" -> new Sla(1,86400,START,NEXT,MANUAL);
            case "l3-null" -> new Sla(3,86400,START,BACKUP,null);
            case "auto" -> new Sla(1,86400,START,"AUTO_APPROVE",null);
            case "zero" -> sla(1,0);default -> sla(1,1000000000000L);
        };var invalid=s;assertThrows(IllegalArgumentException.class,()->validateSla(invalid));
    }
    @Test void coverageAndFixedAuthorityAreRequired(){
        assertThrows(IllegalArgumentException.class,()->resolve(Map.of("LOW",3,"MEDIUM",2,"HIGH",1),List.of(sla(1,1),sla(2,1),sla(3,1))));
        assertThrows(IllegalArgumentException.class,()->resolve(BAND_LEVELS,List.of(sla(1,1),sla(1,1),sla(3,1))));
        assertThrows(IllegalArgumentException.class,()->resolve(BAND_LEVELS,List.of(sla(1,1),sla(2,1))));
    }
}
