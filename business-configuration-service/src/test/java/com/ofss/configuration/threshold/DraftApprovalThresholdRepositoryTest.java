package com.ofss.configuration.threshold;

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

class DraftApprovalThresholdRepositoryTest {
    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    @SuppressWarnings("unchecked")
    void bindsExactDecimalsAndNumericNullPreservingIdentity(boolean update, boolean bounded) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(DraftApprovalThresholdRepository.UPDATE_THRESHOLD)).thenReturn(statement);
        when(connection.prepareStatement(eq(DraftApprovalThresholdRepository.INSERT_THRESHOLD), org.mockito.ArgumentMatchers.<String[]>any())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        ResultSet keys = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(statement.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true, false);
        when(keys.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("THRESHOLD_CONFIG_ID");
        when(keys.getObject(1)).thenReturn(new BigDecimal(Long.toString(Long.MAX_VALUE)));
        when(jdbc.execute(any(PreparedStatementCreator.class), any(PreparedStatementCallback.class))).thenAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            PreparedStatementCallback<Integer> callback = invocation.getArgument(1);
            return callback.doInPreparedStatement(creator.createPreparedStatement(connection));
        });
        var minimum = new BigDecimal("9999999999999.98");
        var maximum = bounded ? new BigDecimal("9999999999999.99") : null;
        var request = new DraftApprovalThresholdRequest(minimum, maximum, 0);
        var existing = update ? new ApprovalThreshold(Long.MAX_VALUE, 7, 3, BigDecimal.ZERO, null) : null;
        assertThat(new DraftApprovalThresholdRepository(jdbc).save(7, 3, request, existing)).isEqualTo(Long.MAX_VALUE);
        verify(statement).setBigDecimal(update ? 1 : 3, minimum);
        if (bounded) verify(statement).setBigDecimal(update ? 2 : 4, maximum);
        else verify(statement).setNull(update ? 2 : 4, Types.NUMERIC);
        verify(statement, never()).setDouble(anyInt(), anyDouble());
        if (update) {
            verify(statement).setLong(3, Long.MAX_VALUE);
            verify(statement).setLong(4, 7);
            verify(statement).setInt(5, 3);
            verify(statement, never()).getGeneratedKeys();
            assertThat(DraftApprovalThresholdRepository.UPDATE_THRESHOLD).doesNotContain("SET THRESHOLD_CONFIG_ID", "DELETE");
        } else {
            verify(statement).setLong(1, 7);
            verify(statement).setInt(2, 3);
            verify(connection).prepareStatement(DraftApprovalThresholdRepository.INSERT_THRESHOLD, new String[]{"THRESHOLD_CONFIG_ID"});
            assertThat(DraftApprovalThresholdRepository.INSERT_THRESHOLD).doesNotContain("THRESHOLD_CONFIG_ID", "CURRENT_CONFIGURATION");
        }
    }

    @Test @SuppressWarnings("unchecked")
    void deterministicListMapsExactMoneyAndNullWithoutDouble() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq(DraftApprovalThresholdRepository.LIST_QUERY), any(RowMapper.class), eq(7L))).thenReturn(List.of());
        assertThat(new DraftApprovalThresholdRepository(jdbc).list(7)).isEmpty();
        var captured = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(eq(DraftApprovalThresholdRepository.LIST_QUERY), captured.capture(), eq(7L));
        assertThat(DraftApprovalThresholdRepository.LIST_QUERY).contains("ORDER BY MIN_AMOUNT_INCLUSIVE ASC, APPROVAL_LEVEL ASC");
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("THRESHOLD_CONFIG_ID")).thenReturn(Long.MAX_VALUE);
        when(row.getLong("VERSION_ID")).thenReturn(7L);
        when(row.getInt("APPROVAL_LEVEL")).thenReturn(3);
        when(row.getBigDecimal("MIN_AMOUNT_INCLUSIVE")).thenReturn(new BigDecimal("9999999999999.98"));
        var result = (ApprovalThreshold) captured.getValue().mapRow(row, 0);
        assertThat(result.maxAmountExclusive()).isNull();
        assertThat(result.response().minAmountInclusive()).isEqualTo("9999999999999.98");
        assertThat(result.response().thresholdConfigId()).isEqualTo(Long.toString(Long.MAX_VALUE));
        when(row.getBigDecimal("MAX_AMOUNT_EXCLUSIVE")).thenReturn(new BigDecimal("9999999999999.99"));
        result = (ApprovalThreshold) captured.getValue().mapRow(row, 0);
        assertThat(result.response().maxAmountExclusive()).isEqualTo("9999999999999.99");
        verify(row, never()).getDouble(anyString());
    }

    @Test @SuppressWarnings("unchecked")
    void duplicateFindRowsAreNotSilentlyOverwritten() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var threshold = new ApprovalThreshold(1, 7, 1, BigDecimal.ZERO, null);
        when(jdbc.query(eq(DraftApprovalThresholdRepository.FIND_QUERY), any(RowMapper.class), eq(7L), eq(1))).thenReturn(List.of(threshold, threshold));
        assertThatThrownBy(() -> new DraftApprovalThresholdRepository(jdbc).find(7, 1)).isInstanceOf(DataRetrievalFailureException.class);
    }
}
