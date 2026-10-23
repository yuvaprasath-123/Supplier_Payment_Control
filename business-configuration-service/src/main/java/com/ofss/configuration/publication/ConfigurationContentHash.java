package com.ofss.configuration.publication;

import com.ofss.configuration.band.RiskBand;
import com.ofss.configuration.rule.*;
import com.ofss.configuration.sla.ApprovalSla;
import com.ofss.configuration.threshold.ApprovalThreshold;
import com.ofss.risk.contract.ApprovalPolicyContracts;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Versioned configuration content, not the risk engine's separate snapshot/acceptance hash. */
@Component
public class ConfigurationContentHash {
    public static final String FORMAT = "configuration-content-v1";
    private final JsonMapper mapper;
    public ConfigurationContentHash(JsonMapper mapper) { this.mapper = mapper; }
    public record Document(String json, String hash) {}
    /** V1 stays byte-for-byte stable for historical verification; only new publications use V2. */
    public Document computePublished(ConfigurationVersionDetails parent,long creator,List<RiskRule> rules,
            List<RiskBand> bands,List<ApprovalThreshold> thresholds,List<ApprovalSla> slas) {
        var levels=new HashMap<String,Integer>();for(var band:bands)if(levels.put(band.bandCode(),band.approvalLevel())!=null)throw ConfigurationPublicationRepository.invalid();
        var semantics=ApprovalPolicyContracts.snapshot(levels,slas.stream().map(s->new ApprovalPolicyContracts.Sla(s.approvalLevel(),s.durationSeconds(),s.startEventCode(),s.escalationPolicyCode(),s.terminalLevelPolicyCode())).toList());
        var root=new TreeMap<String,Object>();
        // Reparse with exact types: generic mapper defaults must not round rule parameter decimals.
        var exact=tools.jackson.databind.json.JsonMapper.builder().enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,tools.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();
        exact.readValue(compute(parent,creator,rules,bands,thresholds,slas).json(),Map.class).forEach((k,v)->root.put((String)k,v));
        root.put("hashFormat",ApprovalPolicyContracts.SNAPSHOT_FORMAT);root.put("approvalPolicy",semantics);return document(root);
    }
    public Document compute(ConfigurationVersionDetails parent, long creator, List<RiskRule> rules,
            List<RiskBand> bands, List<ApprovalThreshold> thresholds, List<ApprovalSla> slas) {
        var v = parent.version();
        if (creator < 1 || v.versionId() < 1 || v.createdAt() == null) throw ConfigurationPublicationRepository.invalid();
        for (var rule : rules) sameVersion(v.versionId(), rule.versionId());
        for (var band : bands) sameVersion(v.versionId(), band.versionId());
        for (var tier : thresholds) sameVersion(v.versionId(), tier.versionId());
        for (var sla : slas) sameVersion(v.versionId(), sla.versionId());
        var root = object("hashFormat", FORMAT, "versionId", Long.toString(v.versionId()), "versionNumber", v.versionNumber(),
                "versionLabel", v.versionLabel(), "description", parent.description(), "isDemonstration", v.isDemonstration(),
                "scorePolicyCode", parent.scorePolicyCode(), "maxFinalScore", parent.maxFinalScore(),
                "createdAt", v.createdAt().withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString(), "createdByUserId", Long.toString(creator),
                "riskRules", rules.stream().sorted(Comparator.comparingInt(RiskRule::evaluationOrder).thenComparing(RiskRule::ruleCode))
                    .map(r -> object("ruleConfigId", Long.toString(r.ruleConfigId()), "ruleCode", r.ruleCode(), "displayName", r.displayName(),
                        "enabled", r.enabled(), "weight", r.weight(), "parameterSchemaVersion", r.parameterSchemaVersion(),
                        "parameters", r.parameters(), "evaluationOrder", r.evaluationOrder())).toList(),
                "riskBands", bands.stream().sorted(Comparator.comparingInt(RiskBand::minScoreInclusive).thenComparing(RiskBand::bandCode))
                    .map(b -> object("bandConfigId", Long.toString(b.bandConfigId()), "bandCode", b.bandCode(),
                        "minScoreInclusive", b.minScoreInclusive(), "maxScoreInclusive", b.maxScoreInclusive(), "approvalLevel", b.approvalLevel())).toList(),
                "approvalThresholds", thresholds.stream().sorted(Comparator.comparingInt(ApprovalThreshold::approvalLevel))
                    .map(t -> object("thresholdConfigId", Long.toString(t.thresholdConfigId()), "approvalLevel", t.approvalLevel(),
                        "minAmountInclusive", money(t.minAmountInclusive()), "maxAmountExclusive", money(t.maxAmountExclusive()))).toList(),
                "approvalSlas", slas.stream().sorted(Comparator.comparingInt(ApprovalSla::approvalLevel))
                    .map(s -> object("slaConfigId", Long.toString(s.slaConfigId()), "approvalLevel", s.approvalLevel(), "durationSeconds", s.durationSeconds(),
                        "startEventCode", s.startEventCode(), "escalationPolicyCode", s.escalationPolicyCode(), "terminalLevelPolicyCode", s.terminalLevelPolicyCode())).toList());
        return document(root);
    }
    private Document document(Object root) {
        String json = canonical(root);
        try { return new Document(json, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required"); }
    }
    private void sameVersion(long expected, long actual) { if (expected != actual) throw ConfigurationPublicationRepository.invalid(); }
    private static String money(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY).toPlainString(); }
    private static Map<String, Object> object(Object... pairs) {
        var map = new TreeMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
    private String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return mapper.writeValueAsString(text);
        if (value instanceof Boolean flag) return flag.toString();
        if (value instanceof BigDecimal number) return number.signum() == 0 ? "0" : number.stripTrailingZeros().toPlainString();
        if (value instanceof BigInteger || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return value.toString();
        if (value instanceof List<?> list) return "[" + String.join(",", list.stream().map(this::canonical).toList()) + "]";
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            map.forEach((key, item) -> { if (!(key instanceof String text)) throw ConfigurationPublicationRepository.invalid(); sorted.put(text, item); });
            return "{" + String.join(",", sorted.entrySet().stream().map(e -> canonical(e.getKey()) + ":" + canonical(e.getValue())).toList()) + "}";
        }
        // Never hash an already-rounded floating-point parameter or stringify an unknown type.
        throw ConfigurationPublicationRepository.invalid();
    }
}
