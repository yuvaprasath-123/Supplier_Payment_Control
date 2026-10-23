package com.ofss.risk.contract;

import java.util.*;

/** Immutable normative meanings of the versioned codes stored in each published SLA.
 * Changing a meaning requires NEW codes; consumers must never reinterpret these identifiers.
 * No default duration or backup person is inserted by this contract.
 */
public final class ApprovalPolicyContracts {
    public static final String VERSION = "approval-policy-v1";
    public static final String SNAPSHOT_FORMAT = "configuration-content-v2";
    public static final String START = "APPROVAL_PENDING_V1";
    public static final String NEXT = "NEXT_LEVEL_V1";
    public static final String BACKUP = "SAME_LEVEL_BACKUP_ONCE_V1";
    public static final String MANUAL = "MANUAL_REVIEW_V1";
    public static final Map<String,Integer> BAND_LEVELS = Map.of("LOW",1,"MEDIUM",2,"HIGH",3);
    private ApprovalPolicyContracts() {}

    public record Sla(int approvalLevel, long durationSeconds, String startEventCode,
            String escalationPolicyCode, String terminalLevelPolicyCode) {}
    public record Policy(String version, String currency, Map<String,Integer> riskLevels,
            String authority, Set<Integer> humanAuthorizationLevels, String timerStart,
            boolean viewingResetsDeadline, boolean ordinaryReassignmentResetsDeadline,
            List<Integer> escalationOrder, Map<Integer,String> primaryExpiryActions,
            String newTaskDuration, int maximumSameLevelBackups, String backupEligibility,
            String absentBackupAction, String backupExpiryAction, Set<String> newTimerEvents,
            Set<String> developer3AuditEvents, boolean automaticApproval, boolean automaticPaymentProcessing,
            List<Sla> slas) {
        public Policy {
            riskLevels=Map.copyOf(riskLevels);humanAuthorizationLevels=Set.copyOf(humanAuthorizationLevels);
            escalationOrder=List.copyOf(escalationOrder);newTimerEvents=Set.copyOf(newTimerEvents);
            primaryExpiryActions=Map.copyOf(primaryExpiryActions);
            developer3AuditEvents=Set.copyOf(developer3AuditEvents);slas=List.copyOf(slas);
        }
    }
    public static void validateSla(Sla sla) {
        if(sla==null || sla.approvalLevel()<1 || sla.approvalLevel()>3
                || sla.durationSeconds()<1 || sla.durationSeconds()>999999999999L
                || !START.equals(sla.startEventCode())
                || !(sla.approvalLevel()<3?NEXT:BACKUP).equals(sla.escalationPolicyCode())
                || !Objects.equals(sla.approvalLevel()<3?null:MANUAL,sla.terminalLevelPolicyCode()))
            throw new IllegalArgumentException("Unsupported approval-policy-v1 SLA combination");
    }
    /** Lossless expansion of the hashed code references, for approval consumers. */
    public static Policy resolve(Map<String,Integer> bandLevels, List<Sla> slas) {
        if(!BAND_LEVELS.equals(bandLevels) || slas==null || slas.size()!=3
                || !slas.stream().map(Sla::approvalLevel).collect(java.util.stream.Collectors.toSet()).equals(Set.of(1,2,3)))
            throw new IllegalArgumentException("Unsupported approval-policy-v1 authority/coverage");
        slas.forEach(ApprovalPolicyContracts::validateSla);
        return new Policy(VERSION,"INR",BAND_LEVELS,"MAX_AMOUNT_TIER_AND_RISK_LEVEL",Set.of(1,2,3),
                "TASK_ENTERS_PENDING",false,false,List.of(1,2,3),
                Map.of(1,"ESCALATE_TO_L2",2,"ESCALATE_TO_L3",3,"ONE_ELIGIBLE_L3_BACKUP_OR_MANUAL_REVIEW"),
                "DESTINATION_LEVEL_CONFIGURED_DURATION",1,
                "DEVELOPER3_CONFIGURED_ELIGIBLE_SAME_LEVEL_BACKUP", "MANUAL_REVIEW","MANUAL_REVIEW",
                Set.of("FORMAL_ESCALATION_TASK","NEW_BACKUP_TASK"),
                Set.of("ESCALATION","REASSIGNMENT","EXPIRY","MANUAL_REVIEW"),false,false,
                slas.stream().sorted(Comparator.comparingInt(Sla::approvalLevel)).toList());
    }
    /** Explicit, deterministic semantic content for publication hashing. No serializer-dependent Set/Map order. */
    public static Map<String,Object> snapshot(Map<String,Integer> bands,List<Sla> slas) {
        var p=resolve(bands,slas);var values=new TreeMap<String,Object>();
        values.put("version",p.version());values.put("currency",p.currency());values.put("riskLevels",p.riskLevels());
        values.put("authority",p.authority());values.put("humanAuthorizationLevels",p.humanAuthorizationLevels().stream().sorted().toList());
        values.put("timerStart",p.timerStart());values.put("viewingResetsDeadline",p.viewingResetsDeadline());
        values.put("ordinaryReassignmentResetsDeadline",p.ordinaryReassignmentResetsDeadline());
        values.put("escalationOrder",p.escalationOrder());
        values.put("primaryExpiryActions",Map.of("1",p.primaryExpiryActions().get(1),"2",p.primaryExpiryActions().get(2),"3",p.primaryExpiryActions().get(3)));
        values.put("newTaskDuration",p.newTaskDuration());values.put("maximumSameLevelBackups",p.maximumSameLevelBackups());
        values.put("backupEligibility",p.backupEligibility());values.put("backupCountScope","APPROVAL_CHAIN");
        values.put("backupRequiresSameLevelAuthorization",true);
        values.put("absentBackupAction",p.absentBackupAction());values.put("backupExpiryAction",p.backupExpiryAction());
        values.put("newTimerEvents",p.newTimerEvents().stream().sorted().toList());
        values.put("developer3AuditEvents",p.developer3AuditEvents().stream().sorted().toList());
        values.put("automaticApproval",p.automaticApproval());values.put("automaticPaymentProcessing",p.automaticPaymentProcessing());
        values.put("slas",p.slas().stream().map(s->{var row=new TreeMap<String,Object>();row.put("approvalLevel",s.approvalLevel());
            row.put("durationSeconds",s.durationSeconds());row.put("startEventCode",s.startEventCode());
            row.put("escalationPolicyCode",s.escalationPolicyCode());row.put("terminalLevelPolicyCode",s.terminalLevelPolicyCode());
            return Collections.unmodifiableMap(row);}).toList());
        return Collections.unmodifiableMap(values);
    }
}
