package com.ofss.configuration.threshold;

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
public class DraftApprovalThresholdRepository {

    private static final String SELECT_THRESHOLD = """
            SELECT THRESHOLD_CONFIG_ID,
                   VERSION_ID,
                   APPROVAL_LEVEL,
                   MIN_AMOUNT_INCLUSIVE,
                   MAX_AMOUNT_EXCLUSIVE
            FROM APPROVAL_THRESHOLD_CONFIGS
            """;

    static final String LIST_QUERY =
            SELECT_THRESHOLD
                    + " WHERE VERSION_ID = ?"
                    + " ORDER BY MIN_AMOUNT_INCLUSIVE ASC, APPROVAL_LEVEL ASC";

    static final String FIND_QUERY =
            SELECT_THRESHOLD
                    + " WHERE VERSION_ID = ?"
                    + " AND APPROVAL_LEVEL = ?";

    static final String INSERT_THRESHOLD = """
            INSERT INTO APPROVAL_THRESHOLD_CONFIGS
                (
                    VERSION_ID,
                    APPROVAL_LEVEL,
                    MIN_AMOUNT_INCLUSIVE,
                    MAX_AMOUNT_EXCLUSIVE
                )
            VALUES (?, ?, ?, ?)
            """;

    static final String UPDATE_THRESHOLD = """
            UPDATE APPROVAL_THRESHOLD_CONFIGS
            SET MIN_AMOUNT_INCLUSIVE = ?,
                MAX_AMOUNT_EXCLUSIVE = ?
            WHERE THRESHOLD_CONFIG_ID = ?
              AND VERSION_ID = ?
              AND APPROVAL_LEVEL = ?
            """;

    private static final RowMapper<ApprovalThreshold> MAPPER =
            (row, index) ->
                    new ApprovalThreshold(
                            row.getLong(
                                    "THRESHOLD_CONFIG_ID"),
                            row.getLong(
                                    "VERSION_ID"),
                            row.getInt(
                                    "APPROVAL_LEVEL"),
                            row.getBigDecimal(
                                    "MIN_AMOUNT_INCLUSIVE"),
                            row.getBigDecimal(
                                    "MAX_AMOUNT_EXCLUSIVE"));

    private final JdbcTemplate jdbc;

    public DraftApprovalThresholdRepository(
            JdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    public List<ApprovalThreshold> list(
            long versionId) {

        return jdbc.query(
                LIST_QUERY,
                MAPPER,
                versionId);
    }

    public Optional<ApprovalThreshold> find(
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
                    "Unexpected threshold cardinality");
        }

        return rows.isEmpty()
                ? Optional.empty()
                : Optional.of(
                        rows.getFirst());
    }

    public long save(
            long versionId,
            int level,
            DraftApprovalThresholdRequest request,
            ApprovalThreshold existing) {

        var keys =
                new GeneratedKeyHolder();

        Integer count =
                jdbc.execute(
                        (PreparedStatementCreator)
                                connection -> {

                                    var statement =
                                            existing == null
                                                    ? connection.prepareStatement(
                                                            INSERT_THRESHOLD,
                                                            new String[]{
                                                                    "THRESHOLD_CONFIG_ID"
                                                            })
                                                    : connection.prepareStatement(
                                                            UPDATE_THRESHOLD);

                                    int parameter = 1;

                                    if (existing == null) {

                                        statement.setLong(
                                                parameter++,
                                                versionId);

                                        statement.setInt(
                                                parameter++,
                                                level);
                                    }

                                    statement.setBigDecimal(
                                            parameter++,
                                            request.minAmountInclusive());

                                    if (request.maxAmountExclusive()
                                            == null) {

                                        statement.setNull(
                                                parameter++,
                                                Types.NUMERIC);

                                    } else {

                                        statement.setBigDecimal(
                                                parameter++,
                                                request.maxAmountExclusive());
                                    }

                                    if (existing != null) {

                                        statement.setLong(
                                                parameter++,
                                                existing.thresholdConfigId());

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
                                        "UK_CFG_THRESHOLD_LEVEL")) {

                                    throw new DraftThresholdConflictException();
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
                    "Threshold was not saved");
        }

        if (existing != null) {

            return existing.thresholdConfigId();
        }

        Number key =
                keys.getKey();

        if (key == null) {

            throw new DataRetrievalFailureException(
                    "Missing threshold identity");
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
                    "Invalid threshold identity");
        }
    }
}
