package com.ofss.configuration.publication;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class ConfigurationPublicationRepository {

    static final String METADATA = """
            SELECT CREATED_BY_USER_ID,
                   PUBLISHED_AT,
                   PUBLISHED_BY_USER_ID,
                   CONTENT_HASH
            FROM CONFIGURATION_VERSIONS
            WHERE VERSION_ID = ?
            """;

    static final String PUBLISH = """
            UPDATE CONFIGURATION_VERSIONS
            SET PUBLICATION_STATE = 'PUBLISHED',
                PUBLISHED_AT = ?,
                PUBLISHED_BY_USER_ID = ?,
                CONTENT_HASH = ?,
                ROW_VERSION = ROW_VERSION + 1
            WHERE VERSION_ID = ?
              AND ROW_VERSION = ?
              AND PUBLICATION_STATE = 'DRAFT'
              AND PUBLISHED_AT IS NULL
              AND PUBLISHED_BY_USER_ID IS NULL
              AND CONTENT_HASH IS NULL
            """;

    public record Metadata(
            long createdByUserId,
            OffsetDateTime publishedAt,
            Long publishedByUserId,
            String contentHash) {
    }

    private final JdbcTemplate jdbc;

    public ConfigurationPublicationRepository(
            JdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    private void bound() {

        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(
                        jdbc.getDataSource())) {

            throw invalid();
        }
    }

    private static long id(
            Object value) {

        try {

            if (!(value instanceof Number)
                    || value instanceof Double
                    || value instanceof Float) {

                throw invalid();
            }

            long id =
                    new BigDecimal(
                            value.toString())
                            .longValueExact();

            if (id < 1) {
                throw invalid();
            }

            return id;

        } catch (
                ArithmeticException
                | NumberFormatException exception) {

            throw invalid();
        }
    }

    public Metadata metadata(
            long versionId) {

        bound();

        var rows =
                jdbc.query(
                        METADATA,
                        ps -> ps.setLong(
                                1,
                                versionId),
                        (rs, index) -> {

                            Object publisher =
                                    rs.getObject(
                                            "PUBLISHED_BY_USER_ID");

                            return new Metadata(
                                    id(
                                            rs.getObject(
                                                    "CREATED_BY_USER_ID")),
                                    rs.getObject(
                                            "PUBLISHED_AT",
                                            OffsetDateTime.class),
                                    publisher == null
                                            ? null
                                            : id(publisher),
                                    rs.getString(
                                            "CONTENT_HASH"));
                        });

        if (rows.size() != 1) {
            throw invalid();
        }

        return rows.getFirst();
    }

    public void publish(
            long versionId,
            long expected,
            long publisher,
            OffsetDateTime time,
            String hash) {

        bound();

        if (versionId < 1
                || expected < 0
                || expected == Long.MAX_VALUE
                || publisher < 1
                || time == null
                || time.getOffset().getTotalSeconds() != 0
                || time.getNano() % 1000 != 0
                || hash == null
                || !hash.matches("[0-9a-f]{64}")) {

            throw invalid();
        }

        int changed =
                jdbc.update(
                        PUBLISH,
                        ps -> {

                            ps.setObject(
                                    1,
                                    time);

                            ps.setLong(
                                    2,
                                    publisher);

                            ps.setString(
                                    3,
                                    hash);

                            ps.setLong(
                                    4,
                                    versionId);

                            ps.setLong(
                                    5,
                                    expected);
                        });

        if (changed != 1) {
            throw invalid();
        }
    }

    static DataRetrievalFailureException invalid() {

        return new DataRetrievalFailureException(
                "Invalid configuration publication state");
    }
}
