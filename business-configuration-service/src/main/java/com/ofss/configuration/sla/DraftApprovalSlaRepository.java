package com.ofss.configuration.sla;

import com.ofss.configuration.error.OracleUniqueConstraint;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DraftApprovalSlaRepository {

    private static final String SELECT_SLA = """
            SELECT SLA_CONFIG_ID,
                   VERSION_ID,
                   APPROVAL_LEVEL,
                   DURATION_SECONDS,
                   START_EVENT_CODE,
                   ESCALATION_POLICY_CODE,
                   TERMINAL_LEVEL_POLICY_CODE
            FROM SLA_CONFIGS
            """;

    static final String LIST_QUERY =
            SELECT_SLA
                    + " WHERE VERSION_ID = ?"
                    + " ORDER BY APPROVAL_LEVEL ASC";

    static final String FIND_QUERY =
            SELECT_SLA
                    + " WHERE VERSION_ID = ?"
                    + " AND APPROVAL_LEVEL = ?";

    static final String INSERT_SLA = """
            INSERT INTO SLA_CONFIGS
                (
                    VERSION_ID,
                    APPROVAL_LEVEL,
                    DURATION_SECONDS,
                    START_EVENT_CODE,
                    ESCALATION_POLICY_CODE,
                    TERMINAL_LEVEL_POLICY_CODE
                )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    static final String UPDATE_SLA = """
            UPDATE SLA_CONFIGS
            SET DURATION_SECONDS = ?,
                START_EVENT_CODE = ?,
                ESCALATION_POLICY_CODE = ?,
                TERMINAL_LEVEL_POLICY_CODE = ?
            WHERE SLA_CONFIG_ID = ?
              AND VERSION_ID = ?
              AND APPROVAL_LEVEL = ?
            """;

    private static final RowMapper<ApprovalSla> MAPPER =
            (row, index) ->
                    new ApprovalSla(
                            row.getLong(
                                    "SLA_CONFIG_ID"),
                            row.getLong(
                                    "VERSION_ID"),
                            row.getInt(
                                    "APPROVAL_LEVEL"),
                            row.getLong(
                                    "DURATION_SECONDS"),
                            row.getString(
                                    "START_EVENT_CODE"),
                            row.getString(
                                    "ESCALATION_POLICY_CODE"),
                            row.getString(
                                    "TERMINAL_LEVEL_POLICY_CODE"));

    private final JdbcTemplate jdbc;

    public DraftApprovalSlaRepository(
            JdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    public List<ApprovalSla> list(
            long versionId) {

        return jdbc.query(
                LIST_QUERY,
                MAPPER,
                versionId);
    }

    public Optional<ApprovalSla> find(
            long versionId,
            int level) {

        var rows =
                jdbc.query(
                        FIND_QUERY,
                        MAPPER,
                        versionId,
                        level);

        if (rows.size() > 1) {

            throw new DataRetrievalFailureException(
                    "Unexpected SLA cardinality");
        }

        return rows.isEmpty()
                ? Optional.empty()
                : Optional.of(
                        rows.getFirst());
    }

    public long save(
            long versionId,
            int level,
            DraftApprovalSlaRequest request,
            ApprovalSla existing) {

        var keys =
                new GeneratedKeyHolder();

        Integer count =
                jdbc.execute(
                        (PreparedStatementCreator)
                                connection -> {

                                    var statement =
                                            existing == null
                                                    ? connection.prepareStatement(
                                                            INSERT_SLA,
                                                            new String[]{
                                                                    "SLA_CONFIG_ID"
                                                            })
                                                    : connection.prepareStatement(
                                                            UPDATE_SLA);

                                    int parameter = 1;

                                    if (existing == null) {

                                        statement.setLong(
                                                parameter++,
                                                versionId);

                                        statement.setInt(
                                                parameter++,
                                                level);
                                    }

                                    statement.setLong(
                                            parameter++,
                                            request.durationSeconds());

                                    statement.setString(
                                            parameter++,
                                            request.startEventCode());

                                    statement.setString(
                                            parameter++,
                                            request.escalationPolicyCode());

                                    if (request.terminalLevelPolicyCode()
                                            == null) {

                                        statement.setNull(
                                                parameter++,
                                                Types.VARCHAR);

                                    } else {

                                        statement.setString(
                                                parameter++,
                                                request.terminalLevelPolicyCode());
                                    }

                                    if (existing != null) {

                                        statement.setLong(
                                                parameter++,
                                                existing.slaConfigId());

                                        statement.setLong(
                                                parameter++,
                                                versionId);

                                        statement.setInt(
                                                parameter,
                                                level);
                                    }

                                    return statement;
                                },

                        statement -> {

                            final int changed;

                            try {

                                changed =
                                        statement.executeUpdate();

                            } catch (
                                    SQLException exception) {

                                if (OracleUniqueConstraint.matches(
                                        exception,
                                        "UK_CFG_SLA_LEVEL")) {

                                    throw new DraftSlaConflictException();
                                }

                                throw exception;
                            }

                            if (existing == null) {

                                try (
                                        var generated =
                                                statement.getGeneratedKeys()) {

                                    if (generated != null) {

                                        keys.getKeyList()
                                                .addAll(
                                                        new RowMapperResultSetExtractor<>(
                                                                new ColumnMapRowMapper())
                                                                .extractData(
                                                                        generated));
                                    }
                                }
                            }

                            return changed;
                        });

        if (count == null
                || count != 1) {

            throw new DataRetrievalFailureException(
                    "SLA was not saved");
        }

        if (existing != null) {

            return existing.slaConfigId();
        }

        Number key =
                keys.getKey();

        if (key == null) {

            throw new DataRetrievalFailureException(
                    "Missing SLA identity");
        }

        try {

            long id =
                    new BigDecimal(
                            key.toString())
                            .longValueExact();

            if (id <= 0) {
                throw new ArithmeticException();
            }

            return id;

        } catch (
                ArithmeticException
                | NumberFormatException exception) {

            throw new DataRetrievalFailureException(
                    "Invalid SLA identity");
        }
    }
}