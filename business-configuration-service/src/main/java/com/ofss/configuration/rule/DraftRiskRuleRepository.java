package com.ofss.configuration.rule;

import com.ofss.configuration.error.OracleUniqueConstraint;
import com.ofss.configuration.version.ConfigurationVersion;

import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class DraftRiskRuleRepository {

    static final String VERSION_QUERY = """
            SELECT VERSION_ID,
                   VERSION_NUMBER,
                   VERSION_LABEL,
                   PUBLICATION_STATE,
                   DESCRIPTION,
                   IS_DEMONSTRATION,
                   SCORE_POLICY_CODE,
                   MAX_FINAL_SCORE,
                   CREATED_AT,
                   PUBLISHED_AT,
                   ROW_VERSION
            FROM CONFIGURATION_VERSIONS
            WHERE VERSION_ID = ?
            """;

    static final String LOCK_VERSION_QUERY =
            VERSION_QUERY.stripTrailing() + " FOR UPDATE";

    private static final String RULE_SELECT = """
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
            """;

    static final String LIST_RULES_QUERY =
            RULE_SELECT
                    + " WHERE VERSION_ID = ?"
                    + " ORDER BY EVALUATION_ORDER ASC, RULE_CODE ASC";

    static final String FIND_RULE_QUERY =
            RULE_SELECT
                    + " WHERE VERSION_ID = ?"
                    + " AND RULE_CODE = ?";

    static final String INSERT_RULE = """
            INSERT INTO RISK_RULE_CONFIGS
            (
                VERSION_ID,
                RULE_CODE,
                DISPLAY_NAME,
                ENABLED,
                WEIGHT,
                PARAMETER_SCHEMA_VERSION,
                PARAMETERS_JSON,
                EVALUATION_ORDER
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    static final String UPDATE_RULE = """
            UPDATE RISK_RULE_CONFIGS
            SET DISPLAY_NAME = ?,
                ENABLED = ?,
                WEIGHT = ?,
                PARAMETER_SCHEMA_VERSION = ?,
                PARAMETERS_JSON = ?,
                EVALUATION_ORDER = ?
            WHERE RULE_CONFIG_ID = ?
              AND VERSION_ID = ?
              AND RULE_CODE = ?
            """;

    static final String INCREMENT_PARENT = """
            UPDATE CONFIGURATION_VERSIONS
            SET ROW_VERSION = ROW_VERSION + 1
            WHERE VERSION_ID = ?
              AND ROW_VERSION = ?
              AND PUBLICATION_STATE = 'DRAFT'
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;
    private final ObjectReader parameterReader;

    public DraftRiskRuleRepository(
            JdbcTemplate jdbc,
            JsonMapper mapper) {

        this.jdbc = jdbc;
        this.mapper = mapper;

        this.parameterReader =
                mapper.readerFor(Object.class)
                        .with(
                                DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                                DeserializationFeature.USE_BIG_INTEGER_FOR_INTS
                        );
    }

    private static final RowMapper<ConfigurationVersionDetails>
            VERSION_MAPPER =
            (row, index) -> {

                int maximum =
                        row.getInt("MAX_FINAL_SCORE");

                Integer nullableMaximum =
                        row.wasNull()
                                ? null
                                : maximum;

                return new ConfigurationVersionDetails(
                        new ConfigurationVersion(
                                row.getLong("VERSION_ID"),
                                row.getInt("VERSION_NUMBER"),
                                row.getString("VERSION_LABEL"),
                                row.getString("PUBLICATION_STATE"),
                                row.getInt("IS_DEMONSTRATION") == 1,
                                row.getObject(
                                        "CREATED_AT",
                                        OffsetDateTime.class
                                ),
                                row.getObject(
                                        "PUBLISHED_AT",
                                        OffsetDateTime.class
                                )
                        ),
                        row.getString("DESCRIPTION"),
                        row.getString("SCORE_POLICY_CODE"),
                        nullableMaximum,
                        row.getLong("ROW_VERSION")
                );
            };

    @SuppressWarnings("unchecked")
    private Map<String, Object> parameters(
            String json) {

        try {

            Object decoded =
                    parameterReader.readValue(json);

            if (!(decoded instanceof Map<?, ?>)) {

                throw new DataRetrievalFailureException(
                        "Invalid stored rule JSON"
                );
            }

            return (Map<String, Object>) decoded;

        } catch (
                tools.jackson.core.JacksonException exception
        ) {

            throw new DataRetrievalFailureException(
                    "Invalid stored rule JSON"
            );
        }
    }

    private RowMapper<RiskRule> ruleMapper() {

        return (row, index) ->
                new RiskRule(
                        row.getLong("RULE_CONFIG_ID"),
                        row.getLong("VERSION_ID"),
                        row.getString("RULE_CODE"),
                        row.getString("DISPLAY_NAME"),
                        row.getInt("ENABLED") == 1,
                        row.getInt("WEIGHT"),
                        row.getString(
                                "PARAMETER_SCHEMA_VERSION"
                        ),
                        parameters(
                                row.getString("PARAMETERS_JSON")
                        ),
                        row.getInt("EVALUATION_ORDER")
                );
    }

    public Optional<ConfigurationVersionDetails> findVersion(
            long id,
            boolean lock) {

        return single(
                jdbc.query(
                        lock
                                ? LOCK_VERSION_QUERY
                                : VERSION_QUERY,
                        VERSION_MAPPER,
                        id
                )
        );
    }

    public List<RiskRule> listRules(
            long id) {

        return jdbc.query(
                LIST_RULES_QUERY,
                ruleMapper(),
                id
        );
    }

    public Optional<RiskRule> findRule(
            long id,
            String code) {

        return single(
                jdbc.query(
                        FIND_RULE_QUERY,
                        ruleMapper(),
                        id,
                        code
                )
        );
    }

    private static <T> Optional<T> single(
            List<T> rows) {

        if (rows.size() > 1) {

            throw new DataRetrievalFailureException(
                    "Unexpected configuration cardinality"
            );
        }

        return rows.isEmpty()
                ? Optional.empty()
                : Optional.of(
                        rows.getFirst()
                );
    }

    public long saveRule(
            long versionId,
            String code,
            DraftRiskRuleRequest request,
            RiskRule existing) {

        String json =
                mapper.writeValueAsString(
                        request.parameters()
                );

        var keys =
                new GeneratedKeyHolder();

        Integer count =
                jdbc.execute(
                        (PreparedStatementCreator)
                                connection -> {

                                    var statement =
                                            existing == null
                                                    ? connection.prepareStatement(
                                                            INSERT_RULE,
                                                            new String[]{
                                                                    "RULE_CONFIG_ID"
                                                            }
                                                    )
                                                    : connection.prepareStatement(
                                                            UPDATE_RULE
                                                    );

                                    int parameter = 1;

                                    if (existing == null) {

                                        statement.setLong(
                                                parameter++,
                                                versionId
                                        );

                                        statement.setString(
                                                parameter++,
                                                code
                                        );
                                    }

                                    statement.setString(
                                            parameter++,
                                            request.displayName()
                                    );

                                    statement.setInt(
                                            parameter++,
                                            request.enabled()
                                                    ? 1
                                                    : 0
                                    );

                                    statement.setInt(
                                            parameter++,
                                            request.weight()
                                    );

                                    statement.setString(
                                            parameter++,
                                            request.parameterSchemaVersion()
                                    );

                                    statement.setCharacterStream(
                                            parameter++,
                                            new StringReader(json),
                                            json.length()
                                    );

                                    statement.setInt(
                                            parameter++,
                                            request.evaluationOrder()
                                    );

                                    if (existing != null) {

                                        statement.setLong(
                                                parameter++,
                                                existing.ruleConfigId()
                                        );

                                        statement.setLong(
                                                parameter++,
                                                versionId
                                        );

                                        statement.setString(
                                                parameter,
                                                code
                                        );
                                    }

                                    return statement;
                                },

                        statement -> {

                            final int changed;

                            try {

                                changed =
                                        statement.executeUpdate();

                            } catch (
                                    SQLException exception
                            ) {

                                if (OracleUniqueConstraint.matches(
                                        exception,
                                        "UK_CFG_RULE_ORDER"
                                )) {

                                    throw new DraftRuleConflictException(
                                            DraftRuleConflictException.Reason
                                                    .RULE_ORDER_CONFLICT
                                    );
                                }

                                if (OracleUniqueConstraint.matches(
                                        exception,
                                        "UK_CFG_RULE_CODE"
                                )) {

                                    throw new DraftRuleConflictException(
                                            DraftRuleConflictException.Reason
                                                    .RULE_CODE_CONFLICT
                                    );
                                }

                                throw exception;
                            }

                            if (existing == null) {

                                try (
                                        var generated =
                                                statement.getGeneratedKeys()
                                ) {

                                    if (generated != null) {

                                        keys.getKeyList()
                                                .addAll(
                                                        new RowMapperResultSetExtractor<>(
                                                                new ColumnMapRowMapper()
                                                        ).extractData(
                                                                generated
                                                        )
                                                );
                                    }
                                }
                            }

                            return changed;
                        }
                );

        if (count == null || count != 1) {

            throw new DataRetrievalFailureException(
                    "Rule was not saved"
            );
        }

        if (existing != null) {

            return existing.ruleConfigId();
        }

        Number key =
                keys.getKey();

        if (key == null) {

            throw new DataRetrievalFailureException(
                    "Missing rule identity"
            );
        }

        try {

            long id =
                    new BigDecimal(
                            key.toString()
                    ).longValueExact();

            if (id <= 0) {
                throw new ArithmeticException();
            }

            return id;

        } catch (
                ArithmeticException
                | NumberFormatException exception
        ) {

            throw new DataRetrievalFailureException(
                    "Invalid rule identity"
            );
        }
    }

    public void incrementParent(
            long id,
            long expected) {

        if (jdbc.update(
                INCREMENT_PARENT,
                id,
                expected
        ) != 1) {

            throw new DraftRuleConflictException(
                    DraftRuleConflictException.Reason
                            .STALE_VERSION
            );
        }
    }
}
