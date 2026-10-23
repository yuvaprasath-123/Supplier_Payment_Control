package com.ofss.configuration.rule;

import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DraftRiskRuleRepositoryTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void savesSafelyBoundJsonAndPreservesIdentityOnUpdate(boolean update) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(DraftRiskRuleRepository.UPDATE_RULE)).thenReturn(statement);
        when(connection.prepareStatement(eq(DraftRiskRuleRepository.INSERT_RULE), org.mockito.ArgumentMatchers.<String[]>any()))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        ResultSet keys = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(statement.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true, false);
        when(keys.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("RULE_CONFIG_ID");
        when(keys.getObject(1)).thenReturn(BigDecimal.valueOf(99));
        when(jdbc.execute(any(PreparedStatementCreator.class), any(PreparedStatementCallback.class)))
                .thenAnswer(invocation -> {
                    PreparedStatementCreator creator = invocation.getArgument(0);
                    PreparedStatementCallback<Long> callback = invocation.getArgument(1);
                    return callback.doInPreparedStatement(creator.createPreparedStatement(connection));
                });
        Map<String, Object> parameters = Map.of("review", "quotes \" and ' ; SQL-like text", "nested", List.of(Map.of("x", true)));
        var request = new DraftRiskRuleRequest("Name with ' quote", false, 0, "v1", parameters, 99999, 0);
        var existing = update ? new RiskRule(99, 7, "STRUCTURING", "Old", true, 1, "old", Map.of(), 1) : null;
        assertThat(new DraftRiskRuleRepository(jdbc, mapper).saveRule(7, "STRUCTURING", request, existing)).isEqualTo(99);
        var stream = ArgumentCaptor.forClass(Reader.class);
        var length = ArgumentCaptor.forClass(Integer.class);
        verify(statement).setCharacterStream(eq(update ? 5 : 7), stream.capture(), length.capture());
        String json = new java.io.BufferedReader(stream.getValue()).lines().collect(java.util.stream.Collectors.joining("\n"));
        assertThat(mapper.readValue(json, Map.class)).isEqualTo(parameters);
        assertThat(length.getValue()).isEqualTo(json.length());
        verify(statement).setString(update ? 1 : 3, "Name with ' quote");
        verify(statement).setInt(update ? 2 : 4, 0);
        verify(statement).setInt(update ? 3 : 5, 0);
        verify(statement).setInt(update ? 6 : 8, 99999);
        if (update) {
            verify(statement).setLong(7, 99);
            verify(statement).setLong(8, 7);
            verify(statement).setString(9, "STRUCTURING");
            verify(statement, never()).getGeneratedKeys();
            assertThat(DraftRiskRuleRepository.UPDATE_RULE).doesNotContain("SET RULE_CONFIG_ID", "DELETE");
        } else {
            verify(connection).prepareStatement(DraftRiskRuleRepository.INSERT_RULE, new String[]{"RULE_CONFIG_ID"});
            assertThat(DraftRiskRuleRepository.INSERT_RULE).doesNotContain("RULE_CONFIG_ID", "CURRENT_CONFIGURATION");
        }
    }

    @Test @SuppressWarnings("unchecked")
    void listHasDeterministicSqlOrderAndDecodesParameters() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq(DraftRiskRuleRepository.LIST_RULES_QUERY), any(RowMapper.class), eq(7L))).thenReturn(List.of());
        assertThat(new DraftRiskRuleRepository(jdbc, mapper).listRules(7)).isEmpty();
        var captured = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(eq(DraftRiskRuleRepository.LIST_RULES_QUERY), captured.capture(), eq(7L));
        assertThat(DraftRiskRuleRepository.LIST_RULES_QUERY).contains("ORDER BY EVALUATION_ORDER ASC, RULE_CODE ASC");
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("RULE_CONFIG_ID")).thenReturn(Long.MAX_VALUE);
        when(row.getLong("VERSION_ID")).thenReturn(7L);
        when(row.getString("RULE_CODE")).thenReturn("STRUCTURING");
        when(row.getString("PARAMETERS_JSON")).thenReturn("{\"draft\":true,\"nested\":{\"value\":null}}");
        RiskRule mapped = (RiskRule) captured.getValue().mapRow(row, 0);
        assertThat(mapped.parameters()).containsEntry("draft", true);
        assertThat(mapped.ruleConfigId()).isEqualTo(Long.MAX_VALUE);
        when(row.getString("PARAMETERS_JSON")).thenReturn("[]");
        assertThatThrownBy(() -> captured.getValue().mapRow(row, 0)).isInstanceOf(org.springframework.dao.DataRetrievalFailureException.class);
    }

    @Test @SuppressWarnings("unchecked")
    void versionReadLocksParentAndHandlesNullableScore() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq(DraftRiskRuleRepository.LOCK_VERSION_QUERY), any(RowMapper.class), eq(7L))).thenReturn(List.of());
        new DraftRiskRuleRepository(jdbc, mapper).findVersion(7, true);
        var captured = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(eq(DraftRiskRuleRepository.LOCK_VERSION_QUERY), captured.capture(), eq(7L));
        assertThat(DraftRiskRuleRepository.LOCK_VERSION_QUERY).endsWith("FOR UPDATE");
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("VERSION_ID")).thenReturn(7L);
        when(row.getLong("ROW_VERSION")).thenReturn(Long.MAX_VALUE);
        when(row.wasNull()).thenReturn(true);
        when(row.getString("SCORE_POLICY_CODE")).thenReturn("SUM");
        when(row.getObject("CREATED_AT", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
        var result = (ConfigurationVersionDetails) captured.getValue().mapRow(row, 0);
        assertThat(result.maxFinalScore()).isNull();
        assertThat(result.rowVersion()).isEqualTo(Long.MAX_VALUE);
        when(row.getInt("MAX_FINAL_SCORE")).thenReturn(100);
        when(row.wasNull()).thenReturn(false);
        assertThat(((ConfigurationVersionDetails) captured.getValue().mapRow(row, 0)).maxFinalScore()).isEqualTo(100);
    }

    @Test void parentIncrementIsConditionalAndNeverSilentlySucceeds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        assertThatThrownBy(() -> new DraftRiskRuleRepository(jdbc, mapper).incrementParent(7, 3))
                .isInstanceOf(DraftRuleConflictException.class);
        verify(jdbc).update(DraftRiskRuleRepository.INCREMENT_PARENT, 7L, 3L);
        assertThat(DraftRiskRuleRepository.INCREMENT_PARENT)
                .contains("ROW_VERSION = ROW_VERSION + 1", "ROW_VERSION = ?", "PUBLICATION_STATE = 'DRAFT'");
    }
}
