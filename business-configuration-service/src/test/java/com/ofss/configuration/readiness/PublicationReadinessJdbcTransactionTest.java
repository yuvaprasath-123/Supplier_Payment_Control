package com.ofss.configuration.readiness;

import com.ofss.configuration.band.DraftRiskBandRepository;
import com.ofss.configuration.error.ApiExceptionHandler;
import com.ofss.configuration.rule.DraftRiskRuleRepository;
import com.ofss.configuration.sla.DraftApprovalSlaRepository;
import com.ofss.configuration.threshold.DraftApprovalThresholdRepository;
import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.json.JsonMapper;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JDBC/transaction/controller flow over mocked connections, NOT Oracle.
 * Standalone HTTP bypasses JWT; separate API tests use the real security filters. */
class PublicationReadinessJdbcTransactionTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement parent = mock(PreparedStatement.class);
    private final PreparedStatement rules = mock(PreparedStatement.class);
    private final PreparedStatement bands = mock(PreparedStatement.class);
    private final PreparedStatement thresholds = mock(PreparedStatement.class);
    private final PreparedStatement slas = mock(PreparedStatement.class);
    private final ResultSet parentRows = mock(ResultSet.class);
    private ResultSet ruleResult;
    private static final String PATH = "/api/v1/configuration/versions/7/publication-readiness";
    private MockMvc mvc() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        var statements = Map.of("CONFIGURATION_VERSIONS", parent, "RISK_RULE_CONFIGS", rules,
                "RISK_BAND_CONFIGS", bands, "APPROVAL_THRESHOLD_CONFIGS", thresholds, "SLA_CONFIGS", slas);
        for (var entry : statements.entrySet()) {
            when(connection.prepareStatement(argThat((String sql) -> sql != null && sql.contains("FROM "SUPPLIER_PAYMENT_APP"" + entry.getKey()))))
                    .thenReturn(entry.getValue());
        }
        populate(parentRows, List.of(row("VERSION_ID", 7L, "VERSION_NUMBER", 1, "VERSION_LABEL", "Fixture",
                "PUBLICATION_STATE", "DRAFT", "IS_DEMONSTRATION", 1, "SCORE_POLICY_CODE", "SUM", "MAX_FINAL_SCORE", null,
                "CREATED_AT", OffsetDateTime.parse("2026-01-01T00:00:00Z"), "PUBLISHED_AT", null, "ROW_VERSION", Long.MAX_VALUE)));
        when(parent.executeQuery()).thenReturn(parentRows);
        var ruleRows = new ArrayList<Map<String, Object>>();
        for (var rule : ReadinessFixtures.rules()) ruleRows.add(row("RULE_CONFIG_ID", rule.ruleConfigId(), "VERSION_ID", 7L,
                "RULE_CODE", rule.ruleCode(), "DISPLAY_NAME", rule.displayName(), "ENABLED", 1, "WEIGHT", 1,
                "PARAMETER_SCHEMA_VERSION", rule.parameterSchemaVersion(), "PARAMETERS_JSON", JsonMapper.builder().build().writeValueAsString(rule.parameters()), "EVALUATION_ORDER", rule.evaluationOrder()));
        ruleResult = rows(ruleRows);
        doReturn(ruleResult).when(rules).executeQuery();
        var bandRows = new ArrayList<Map<String, Object>>();
        for (var band : ReadinessFixtures.bands()) bandRows.add(row("BAND_CONFIG_ID", band.bandConfigId(), "VERSION_ID", 7L,
                "BAND_CODE", band.bandCode(), "MIN_SCORE_INCLUSIVE", band.minScoreInclusive(),
                "MAX_SCORE_INCLUSIVE", band.maxScoreInclusive(), "APPROVAL_LEVEL", band.approvalLevel()));
        doReturn(rows(bandRows)).when(bands).executeQuery();
        var thresholdRows = new ArrayList<Map<String, Object>>();
        for (var threshold : ReadinessFixtures.thresholds()) thresholdRows.add(row("THRESHOLD_CONFIG_ID", threshold.thresholdConfigId(),
                "VERSION_ID", 7L, "APPROVAL_LEVEL", threshold.approvalLevel(), "MIN_AMOUNT_INCLUSIVE", threshold.minAmountInclusive(),
                "MAX_AMOUNT_EXCLUSIVE", threshold.maxAmountExclusive()));
        doReturn(rows(thresholdRows)).when(thresholds).executeQuery();
        var slaRows = new ArrayList<Map<String, Object>>();
        for (var sla : ReadinessFixtures.slas()) slaRows.add(row("SLA_CONFIG_ID", sla.slaConfigId(), "VERSION_ID", 7L,
                "APPROVAL_LEVEL", sla.approvalLevel(), "DURATION_SECONDS", sla.durationSeconds(), "START_EVENT_CODE", sla.startEventCode(),
                "ESCALATION_POLICY_CODE", sla.escalationPolicyCode(), "TERMINAL_LEVEL_POLICY_CODE", sla.terminalLevelPolicyCode()));
        doReturn(rows(slaRows)).when(slas).executeQuery();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.setExceptionTranslator(new SQLExceptionSubclassTranslator());
        var service = new PublicationReadinessService(new DraftRiskRuleRepository(jdbc, JsonMapper.builder().build()),
                new PublicationReadinessRuleRepository(jdbc, JsonMapper.builder().build()),
                new DraftRiskBandRepository(jdbc), new DraftApprovalThresholdRepository(jdbc), new DraftApprovalSlaRepository(jdbc),
                new PublicationReadinessValidator());
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new JdbcTransactionManager(dataSource));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(service); proxy.addAdvice(interceptor);
        return MockMvcBuilders.standaloneSetup(new PublicationReadinessController((PublicationReadinessService) proxy.getProxy()))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }
    private Map<String, Object> row(Object... values) {
        var row = new HashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) row.put((String) values[i], values[i + 1]);
        return row;
    }
    private ResultSet rows(List<Map<String, Object>> values) throws Exception {
        var result = mock(ResultSet.class); populate(result, values); return result;
    }
    private void populate(ResultSet result, List<Map<String, Object>> values) throws Exception {
        var index = new AtomicInteger(-1);
        var wasNull = new AtomicBoolean();
        when(result.next()).thenAnswer(invocation -> index.incrementAndGet() < values.size());
        when(result.getInt(anyString())).thenAnswer(invocation -> {
            Object value = values.get(index.get()).get(invocation.getArgument(0)); wasNull.set(value == null);
            return value == null ? 0 : ((Number) value).intValue();
        });
        when(result.getLong(anyString())).thenAnswer(invocation -> {
            Object value = values.get(index.get()).get(invocation.getArgument(0)); wasNull.set(value == null);
            return value == null ? 0L : ((Number) value).longValue();
        });
        when(result.wasNull()).thenAnswer(invocation -> wasNull.get());
        when(result.getString(anyString())).thenAnswer(invocation -> values.get(index.get()).get(invocation.getArgument(0)));
        when(result.getBigDecimal(anyString())).thenAnswer(invocation -> (BigDecimal) values.get(index.get()).get(invocation.getArgument(0)));
        when(result.getObject(anyString(), eq(OffsetDateTime.class))).thenAnswer(invocation -> values.get(index.get()).get(invocation.getArgument(0)));
    }
    private void noWrites() throws Exception {
        for (var statement : List.of(parent, rules, bands, thresholds, slas)) verify(statement, never()).executeUpdate();
        verify(connection, never()).prepareStatement(argThat((String sql) -> sql != null && sql.contains("FOR UPDATE")));
        verify(connection, never()).prepareStatement(anyString(), org.mockito.ArgumentMatchers.<String[]>any());
        verify(dataSource, times(1)).getConnection();
    }
    @Test void readsEverySectionOnOneSerializableReadOnlyConnectionAndRestoresIt() throws Exception {
        var mvc = mvc();
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(jsonPath("$.structurallyValid").value(true))
                .andExpect(jsonPath("$.ready").value(true)).andExpect(jsonPath("$.rowVersion").value(Long.toString(Long.MAX_VALUE)));
        var order = inOrder(connection, parent, rules, bands, thresholds, slas);
        order.verify(connection).setReadOnly(true);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        order.verify(parent).executeQuery(); order.verify(rules).executeQuery(); order.verify(bands).executeQuery();
        order.verify(thresholds).executeQuery(); order.verify(slas).executeQuery(); order.verify(connection).commit();
        verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        verify(connection).setReadOnly(false);
        verify(connection, never()).rollback();
        for (var statement : List.of(parent, rules, bands, thresholds, slas)) verify(statement).setObject(1, 7L);
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings = {"PARENT", "RULES", "BANDS", "THRESHOLDS", "SLAS"})
    void sectionFailureRollsBackWithoutPartialResponse(String stage) throws Exception {
        var mvc = mvc();
        var statement = switch (stage) { case "PARENT" -> parent; case "RULES" -> rules; case "BANDS" -> bands; case "THRESHOLDS" -> thresholds; default -> slas; };
        when(statement.executeQuery()).thenThrow(new SQLRecoverableException("private Oracle detail", "08006", 17002));
        mvc.perform(get(PATH)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.ready").doesNotExist()).andExpect(content().string(not(containsString("private Oracle detail"))));
        verify(connection).rollback(); verify(connection, never()).commit();
        verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        noWrites();
    }
    @Test void missingParentStopsBeforeChildQueriesAndRemainsReadOnly() throws Exception {
        var mvc = mvc(); when(parentRows.next()).thenReturn(false);
        mvc.perform(get(PATH)).andExpect(status().isNotFound());
        for (var statement : List.of(rules, bands, thresholds, slas)) verify(statement, never()).executeQuery();
        verify(connection).rollback(); verify(connection, never()).commit(); noWrites();
    }
    @Test void isolationSetupFailureIsSanitizedRatherThanFallingBackToMixedReads() throws Exception {
        var mvc = mvc();
        doThrow(new SQLException("private isolation detail", "99999", 17004)).when(connection).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        mvc.perform(get(PATH)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("private isolation detail"))));
        verify(parent, never()).executeQuery(); verify(connection, never()).commit(); noWrites();
    }
    @ParameterizedTest @ValueSource(strings = {"[]", "null", "1", "true", "\"text\"", "broken JSON", "{} {}"})
    void wrongStoredParameterShapeIsConfigurationErrorRatherThanDatabaseOutage(String json) throws Exception {
        var mvc = mvc();
        doReturn(json).when(ruleResult).getString("PARAMETERS_JSON");
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(jsonPath("$.structurallyValid").value(false))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.issues[?(@.code=='INVALID_RULE_PARAMETER_OBJECT')]", hasSize(7)));
        verify(connection).commit(); verify(connection, never()).rollback(); noWrites();
    }
}
