package com.ofss.configuration.activation;

import com.ofss.configuration.error.OracleUniqueConstraint;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class ConfigurationActivationRepository {

    // A row lock alone cannot serialize first activation
    // of an empty singleton table.
    static final String LOCK =
            "LOCK TABLE SUPPLIER_PAYMENT_APP.CURRENT_CONFIGURATION " +
            "IN EXCLUSIVE MODE WAIT 5";

    static final String POINTER =
            "SELECT POINTER_ID, VERSION_ID, ACTIVATED_AT, " +
            "ACTIVATED_BY_USER_ID, ROW_VERSION " +
            "FROM SUPPLIER_PAYMENT_APP.CURRENT_CONFIGURATION";

    static final String INSERT =
            "INSERT INTO SUPPLIER_PAYMENT_APP.CURRENT_CONFIGURATION " +
            "(POINTER_ID, VERSION_ID, ACTIVATED_AT, " +
            "ACTIVATED_BY_USER_ID, ROW_VERSION) " +
            "VALUES (1, ?, ?, ?, 0)";

    static final String UPDATE =
            "UPDATE SUPPLIER_PAYMENT_APP.CURRENT_CONFIGURATION " +
            "SET VERSION_ID = ?, " +
            "ACTIVATED_AT = ?, " +
            "ACTIVATED_BY_USER_ID = ?, " +
            "ROW_VERSION = ROW_VERSION + 1 " +
            "WHERE POINTER_ID = 1 " +
            "AND VERSION_ID = ? " +
            "AND ROW_VERSION = ?";

    static final String METADATA =
            "SELECT CREATED_BY_USER_ID, PUBLISHED_AT, " +
            "PUBLISHED_BY_USER_ID, CONTENT_HASH " +
            "FROM SUPPLIER_PAYMENT_APP.CONFIGURATION_VERSIONS " +
            "WHERE VERSION_ID = ?";

    public record Pointer(
            long versionId,
            OffsetDateTime activatedAt,
            long activatedBy,
            long rowVersion) {
    }

    public record Metadata(
            long creator,
            OffsetDateTime publishedAt,
            long publisher,
            String hash) {
    }

    private final JdbcTemplate jdbc;

    public ConfigurationActivationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private void bound(boolean write) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(
                        jdbc.getDataSource())
                || (write
                    && TransactionSynchronizationManager
                        .isCurrentTransactionReadOnly())) {

            throw invalid();
        }
    }

    public void lockPointer() {
        bound(true);
        jdbc.execute(LOCK);
    }

    public Optional<Pointer> pointer() {
        bound(false);

        var rows = jdbc.query(
                POINTER,
                (rs, n) -> {

                    if (number(
                            rs.getObject("POINTER_ID"),
                            false) != 1) {

                        throw invalid();
                    }

                    var time = rs.getObject(
                            "ACTIVATED_AT",
                            OffsetDateTime.class);

                    if (time == null) {
                        throw invalid();
                    }

                    return new Pointer(
                            number(
                                    rs.getObject("VERSION_ID"),
                                    false),
                            time,
                            number(
                                    rs.getObject(
                                            "ACTIVATED_BY_USER_ID"),
                                    false),
                            number(
                                    rs.getObject("ROW_VERSION"),
                                    true));
                });

        if (rows.size() > 1) {
            throw invalid();
        }

        return rows.stream().findFirst();
    }

    public Metadata metadata(long versionId) {
        bound(false);

        var rows = jdbc.query(
                METADATA,
                (rs, n) -> new Metadata(
                        number(
                                rs.getObject(
                                        "CREATED_BY_USER_ID"),
                                false),
                        rs.getObject(
                                "PUBLISHED_AT",
                                OffsetDateTime.class),
                        number(
                                rs.getObject(
                                        "PUBLISHED_BY_USER_ID"),
                                false),
                        rs.getString("CONTENT_HASH")),
                versionId);

        if (rows.size() != 1) {
            throw invalid();
        }

        return rows.getFirst();
    }

    public void save(
            Pointer before,
            long versionId,
            long actor,
            OffsetDateTime now) {

        bound(true);

        if (versionId < 1
                || actor < 1
                || now == null
                || now.getOffset().getTotalSeconds() != 0
                || now.getNano() % 1000 != 0
                || (before != null
                    && (before.rowVersion() < 0
                        || before.rowVersion()
                            == Long.MAX_VALUE))) {

            throw invalid();
        }

        Integer count = jdbc.execute(
                before == null ? INSERT : UPDATE,
                (org.springframework.jdbc.core
                        .PreparedStatementCallback<Integer>) ps -> {

                    ps.setLong(1, versionId);
                    ps.setObject(2, now);
                    ps.setLong(3, actor);

                    if (before != null) {
                        ps.setLong(
                                4,
                                before.versionId());

                        ps.setLong(
                                5,
                                before.rowVersion());
                    }

                    try {
                        return ps.executeUpdate();

                    } catch (SQLException exception) {

                        if (before == null
                                && OracleUniqueConstraint.matches(
                                        exception,
                                        "PK_CFG_CURRENT")) {

                            throw new ActivationFailure(
                                    ActivationFailure.Reason
                                            .STALE_ACTIVE_CONFIGURATION);
                        }

                        throw exception;
                    }
                });

        if (count == null || count != 1) {
            throw new ActivationFailure(
                    ActivationFailure.Reason
                        .STALE_ACTIVE_CONFIGURATION);
        }
    }

    static long number(
            Object value,
            boolean zeroAllowed) {

        try {
            if (!(value instanceof Number)
                    || value instanceof Double
                    || value instanceof Float) {

                throw invalid();
            }

            long result =
                    new BigDecimal(
                            value.toString())
                            .longValueExact();

            if (result < (zeroAllowed ? 0 : 1)) {
                throw invalid();
            }

            return result;

        } catch (ArithmeticException
                 | NumberFormatException e) {

            throw invalid();
        }
    }

    public static DataRetrievalFailureException invalid() {
        return new DataRetrievalFailureException(
                "Invalid active configuration state");
    }
}