package com.ofss.configuration.publication;

import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.ApprovalSla;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static com.ofss.configuration.publication.PublicationFixtures.*;

class ConfigurationContentHashTest {
    private final ConfigurationContentHash hashes = new ConfigurationContentHash(JsonMapper.builder().build());
    @Test void newHashCoversExplicitSemanticsWhileHistoricalV1RemainsUnexpanded(){
        var old=hashes.compute(parent("DRAFT",8),42,rules(),bands(),thresholds(),approvedSlas());
        var current=hashes.computePublished(parent("DRAFT",8),42,rules(),bands(),thresholds(),approvedSlas());
        assertThat(old.json()).contains("\"hashFormat\":\"configuration-content-v1\"").doesNotContain("\"approvalPolicy\"");
        assertThat(current.json()).contains("\"hashFormat\":\"configuration-content-v2\"","\"approvalPolicy\"","\"humanAuthorizationLevels\":[1,2,3]",
            "\"authority\":\"MAX_AMOUNT_TIER_AND_RISK_LEVEL\"","\"automaticApproval\":false","\"automaticPaymentProcessing\":false",
            "\"backupRequiresSameLevelAuthorization\":true","\"backupCountScope\":\"APPROVAL_CHAIN\"","\"developer3AuditEvents\"");
        assertThat(current.hash()).isNotEqualTo(old.hash());
        var changed=new ArrayList<>(approvedSlas());var first=changed.getFirst();
        changed.set(0,new ApprovalSla(first.slaConfigId(),7,1,86401,first.startEventCode(),first.escalationPolicyCode(),null));
        assertThat(hashes.computePublished(parent("DRAFT",8),42,rules(),bands(),thresholds(),changed).hash()).isNotEqualTo(current.hash());
        assertThat(hashes.computePublished(parent("PUBLISHED",9),42,rules(),bands(),thresholds(),approvedSlas())).isEqualTo(current);
    }
    private ConfigurationContentHash.Document hash(List<RiskRule> rules) { return hashes.compute(parent("DRAFT",8),42,rules,bands(),thresholds(),slas()); }
    @Test void canonicalHashIsStableAcrossChildAndObjectOrderingAndExactNumericRepresentations() {
        var a=new LinkedHashMap<String,Object>(); a.put("z",new BigInteger("9007199254740993"));
        a.put("a",List.of(Map.of("decimal",new BigDecimal("1.0000000000000001")),new BigDecimal("1E+3")));
        var b=new LinkedHashMap<String,Object>(); b.put("a",List.of(Map.of("decimal",new BigDecimal("1.0000000000000001")),new BigInteger("1000"))); b.put("z",new BigInteger("9007199254740993"));
        var first=replaceParameters(a); var second=new ArrayList<>(replaceParameters(b)); Collections.reverse(second);
        assertThat(hash(first)).isEqualTo(hash(second));
        assertThat(hash(first).json()).contains("1.0000000000000001","9007199254740993","1000","Ω","\"hashFormat\":\"configuration-content-v1\"",
            "\"maxAmountExclusive\":null","\"minAmountInclusive\":\"0.00\"");
        assertThat(hash(first).hash()).matches("[0-9a-f]{64}");
    }
    @Test void meaningfulArrayOrderUnicodeAndTinyDecimalDifferencesChangeHash() {
        assertThat(hash(replaceParameters(Map.of("values",List.of(1,2))))).isNotEqualTo(hash(replaceParameters(Map.of("values",List.of(2,1)))));
        assertThat(hash(replaceParameters(Map.of("x",new BigDecimal("1.0000000000000001"))))).isNotEqualTo(hash(replaceParameters(Map.of("x",BigDecimal.ONE))));
        assertThat(hash(replaceParameters(Map.of("name","Ω")))).isNotEqualTo(hash(replaceParameters(Map.of("name","O"))));
    }
    @Test void revisionAndPublicationMetadataAreExcludedButCreationProvenanceIsIncluded() {
        var a=hash(rules());
        assertThat(hashes.compute(parent("PUBLISHED",9),42,rules(),bands(),thresholds(),slas())).isEqualTo(a);
        assertThat(hashes.compute(parent("DRAFT",8),43,rules(),bands(),thresholds(),slas())).isNotEqualTo(a);
        var original=parent("DRAFT",8); var changed=new ConfigurationVersionDetails(original.version(),null,original.scorePolicyCode(),100,8);
        assertThat(hashes.compute(changed,42,rules(),bands(),thresholds(),slas())).isNotEqualTo(a);
    }
    @Test void everyConfigurationSectionParticipatesIncludingNullablePolicies() {
        var changed=new ArrayList<>(slas()); var s=changed.getFirst();
        changed.set(0,new ApprovalSla(s.slaConfigId(),7,1,2,s.startEventCode(),s.escalationPolicyCode(),"UNAGREED_TERMINAL"));
        assertThat(hashes.compute(parent("DRAFT",8),42,rules(),bands(),thresholds(),changed)).isNotEqualTo(hash(rules()));
        assertThat(hashes.compute(parent("DRAFT",8),42,rules(),List.of(),thresholds(),slas())).isNotEqualTo(hash(rules()));
        assertThat(hashes.compute(parent("DRAFT",8),42,rules(),bands(),List.of(),slas())).isNotEqualTo(hash(rules()));
    }
    @ParameterizedTest @ValueSource(doubles={1.1,Double.NaN,Double.POSITIVE_INFINITY})
    void alreadyRoundedOrNonfiniteParametersFailClosed(double value) { assertThatThrownBy(()->hash(replaceParameters(Map.of("x",value)))).isInstanceOf(org.springframework.dao.DataRetrievalFailureException.class); }
    private List<RiskRule> replaceParameters(Map<String,Object> value) {
        var result=new ArrayList<>(rules()); var r=result.getFirst();
        result.set(0,new RiskRule(r.ruleConfigId(),7,r.ruleCode(),r.displayName(),r.enabled(),r.weight(),r.parameterSchemaVersion(),value,r.evaluationOrder())); return result;
    }
}
