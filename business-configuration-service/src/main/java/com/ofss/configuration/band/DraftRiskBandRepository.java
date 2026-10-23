package com.ofss.configuration.band;

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
public class DraftRiskBandRepository {

    private static final String SELECT_BAND = """
            SELECT BAND_CONFIG_ID,
                   VERSION_ID,
                   BAND_CODE,
                   MIN_SCORE_INCLUSIVE,
                   MAX_SCORE_INCLUSIVE,
                   APPROVAL_LEVEL
            FROM RISK_BAND_CONFIGS
            """;

    static final String LIST_QUERY =
            SELECT_BAND
                    + " WHERE VERSION_ID = ?"
                    + " ORDER BY MIN_SCORE_INCLUSIVE ASC, BAND_CODE ASC";

    static final String FIND_QUERY =
            SELECT_BAND
                    + " WHERE VERSION_ID = ?"
                    + " AND BAND_CODE = ?";

    static final String INSERT_BAND = """
            INSERT INTO RISK_BAND_CONFIGS
                (
                    VERSION_ID,
                    BAND_CODE,
                    MIN_SCORE_INCLUSIVE,
                    MAX_SCORE_INCLUSIVE,
                    APPROVAL_LEVEL
                )
            VALUES (?, ?, ?, ?, ?)
            """;

    static final String UPDATE_BAND = """
            UPDATE RISK_BAND_CONFIGS
            SET MIN_SCORE_INCLUSIVE = ?,
                MAX_SCORE_INCLUSIVE = ?,
                APPROVAL_LEVEL = ?
            WHERE BAND_CONFIG_ID = ?
              AND VERSION_ID = ?
              AND BAND_CODE = ?
            """;

    private static final RowMapper<RiskBand> MAPPER =
            (row, index) -> {

                int maximum =
                        row.getInt("MAX_SCORE_INCLUSIVE");

                Integer nullableMaximum =
                        row.wasNull()
                                ? null
                                : maximum;

                return new RiskBand(
                        row.getLong("BAND_CONFIG_ID"),
                        row.getLong("VERSION_ID"),
                        row.getString("BAND_CODE"),
                        row.getInt("MIN_SCORE_INCLUSIVE"),
                        nullableMaximum,
                        row.getInt("APPROVAL_LEVEL")
                );
            };

    private final JdbcTemplate jdbc;

    public DraftRiskBandRepository(
            JdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    public List<RiskBand> list(
            long versionId) {

        return jdbc.query(
                LIST_QUERY,
                MAPPER,
                versionId
        );
    }

    public Optional<RiskBand> find(
            long versionId,
            String code) {

        var rows =
                jdbc.query(
                        FIND_QUERY,
                        MAPPER,
                        versionId,
                        code
                );

        if (rows.size() > 1) {

            throw new DataRetrievalFailureException(
                    "Unexpected band cardinality"
            );
        }

        return rows.isEmpty()
                ? Optional.empty()
                : Optional.of(
                        rows.getFirst()
                );
    }

    public long save(
            long versionId,
            String code,
            DraftRiskBandRequest request,
            RiskBand existing) {

        var keys =
                new GeneratedKeyHolder();

        Integer count =
                jdbc.execute(
                        (PreparedStatementCreator)
                                connection -> {

                                    var statement =
                                            existing == null
                                                    ? connection.prepareStatement(
                                                            INSERT_BAND,
                                                            new String[]{
                                                                    "BAND_CONFIG_ID"
                                                            }
                                                    )
                                                    : connection.prepareStatement(
                                                            UPDATE_BAND
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

                                    statement.setInt(
                                            parameter++,
                                            request.minScoreInclusive()
                                    );

                                    if (request.maxScoreInclusive() == null) {

                                        statement.setNull(
                                                parameter++,
                                                Types.INTEGER
                                        );

                                    } else {

                                        statement.setInt(
                                                parameter++,
                                                request.maxScoreInclusive()
                                        );
                                    }

                                    statement.setInt(
                                            parameter++,
                                            request.approvalLevel()
                                    );

                                    if (existing != null) {

                                        statement.setLong(
                                                parameter++,
                                                existing.bandConfigId()
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
                                        "UK_CFG_BAND_CODE"
                                )) {

                                    throw new DraftBandConflictException();
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
                    "Band was not saved"
            );
        }

        if (existing != null) {

            return existing.bandConfigId();
        }

        Number key =
                keys.getKey();

        if (key == null) {

            throw new DataRetrievalFailureException(
                    "Missing band identity"
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
                    "Invalid band identity"
            );
        }
    }
}
