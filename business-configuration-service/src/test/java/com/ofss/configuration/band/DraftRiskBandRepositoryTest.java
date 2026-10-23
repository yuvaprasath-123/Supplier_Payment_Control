package com.ofss.configuration.band;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DraftRiskBandRepositoryTest {
    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    @SuppressWarnings("unchecked")
    void bindsScoresAndTypedNullAndRetainsUpdateIdentity(boolean update, boolean capped) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(DraftRiskBandRepository.UPDATE_BAND)).thenReturn(statement);
        when(connection.prepareStatement(eq(DraftRiskBandRepository.INSERT_BAND), org.mockito.ArgumentMatchers.<String[]>any())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        ResultSet keys = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(statement.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true, false);
        when(keys.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("BAND_CONFIG_ID");
        when(keys.getObject(1)).thenReturn(new BigDecimal(Long.toString(Long.MAX_VALUE)));
        when(jdbc.execute(any(PreparedStatementCreator.class), any(PreparedStatementCallback.class))).thenAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            PreparedStatementCallback<Integer> callback = invocation.getArgument(1);
            return callback.doInPreparedStatement(creator.createPreparedStatement(connection));
        });
        var request = new DraftRiskBandRequest(0, capped ? Integer.MAX_VALUE : null, 3, 0);
        var existing = update ? new RiskBand(Long.MAX_VALUE, 7, "LOW", 10, null, 1) : null;
        assertThat(new DraftRiskBandRepository(jdbc).save(7, "LOW", request, existing)).isEqualTo(Long.MAX_VALUE);
        verify(statement).setInt(update ? 1 : 3, 0);
        if (capped) verify(statement).setInt(update ? 2 : 4, Integer.MAX_VALUE);
        else verify(statement).setNull(update ? 2 : 4, Types.INTEGER);
        verify(statement).setInt(update ? 3 : 5, 3);
        if (update) {
            verify(statement).setLong(4, Long.MAX_VALUE);
            verify(statement).setLong(5, 7);
            verify(statement).setString(6, "LOW");
            verify(statement, never()).getGeneratedKeys();
            assertThat(DraftRiskBandRepository.UPDATE_BAND).doesNotContain("SET BAND_CONFIG_ID", "DELETE");
        } else {
            verify(connection).prepareStatement(DraftRiskBandRepository.INSERT_BAND, new String[]{"BAND_CONFIG_ID"});
            assertThat(DraftRiskBandRepository.INSERT_BAND).doesNotContain("BAND_CONFIG_ID", "CURRENT_CONFIGURATION");
        }
    }

    @Test @SuppressWarnings("unchecked")
    void listQueryHasDeterministicOrderAndNullableUpperScoreMapping() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq(DraftRiskBandRepository.LIST_QUERY), any(RowMapper.class), eq(7L))).thenReturn(List.of());
        assertThat(new DraftRiskBandRepository(jdbc).list(7)).isEmpty();
        var captured = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(eq(DraftRiskBandRepository.LIST_QUERY), captured.capture(), eq(7L));
        assertThat(DraftRiskBandRepository.LIST_QUERY).contains("ORDER BY MIN_SCORE_INCLUSIVE ASC, BAND_CODE ASC");
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("BAND_CONFIG_ID")).thenReturn(Long.MAX_VALUE);
        when(row.getLong("VERSION_ID")).thenReturn(7L);
        when(row.getString("BAND_CODE")).thenReturn("LOW");
        when(row.getInt("MIN_SCORE_INCLUSIVE")).thenReturn(0);
        when(row.getInt("APPROVAL_LEVEL")).thenReturn(3);
        when(row.wasNull()).thenReturn(true);
        RiskBand result = (RiskBand) captured.getValue().mapRow(row, 0);
        assertThat(result.maxScoreInclusive()).isNull();
        assertThat(result.bandConfigId()).isEqualTo(Long.MAX_VALUE);
        when(row.getInt("MAX_SCORE_INCLUSIVE")).thenReturn(Integer.MAX_VALUE);
        when(row.wasNull()).thenReturn(false);
        assertThat(((RiskBand) captured.getValue().mapRow(row, 0)).maxScoreInclusive()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test @SuppressWarnings("unchecked")
    void duplicateFindResultsAreDatabaseFailureRatherThanSilentOverwrite() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var band = new RiskBand(1, 7, "LOW", 0, null, 1);
        when(jdbc.query(eq(DraftRiskBandRepository.FIND_QUERY), any(RowMapper.class), eq(7L), eq("LOW"))).thenReturn(List.of(band, band));
        assertThatThrownBy(() -> new DraftRiskBandRepository(jdbc).find(7, "LOW")).isInstanceOf(DataRetrievalFailureException.class);
    }
}
