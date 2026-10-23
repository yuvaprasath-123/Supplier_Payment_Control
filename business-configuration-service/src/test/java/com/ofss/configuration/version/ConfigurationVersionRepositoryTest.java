package com.ofss.configuration.version;

import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.math.BigDecimal;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementCallback;
import java.sql.ResultSetMetaData;
import org.springframework.dao.DataRetrievalFailureException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConfigurationVersionRepositoryTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void insertBindsOnlyDraftFieldsAndReadsBackNamedOracleIdentity(boolean capped) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-10-13T10:00:00.123456Z");
        var request = new CreateConfigurationVersionRequest("Draft", capped ? "Description" : null,
                capped, capped ? "CAP_AT_MAX" : "SUM", capped ? 100 : null);
        var expected = new ConfigurationVersion(Long.MAX_VALUE, 9, "Draft", "DRAFT", capped, createdAt, null);
        when(connection.prepareStatement(eq(ConfigurationVersionRepository.INSERT_DRAFT),
                org.mockito.ArgumentMatchers.<String[]>any())).thenReturn(statement);
        ResultSet generated = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(statement.executeUpdate()).thenReturn(1);
        when(statement.getGeneratedKeys()).thenReturn(generated);
        when(generated.next()).thenReturn(true, false);
        when(generated.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("VERSION_ID");
        when(generated.getObject(1)).thenReturn(new BigDecimal(Long.toString(Long.MAX_VALUE)));
        when(jdbc.execute(any(PreparedStatementCreator.class), any(PreparedStatementCallback.class))).thenAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            assertThat(creator.createPreparedStatement(connection)).isSameAs(statement);
            PreparedStatementCallback<Integer> callback = invocation.getArgument(1);
            return callback.doInPreparedStatement(statement);
        });
        when(jdbc.queryForObject(eq(ConfigurationVersionRepository.FIND_BY_ID_QUERY), any(RowMapper.class),
                eq(Long.MAX_VALUE))).thenReturn(expected);
        assertThat(new ConfigurationVersionRepository(jdbc).insertDraft(request, 9, 42, createdAt)).isEqualTo(expected);
        verify(connection).prepareStatement(ConfigurationVersionRepository.INSERT_DRAFT, new String[] {"VERSION_ID"});
        verify(statement).setInt(1, 9);
        verify(statement).setString(2, "Draft");
        verify(statement).setString(3, request.description());
        verify(statement).setInt(4, capped ? 1 : 0);
        verify(statement).setString(5, request.scorePolicyCode());
        if (capped) verify(statement).setInt(6, 100);
        else verify(statement).setNull(6, Types.INTEGER);
        verify(statement).setObject(7, createdAt, Types.TIMESTAMP_WITH_TIMEZONE);
        verify(statement).setLong(8, 42);
        verify(statement).executeUpdate();
        verify(statement).getGeneratedKeys();
        verifyNoMoreInteractions(statement);
        assertThat(ConfigurationVersionRepository.INSERT_DRAFT)
                .contains("VALUES (?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?)")
                .doesNotContain("VERSION_ID", "PUBLISHED_AT", "PUBLISHED_BY_USER_ID", "CONTENT_HASH", "CURRENT_CONFIGURATION");
        verify(jdbc).queryForObject(eq(ConfigurationVersionRepository.FIND_BY_ID_QUERY), any(RowMapper.class),
                eq(Long.MAX_VALUE));
    }

    @Test
    void missingIdentityFailsInsteadOfReportingSuccessfulCreation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.execute(any(PreparedStatementCreator.class), any(PreparedStatementCallback.class))).thenReturn(1);
        var request = new CreateConfigurationVersionRequest("Draft", null, false, "SUM", null);
        assertThatThrownBy(() -> new ConfigurationVersionRepository(jdbc).insertDraft(request, 1, 42,
                OffsetDateTime.now(java.time.ZoneOffset.UTC))).isInstanceOf(DataRetrievalFailureException.class);
    }

    @Test
    void allocationReadsMaximumAcrossAllStatesAndHandlesEmptyTable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(ConfigurationVersionRepository.LATEST_VERSION_QUERY, Long.class)).thenReturn(0L, 7L);
        var repository = new ConfigurationVersionRepository(jdbc);
        assertThat(repository.latestVersionNumber()).isZero();
        assertThat(repository.latestVersionNumber()).isEqualTo(7);
        assertThat(ConfigurationVersionRepository.LATEST_VERSION_QUERY)
                .contains("COALESCE(MAX(VERSION_NUMBER), 0)").doesNotContain("WHERE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryMapsOracleTimestampTypesAndUsesDeterministicOrder() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq(ConfigurationVersionRepository.LIST_QUERY), any(RowMapper.class)))
                .thenReturn(List.of());
        assertThat(new ConfigurationVersionRepository(jdbc).findAll()).isEmpty();
        var mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(eq(ConfigurationVersionRepository.LIST_QUERY), mapper.capture());
        assertThat(ConfigurationVersionRepository.LIST_QUERY)
                .contains("FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS")
                .contains("ORDER BY VERSION_NUMBER ASC, VERSION_ID ASC");
        ResultSet row = mock(ResultSet.class);
        OffsetDateTime created = OffsetDateTime.parse("2026-10-13T10:00:00+05:30");
        when(row.getLong("VERSION_ID")).thenReturn(Long.MAX_VALUE);
        when(row.getInt("VERSION_NUMBER")).thenReturn(1);
        when(row.getString("VERSION_LABEL")).thenReturn("Draft");
        when(row.getString("PUBLICATION_STATE")).thenReturn("DRAFT");
        when(row.getInt("IS_DEMONSTRATION")).thenReturn(1);
        when(row.getObject("CREATED_AT", OffsetDateTime.class)).thenReturn(created);
        ConfigurationVersion result = (ConfigurationVersion) mapper.getValue().mapRow(row, 0);
        assertThat(result.versionId()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.isDemonstration()).isTrue();
        assertThat(result.createdAt()).isEqualTo(created);
        assertThat(result.publishedAt()).isNull();
        verifyNoMoreInteractions(jdbc);
    }
}
