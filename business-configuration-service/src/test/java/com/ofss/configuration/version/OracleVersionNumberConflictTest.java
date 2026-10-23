package com.ofss.configuration.version;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;

class OracleVersionNumberConflictTest {
    private static SQLException target() {
        return new SQLException("ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UK_CFG_VERSION_NUMBER) violated", "23000", 1);
    }

    @Test
    void followsJdbcNextExceptionAndNestedCause() {
        SQLException wrapper = new SQLException("wrapper", "99999", 0);
        wrapper.setNextException(target());
        assertThat(OracleVersionNumberConflict.matches(new DuplicateKeyException("wrapper", wrapper))).isTrue();
        assertThat(OracleVersionNumberConflict.matches(new RuntimeException(new RuntimeException(target())))).isTrue();
    }

    @Test
    void ambiguousConstraintChainFailsClosed() {
        SQLException sql = target();
        sql.setNextException(new SQLException("ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"PK_CFG_VERSIONS) violated", "23000", 1));
        assertThat(OracleVersionNumberConflict.matches(sql)).isFalse();
    }

    @Test
    void springExceptionTextWithoutUnderlyingOracleErrorIsInsufficient() {
        assertThat(OracleVersionNumberConflict.matches(new DuplicateKeyException(target().getMessage()))).isFalse();
    }

    @Test
    void cyclicCausesTerminateAndUnrecognizedIdentityFailsClosed() {
        RuntimeException first = new RuntimeException();
        RuntimeException second = new RuntimeException(first);
        first.initCause(second);
        assertThat(OracleVersionNumberConflict.matches(first)).isFalse();
        assertThat(OracleVersionNumberConflict.matches(new SQLException(
                "ORA-00001: unique constraint (\"D2_CONFIGURATION\".\"uk_cfg_version_number\") violated", "23000", 1))).isFalse();
    }
}
