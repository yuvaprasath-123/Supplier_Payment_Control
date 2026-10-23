package com.ofss.configuration.readiness;

import com.ofss.configuration.rule.RiskRule;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/**
 * Readiness must report invalid stored configuration rather than treating a
 * schema-valid JSON array/scalar as a database outage.
 * Existing rule APIs stay unchanged.
 */
@Repository
public class PublicationReadinessRuleRepository {

    static final String QUERY = """
            SELECT RULE_CONFIG_ID,
                   VERSION_ID,
                   RULE_CODE,
                   DISPLAY_NAME,
                   ENABLED,
                   WEIGHT,
                   PARAMETER_SCHEMA_VERSION,
                   PARAMETERS_JSON,
                   EVALUATION_ORDER
            FROM RISK_RULE_CONFIGS
            WHERE VERSION_ID = ?
            ORDER BY EVALUATION_ORDER ASC, RULE_CODE ASC
            """;

    private final JdbcTemplate jdbc;
    private final ObjectReader reader;

    public PublicationReadinessRuleRepository(
            JdbcTemplate jdbc,
            JsonMapper mapper) {

        this.jdbc = jdbc;

        this.reader =
                mapper.readerFor(
                                Object.class)
                        .with(
                                DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                                DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,
                                DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public List<RiskRule> list(
            long versionId) {

        return jdbc.query(
                QUERY,
                (row, index) ->
                        new RiskRule(
                                row.getLong(
                                        "RULE_CONFIG_ID"),
                                row.getLong(
                                        "VERSION_ID"),
                                row.getString(
                                        "RULE_CODE"),
                                row.getString(
                                        "DISPLAY_NAME"),
                                row.getInt(
                                        "ENABLED") == 1,
                                row.getInt(
                                        "WEIGHT"),
                                row.getString(
                                        "PARAMETER_SCHEMA_VERSION"),
                                parameters(
                                        row.getString(
                                                "PARAMETERS_JSON")),
                                row.getInt(
                                        "EVALUATION_ORDER")),
                versionId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parameters(
            String json) {

        if (json == null) {
            return null;
        }

        try {

            Object value =
                    reader.readValue(json);

            // Null is an internal invalid-object marker,
            // never an approval or fallback.
            return value instanceof Map<?, ?>
                    ? (Map<String, Object>) value
                    : null;

        } catch (
                JacksonException exception) {

            return null;
        }
    }
}