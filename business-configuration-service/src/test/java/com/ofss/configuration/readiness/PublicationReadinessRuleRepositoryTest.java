package com.ofss.configuration.readiness;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;
import com.ofss.configuration.rule.RiskRule;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicationReadinessRuleRepositoryTest {
    @Test @SuppressWarnings("unchecked")
    void queryBindsVersionAndKeepsParameterNumbersExactWithoutInterpretingThem() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq(PublicationReadinessRuleRepository.QUERY), any(RowMapper.class), eq(7L))).thenReturn(List.of());
        new PublicationReadinessRuleRepository(jdbc, JsonMapper.builder().build()).list(7);
        var mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(eq(PublicationReadinessRuleRepository.QUERY), mapper.capture(), eq(7L));
        assertThat(PublicationReadinessRuleRepository.QUERY).contains("WHERE VERSION_ID = ?", "ORDER BY EVALUATION_ORDER ASC, RULE_CODE ASC")
                .doesNotContain("FOR UPDATE", "INSERT", "UPDATE ");
        var row = mock(ResultSet.class);
        when(row.getString("PARAMETERS_JSON")).thenReturn("""
                {"decimal":1.0000000000000001,"large":9007199254740993,"scientific":1e-20}
                """);
        var rule = (RiskRule) mapper.getValue().mapRow(row, 0);
        assertThat(rule.parameters().get("decimal")).isEqualTo(new BigDecimal("1.0000000000000001"));
        assertThat(rule.parameters().get("large")).isEqualTo(new BigInteger("9007199254740993"));
        assertThat((BigDecimal) rule.parameters().get("scientific")).isEqualByComparingTo(new BigDecimal("1e-20"));
    }
}
