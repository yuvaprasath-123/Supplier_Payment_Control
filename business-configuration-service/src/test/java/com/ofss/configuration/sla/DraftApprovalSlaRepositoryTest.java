package com.ofss.configuration.sla;

import java.math.BigDecimal;
import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DraftApprovalSlaRepositoryTest {
    @ParameterizedTest @CsvSource({"false,false,1", "false,true,999999999999", "true,false,999999999999", "true,true,1"})
    @SuppressWarnings("unchecked")
    void bindsLongDurationUnicodeAndTypedNullablePolicyRetainingIdentity(boolean update, boolean terminal, long duration) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(DraftApprovalSlaRepository.UPDATE_SLA)).thenReturn(statement);
        when(connection.prepareStatement(eq(DraftApprovalSlaRepository.INSERT_SLA), org.mockito.ArgumentMatchers.<String[]>any())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        ResultSet keys = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(statement.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true, false);
        when(keys.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("SLA_CONFIG_ID");
        when(keys.getObject(1)).thenReturn(new BigDecimal(Long.toString(Long.MAX_VALUE)));
        when(jdbc.execute(any(PreparedStatementCreator.class), any(PreparedStatementCallback.class))).thenAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            PreparedStatementCallback<Integer> callback = invocation.getArgument(1);
            return callback.doInPreparedStatement(creator.createPreparedStatement(connection));
        });
        String start = " Pending draft ";
        String escalation = "\uD83D\uDE00".repeat(32);
        String ending = terminal ? " Draft terminal " : null;
        var request = new DraftApprovalSlaRequest(duration, start, escalation, ending, 0);
        var existing = update ? new ApprovalSla(Long.MAX_VALUE, 7, 3, 1, "OLD", "OLD", null) : null;
        assertThat(new DraftApprovalSlaRepository(jdbc).save(7, 3, request, existing)).isEqualTo(Long.MAX_VALUE);
        verify(statement).setLong(update ? 1 : 3, duration);
        verify(statement).setString(update ? 2 : 4, start);
        verify(statement).setString(update ? 3 : 5, escalation);
        if (terminal) verify(statement).setString(update ? 4 : 6, ending);
        else verify(statement).setNull(update ? 4 : 6, Types.VARCHAR);
        if (update) {
            verify(statement).setLong(5, Long.MAX_VALUE);
            verify(statement).setLong(6, 7);
            verify(statement).setInt(7, 3);
            verify(statement, never()).getGeneratedKeys();
            assertThat(DraftApprovalSlaRepository.UPDATE_SLA).doesNotContain("SET SLA_CONFIG_ID", "DELETE");
        } else {
            verify(statement).setLong(1, 7);
            verify(statement).setInt(2, 3);
            verify(connection).prepareStatement(DraftApprovalSlaRepository.INSERT_SLA, new String[]{"SLA_CONFIG_ID"});
            assertThat(DraftApprovalSlaRepository.INSERT_SLA).doesNotContain("SLA_CONFIG_ID", "CURRENT_CONFIGURATION");
        }
    }

    @Test @SuppressWarnings("unchecked")
    void deterministicListMapsLongAndNullablePolicyWithoutTruncation() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq(DraftApprovalSlaRepository.LIST_QUERY), any(RowMapper.class), eq(7L))).thenReturn(List.of());
        assertThat(new DraftApprovalSlaRepository(jdbc).list(7)).isEmpty();
        var captured = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(eq(DraftApprovalSlaRepository.LIST_QUERY), captured.capture(), eq(7L));
        assertThat(DraftApprovalSlaRepository.LIST_QUERY).contains("ORDER BY APPROVAL_LEVEL ASC");
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("SLA_CONFIG_ID")).thenReturn(Long.MAX_VALUE);
        when(row.getLong("VERSION_ID")).thenReturn(7L);
        when(row.getInt("APPROVAL_LEVEL")).thenReturn(3);
        when(row.getLong("DURATION_SECONDS")).thenReturn(999999999999L);
        when(row.getString("START_EVENT_CODE")).thenReturn(" Draft start ");
        when(row.getString("ESCALATION_POLICY_CODE")).thenReturn("Draft escalation");
        var result = (ApprovalSla) captured.getValue().mapRow(row, 0);
        assertThat(result.terminalLevelPolicyCode()).isNull();
        assertThat(result.durationSeconds()).isEqualTo(999999999999L);
        assertThat(result.response().slaConfigId()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(result.startEventCode()).isEqualTo(" Draft start ");
        when(row.getString("TERMINAL_LEVEL_POLICY_CODE")).thenReturn("Draft terminal");
        assertThat(((ApprovalSla) captured.getValue().mapRow(row, 0)).terminalLevelPolicyCode()).isEqualTo("Draft terminal");
    }

    @Test @SuppressWarnings("unchecked")
    void duplicateFindRowsFailWithoutOverwrite() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var sla = new ApprovalSla(1, 7, 1, 1, "DRAFT", "DRAFT", null);
        when(jdbc.query(eq(DraftApprovalSlaRepository.FIND_QUERY), any(RowMapper.class), eq(7L), eq(1))).thenReturn(List.of(sla, sla));
        assertThatThrownBy(() -> new DraftApprovalSlaRepository(jdbc).find(7, 1)).isInstanceOf(DataRetrievalFailureException.class);
    }
}
