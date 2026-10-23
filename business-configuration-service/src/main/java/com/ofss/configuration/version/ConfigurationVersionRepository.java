package com.ofss.configuration.version;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ConfigurationVersionRepository {

    private static final String LIST_QUERY = """
            SELECT
                VERSION_ID,
                VERSION_NUMBER,
                VERSION_LABEL,
                PUBLICATION_STATE,
                IS_DEMONSTRATION,
                CREATED_AT,
                PUBLISHED_AT
            FROM CONFIGURATION_VERSIONS
            ORDER BY VERSION_NUMBER ASC, VERSION_ID ASC
            """;

    private static final String LATEST_VERSION_QUERY = """
            SELECT NVL(MAX(VERSION_NUMBER), 0)
            FROM CONFIGURATION_VERSIONS
            """;

    private static final String INSERT_DRAFT = """
            INSERT INTO CONFIGURATION_VERSIONS
            (
                VERSION_NUMBER,
                VERSION_LABEL,
                PUBLICATION_STATE,
                DESCRIPTION,
                IS_DEMONSTRATION,
                SCORE_POLICY_CODE,
                MAX_FINAL_SCORE,
                CREATED_AT,
                CREATED_BY_USER_ID
            )
            VALUES (?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_BY_ID_QUERY = """
            SELECT
                VERSION_ID,
                VERSION_NUMBER,
                VERSION_LABEL,
                PUBLICATION_STATE,
                IS_DEMONSTRATION,
                CREATED_AT,
                PUBLISHED_AT
            FROM CONFIGURATION_VERSIONS
            WHERE VERSION_ID = ?
            """;

    private static final RowMapper<ConfigurationVersion>
            VERSION_MAPPER =
            (row, index) ->
                    new ConfigurationVersion(
                            row.getLong("VERSION_ID"),
                            row.getInt("VERSION_NUMBER"),
                            row.getString("VERSION_LABEL"),
                            row.getString(
                                    "PUBLICATION_STATE"
                            ),
                            row.getInt(
                                    "IS_DEMONSTRATION"
                            ) == 1,
                            row.getObject(
                                    "CREATED_AT",
                                    OffsetDateTime.class
                            ),
                            row.getObject(
                                    "PUBLISHED_AT",
                                    OffsetDateTime.class
                            )
                    );

    private final JdbcTemplate jdbc;

    public ConfigurationVersionRepository(
            JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ConfigurationVersion> findAll() {

        return jdbc.query(
                LIST_QUERY,
                VERSION_MAPPER
        );
    }

    public long latestVersionNumber() {

        Long latest =
                jdbc.queryForObject(
                        LATEST_VERSION_QUERY,
                        Long.class
                );

        if (latest == null || latest < 0) {
            throw new DataRetrievalFailureException(
                    "Invalid version allocation data"
            );
        }

        return latest;
    }

    public ConfigurationVersion insertDraft(
            CreateConfigurationVersionRequest request,
            int versionNumber,
            long actorId,
            OffsetDateTime createdAt) {

        GeneratedKeyHolder keys =
                new GeneratedKeyHolder();

        Integer inserted =
                jdbc.execute(
                        (PreparedStatementCreator)
                                connection -> {

                                    var statement =
                                            connection
                                                    .prepareStatement(
                                                            INSERT_DRAFT,
                                                            new String[]{
                                                                    "VERSION_ID"
                                                            }
                                                    );

                                    statement.setInt(
                                            1,
                                            versionNumber
                                    );

                                    statement.setString(
                                            2,
                                            request.versionLabel()
                                    );

                                    statement.setString(
                                            3,
                                            request.description()
                                    );

                                    statement.setInt(
                                            4,
                                            request.isDemonstration()
                                                    ? 1
                                                    : 0
                                    );

                                    statement.setString(
                                            5,
                                            request.scorePolicyCode()
                                    );

                                    if (request.maxFinalScore()
                                            == null) {

                                        statement.setNull(
                                                6,
                                                Types.INTEGER
                                        );

                                    } else {

                                        statement.setInt(
                                                6,
                                                request.maxFinalScore()
                                        );
                                    }

                                    statement.setObject(
                                            7,
                                            createdAt,
                                            Types.TIMESTAMP_WITH_TIMEZONE
                                    );

                                    statement.setLong(
                                            8,
                                            actorId
                                    );

                                    return statement;
                                },

                        statement -> {

                            final int count;

                            try {

                                count =
                                        statement.executeUpdate();

                            } catch (
                                    SQLException exception
                            ) {

                                if (OracleVersionNumberConflict
                                        .matches(exception)) {

                                    throw new
                                            VersionAllocationConflictException();
                                }

                                throw exception;
                            }

                            try (
                                    var generated =
                                            statement
                                                    .getGeneratedKeys()
                            ) {

                                if (generated != null) {

                                    var extractor =
                                            new RowMapperResultSetExtractor<>(
                                                    new ColumnMapRowMapper()
                                            );

                                    keys.getKeyList()
                                            .addAll(
                                                    extractor.extractData(
                                                            generated
                                                    )
                                            );
                                }
                            }

                            return count;
                        }
                );

        Number key =
                keys.getKey();

        if (inserted == null
                || inserted != 1
                || key == null) {

            throw new DataRetrievalFailureException(
                    "Missing created version identity"
            );
        }

        final long versionId;

        try {

            versionId =
                    new BigDecimal(
                            key.toString()
                    ).longValueExact();

        } catch (
                ArithmeticException
                | NumberFormatException exception
        ) {

            throw new DataRetrievalFailureException(
                    "Invalid created version identity",
                    exception
            );
        }

        if (versionId <= 0) {

            throw new DataRetrievalFailureException(
                    "Invalid created version identity"
            );
        }

        ConfigurationVersion created =
                jdbc.queryForObject(
                        FIND_BY_ID_QUERY,
                        VERSION_MAPPER,
                        versionId
                );

        if (created == null) {

            throw new DataRetrievalFailureException(
                    "Missing created version"
            );
        }

        return created;
    }
}