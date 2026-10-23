package com.ofss.configuration.publication;

import com.ofss.configuration.band.RiskBand;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.ApprovalSla;
import com.ofss.configuration.threshold.ApprovalThreshold;
import com.ofss.configuration.version.ConfigurationVersion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/** Synthetic structural data, not approved routing/SLA policy or database seeds. */
final class PublicationFixtures {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-15T08:09:10.123456Z");
    static ConfigurationVersionDetails parent(String state, long revision) {
        return new ConfigurationVersionDetails(new ConfigurationVersion(7, 3, "Publication fixture Ω", state, true,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), "PUBLISHED".equals(state) ? NOW : null),
                "Synthetic only", "CAP_AT_MAX", 100, revision);
    }
    static List<RiskRule> rules() {
        var result = new ArrayList<RiskRule>();
        for (String code : new TreeSet<>(DraftRiskRuleRequest.RULE_CODES)) {
            Map<String,Object> parameters = switch(code) {
                case "STRUCTURING" -> Map.of("windowSeconds",604800,"minimumPayments",2,"approvalBoundaryLevels",List.of(2,3),"historyEligibility","VALIDATED_SUBMISSIONS");
                case "UNUSUAL_FREQUENCY" -> Map.of("windowSeconds",604800,"maximumSubmissions",3,"historyEligibility","VALIDATED_SUBMISSIONS");
                case "UNUSUAL_AMOUNT" -> Map.of("windowSeconds",7776000,"minimumObservations",5,"meanMultiplier","2","zeroBaselinePolicy","INSUFFICIENT_HISTORY");
                case "PREVIOUS_RISK_FLAGS" -> Map.of("windowSeconds",7776000,"minimumFlaggedPayments",1,"qualifyingRuleCodes",
                    List.of("STRUCTURING","UNUSUAL_FREQUENCY","UNUSUAL_AMOUNT","RECENTLY_REACTIVATED_SUPPLIER","NEW_SUPPLIER","NEAR_DUPLICATE_INVOICE"));
                case "NEAR_DUPLICATE_INVOICE" -> Map.of("windowSeconds",2592000,"historyEligibility","VALIDATED_SUBMISSIONS","invoiceNumberNormalization","EXACT");
                default -> Map.of("windowSeconds",2592000);
            };
            int order = result.size()+1;
            result.add(new RiskRule(order,7,code,"Synthetic Ω",true,1,"risk-policy-v1",parameters,order));
        }
        return List.copyOf(result);
    }
    static List<RiskBand> bands() { return List.of(new RiskBand(11,7,"LOW",0,29,1),new RiskBand(12,7,"MEDIUM",30,60,2),new RiskBand(13,7,"HIGH",61,100,3)); }
    static List<ApprovalThreshold> thresholds() { return List.of(tier(1,"0.00","0.10"),tier(2,"0.10","0.20"),tier(3,"0.20",null)); }
    private static ApprovalThreshold tier(int level,String min,String max) { return new ApprovalThreshold(20+level,7,level,new BigDecimal(min),max==null?null:new BigDecimal(max)); }
    static List<ApprovalSla> slas() { return List.of(sla(1),sla(2),sla(3)); }
    static List<ApprovalSla> approvedSlas() {
        return java.util.stream.IntStream.rangeClosed(1,3).mapToObj(level->new ApprovalSla(30+level,7,level,86400,
            "APPROVAL_PENDING_V1",level<3?"NEXT_LEVEL_V1":"SAME_LEVEL_BACKUP_ONCE_V1",level<3?null:"MANUAL_REVIEW_V1")).toList();
    }
    private static ApprovalSla sla(int level) { return new ApprovalSla(30+level,7,level,1,"UNAGREED_START","UNAGREED_ESCALATION",null); }
}
