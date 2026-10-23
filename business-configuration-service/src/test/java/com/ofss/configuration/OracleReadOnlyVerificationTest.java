package com.ofss.configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Optional actual Oracle read test. Never substitutes an embedded database. */
@EnabledIfEnvironmentVariable(named = "CONFIG_VERIFY_ORACLE", matches = "true")
class OracleReadOnlyVerificationTest {
    @Test
    void verifiesConnectionIdentityMetadataAndVersionReadWithoutWrites() throws Exception {
        String password = System.getenv("CONFIG_DB_PASSWORD");
        assertThat(password != null && !password.isBlank())
                .as("Supply CONFIG_DB_PASSWORD securely for opt-in Oracle verification").isTrue();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:oracle:thin:@//localhost:1521/FREEPDB1", "D2_CONFIGURATION", password)) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement(); var row = statement.executeQuery(
                    "SELECT SYS_CONTEXT('USERENV','CON_NAME'), SYS_CONTEXT('USERENV','SESSION_USER') FROM DUAL")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("FREEPDB1");
                assertThat(row.getString(2)).isEqualTo("D2_CONFIGURATION");
            }
            Set<String> tables = new HashSet<>();
            try (var rows = connection.getMetaData().getTables(null, "D2_CONFIGURATION", "%", new String[]{"TABLE"})) {
                while (rows.next()) tables.add(rows.getString("TABLE_NAME"));
            }
            assertThat(tables).contains("CONFIGURATION_VERSIONS", "RISK_RULE_CONFIGS", "RISK_BAND_CONFIGS",
                    "APPROVAL_THRESHOLD_CONFIGS", "SLA_CONFIGS", "CURRENT_CONFIGURATION");
            Set<String> columns = new HashSet<>();
            try (var rows = connection.getMetaData().getColumns(null, "D2_CONFIGURATION", "CONFIGURATION_VERSIONS", "%")) {
                while (rows.next()) columns.add(rows.getString("COLUMN_NAME"));
            }
            assertThat(columns).contains("VERSION_ID", "VERSION_NUMBER", "VERSION_LABEL", "PUBLICATION_STATE",
                    "IS_DEMONSTRATION", "CREATED_AT", "PUBLISHED_AT");
            Set<String> indexes = new HashSet<>();
            try (var rows = connection.getMetaData().getIndexInfo(null, "D2_CONFIGURATION", "CURRENT_CONFIGURATION", false, true)) {
                while (rows.next()) indexes.add(rows.getString("INDEX_NAME"));
            }
            assertThat(indexes).contains("IX_CFG_CURRENT_VERSION");
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT VERSION_ID, CREATED_AT, PUBLISHED_AT FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS ORDER BY VERSION_NUMBER, VERSION_ID")) {
                while (rows.next()) {
                    assertThat(rows.getLong("VERSION_ID")).isPositive();
                    assertThat(rows.getObject("CREATED_AT", java.time.OffsetDateTime.class)).isNotNull();
                    rows.getObject("PUBLISHED_AT", java.time.OffsetDateTime.class);
                }
            }
        } catch (java.sql.SQLException exception) {
            // Do not expose driver diagnostics/connection details in reports.
            throw new AssertionError("Oracle read-only verification failed; JDBC integration remains unverified");
        }
    }
}
