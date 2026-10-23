package com.ofss.configuration.sla;

import com.ofss.configuration.error.ApiExceptionHandler;
import com.ofss.configuration.rule.DraftRiskRuleRepository;
import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring JDBC/transactions over mocked JDBC objects, NOT live Oracle.
 * Standalone HTTP bypasses JWT here; the API tests cover authentication. */
class DraftApprovalSlaJdbcTransactionTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement parent = mock(PreparedStatement.class);
    private final PreparedStatement find = mock(PreparedStatement.class);
    private final PreparedStatement child = mock(PreparedStatement.class);
    private final PreparedStatement increment = mock(PreparedStatement.class);
    private final ResultSet parentRows = mock(ResultSet.class);
    private final ResultSet before = mock(ResultSet.class);
    private final ResultSet after = mock(ResultSet.class);
    private final ResultSet keys = mock(ResultSet.class);
    private static final String PATH = "/api/v1/configuration/versions/7/approval-slas/1";
    private static final String BODY = """
            {"durationSeconds":1,"startEventCode":"DRAFT_START",
             "escalationPolicyCode":"DRAFT_ESCALATION","terminalLevelPolicyCode":null,"expectedRowVersion":"0"}
            """;

    private MockMvc mvc(boolean update) throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(argThat((String sql) ->
                sql != null && sql.contains("CONFIGURATION_VERSIONS") && sql.stripTrailing().endsWith("FOR UPDATE")))).thenReturn(parent);
        when(parent.executeQuery()).thenReturn(parentRows);
        when(parentRows.next()).thenReturn(true, false);
        when(parentRows.getLong("VERSION_ID")).thenReturn(7L);
        when(parentRows.getString("PUBLICATION_STATE")).thenReturn("DRAFT");
        when(parentRows.getString("SCORE_POLICY_CODE")).thenReturn("SUM");
        when(parentRows.getObject("CREATED_AT", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
        when(parentRows.wasNull()).thenReturn(true);
        when(connection.prepareStatement(DraftApprovalSlaRepository.FIND_QUERY)).thenReturn(find);
        when(find.executeQuery()).thenReturn(before, after);
        if (update) slaRows(before, 99);
        slaRows(after, 99);
        when(connection.prepareStatement(eq(DraftApprovalSlaRepository.INSERT_SLA), org.mockito.ArgumentMatchers.<String[]>any()))
                .thenReturn(child);
        when(connection.prepareStatement(DraftApprovalSlaRepository.UPDATE_SLA)).thenReturn(child);
        when(child.executeUpdate()).thenReturn(1);
        when(child.getGeneratedKeys()).thenReturn(keys);
        var metadata = mock(ResultSetMetaData.class);
        when(keys.next()).thenReturn(true, false);
        when(keys.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("SLA_CONFIG_ID");
        when(keys.getObject(1)).thenReturn(BigDecimal.valueOf(99));
        when(connection.prepareStatement(argThat((String sql) ->
                sql != null && sql.contains("UPDATE "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS")))).thenReturn(increment);
        when(increment.executeUpdate()).thenReturn(1);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.setExceptionTranslator(new SQLExceptionSubclassTranslator());
        var service = new DraftApprovalSlaService(new DraftRiskRuleRepository(jdbc, JsonMapper.builder().build()),
                new DraftApprovalSlaRepository(jdbc));
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(new JdbcTransactionManager(dataSource));
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(advice);
        return MockMvcBuilders.standaloneSetup(new DraftApprovalSlaController((DraftApprovalSlaService) proxy.getProxy()))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private void slaRows(ResultSet rows, long id) throws Exception {
        when(rows.next()).thenReturn(true, false);
        when(rows.getLong("SLA_CONFIG_ID")).thenReturn(id);
        when(rows.getLong("VERSION_ID")).thenReturn(7L);
        when(rows.getString("START_EVENT_CODE")).thenReturn("DRAFT_START");
        when(rows.getLong("DURATION_SECONDS")).thenReturn(1L);
        when(rows.getString("ESCALATION_POLICY_CODE")).thenReturn("DRAFT_ESCALATION");
        when(rows.getInt("APPROVAL_LEVEL")).thenReturn(1);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void commitsChildAndParentOnOneConnectionPreservingIdentity(boolean update) throws Exception {
        var mvc = mvc(update);
        mvc.perform(put(PATH).contentType("application/json").content(BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sla.slaConfigId").value("99"))
                .andExpect(jsonPath("$.sla.durationSeconds").value(1))
                .andExpect(jsonPath("$.sla.terminalLevelPolicyCode").value(nullValue()))
                .andExpect(jsonPath("$.rowVersion").value("1"));
        var order = inOrder(parent, find, child, increment, connection);
        order.verify(parent).executeQuery();
        order.verify(find).executeQuery();
        order.verify(child).executeUpdate();
        order.verify(increment).executeUpdate();
        order.verify(find).executeQuery();
        order.verify(connection).commit();
        verify(parent).setObject(1, 7L);
        verify(increment).setObject(1, 7L);
        verify(increment).setObject(2, 0L);
        verify(connection, never()).rollback();
        verify(dataSource, times(1)).getConnection();
        if (update) { verify(child).setLong(5, 99); verify(child, never()).getGeneratedKeys(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void httpBindingAndReadbackPreserveLongAndPolicyText(boolean update) throws Exception {
        var mvc = mvc(update);
        var duration = new java.util.concurrent.atomic.AtomicLong();
        var start = new java.util.concurrent.atomic.AtomicReference<String>();
        var escalation = new java.util.concurrent.atomic.AtomicReference<String>();
        var terminal = new java.util.concurrent.atomic.AtomicReference<String>();
        doAnswer(invocation -> { duration.set(invocation.getArgument(1)); return null; })
                .when(child).setLong(eq(update ? 1 : 3), anyLong());
        doAnswer(invocation -> { start.set(invocation.getArgument(1)); return null; })
                .when(child).setString(eq(update ? 2 : 4), anyString());
        doAnswer(invocation -> { escalation.set(invocation.getArgument(1)); return null; })
                .when(child).setString(eq(update ? 3 : 5), anyString());
        doAnswer(invocation -> { terminal.set(invocation.getArgument(1)); return null; })
                .when(child).setString(eq(update ? 4 : 6), anyString());
        when(after.getLong("DURATION_SECONDS")).thenAnswer(invocation -> duration.get());
        when(after.getString("START_EVENT_CODE")).thenAnswer(invocation -> start.get());
        when(after.getString("ESCALATION_POLICY_CODE")).thenAnswer(invocation -> escalation.get());
        when(after.getString("TERMINAL_LEVEL_POLICY_CODE")).thenAnswer(invocation -> terminal.get());
        String unicode = "\uD83D\uDE00".repeat(32);
        String body = "{\"durationSeconds\":999999999999,\"startEventCode\":\"" + unicode
                + "\",\"escalationPolicyCode\":\" Draft future policy \",\"terminalLevelPolicyCode\":\" Draft terminal \",\"expectedRowVersion\":\"0\"}";
        mvc.perform(put(PATH).contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sla.durationSeconds").value(999999999999L))
                .andExpect(jsonPath("$.sla.startEventCode").value(unicode))
                .andExpect(jsonPath("$.sla.escalationPolicyCode").value(" Draft future policy "))
                .andExpect(jsonPath("$.sla.terminalLevelPolicyCode").value(" Draft terminal "));
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(dataSource, times(1)).getConnection();
    }

    @ParameterizedTest @ValueSource(strings = {"MISSING", "STALE", "PUBLISHED"})
    void rejectsMissingOrProtectedParentBeforeChildChanges(String scenario) throws Exception {
        var mvc = mvc(false);
        if (scenario.equals("MISSING")) when(parentRows.next()).thenReturn(false);
        else if (scenario.equals("STALE")) when(parentRows.getLong("ROW_VERSION")).thenReturn(1L);
        else when(parentRows.getString("PUBLICATION_STATE")).thenReturn("PUBLISHED");
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().is(scenario.equals("MISSING") ? 404 : 409));
        verify(find, never()).executeQuery();
        verify(child, never()).executeUpdate();
        verify(increment, never()).executeUpdate();
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @ParameterizedTest @ValueSource(strings = {"MISSING_KEY", "NULL_KEY", "FRACTIONAL_KEY", "OVERFLOW_KEY", "ZERO_KEY",
            "CHILD_ZERO", "PARENT_SQL", "PARENT_ZERO", "READBACK_SQL", "READBACK_MAPPING", "READBACK_MISSING", "IDENTITY_CHANGED"})
    void failuresAfterInsertRollBackWithoutSuccessfulResponse(String scenario) throws Exception {
        var mvc = mvc(false);
        switch (scenario) {
            case "MISSING_KEY" -> when(keys.next()).thenReturn(false);
            case "NULL_KEY" -> when(keys.getObject(1)).thenReturn(null);
            case "FRACTIONAL_KEY" -> when(keys.getObject(1)).thenReturn(new BigDecimal("1.5"));
            case "OVERFLOW_KEY" -> when(keys.getObject(1)).thenReturn(new BigDecimal("9223372036854775808"));
            case "ZERO_KEY" -> when(keys.getObject(1)).thenReturn(BigDecimal.ZERO);
            case "CHILD_ZERO" -> when(child.executeUpdate()).thenReturn(0);
            case "PARENT_SQL" -> when(increment.executeUpdate()).thenThrow(new SQLRecoverableException("private Oracle detail", "08006", 17002));
            case "PARENT_ZERO" -> when(increment.executeUpdate()).thenReturn(0);
            case "READBACK_SQL" -> when(find.executeQuery()).thenReturn(before).thenThrow(new SQLException("private Oracle detail", "99999", 17004));
            case "READBACK_MAPPING" -> when(after.getString("TERMINAL_LEVEL_POLICY_CODE")).thenThrow(new SQLException("private Oracle detail", "99999", 17004));
            case "READBACK_MISSING" -> when(after.next()).thenReturn(false);
            case "IDENTITY_CHANGED" -> when(after.getLong("SLA_CONFIG_ID")).thenReturn(100L);
        }
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().is(scenario.equals("PARENT_ZERO") ? 409 : 503))
                .andExpect(jsonPath("$.sla").doesNotExist())
                .andExpect(content().string(not(containsString("private Oracle detail"))));
        verify(child).executeUpdate();
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(dataSource, times(1)).getConnection();
        if (scenario.startsWith("READBACK") || scenario.equals("IDENTITY_CHANGED")) verify(increment).executeUpdate();
    }

    @ParameterizedTest @ValueSource(strings = {"READBACK", "CONFLICT"})
    void updateFailureRollsBackPreservedIdentityAndParent(String scenario) throws Exception {
        var mvc = mvc(true);
        if (scenario.equals("READBACK")) when(after.next()).thenReturn(false);
        else when(child.executeUpdate()).thenThrow(new SQLIntegrityConstraintViolationException(
                "ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UK_CFG_SLA_LEVEL) violated", "23000", 1));
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().is(scenario.equals("READBACK") ? 503 : 409))
                .andExpect(jsonPath("$.sla").doesNotExist());
        verify(child).setLong(5, 99);
        verify(child, never()).getGeneratedKeys();
        verify(connection).rollback();
        verify(connection, never()).commit();
        if (scenario.equals("READBACK")) verify(increment).executeUpdate();
    }

    @ParameterizedTest @ValueSource(strings = {""SUPPLIER_PAYMENT_APP"UK_CFG_SLA_LEVEL", ""SUPPLIER_PAYMENT_APP"PK_CFG_SLAS",
            ""SUPPLIER_PAYMENT_APP"UNKNOWN", "OTHER.UK_CFG_SLA_LEVEL"})
    void onlyNamedOwnedSlaLevelConstraintIsAConflict(String identity) throws Exception {
        var mvc = mvc(false);
        boolean known = identity.equals(""SUPPLIER_PAYMENT_APP"UK_CFG_SLA_LEVEL");
        when(child.executeUpdate()).thenThrow(new SQLIntegrityConstraintViolationException(
                "ORA-00001: unique constraint (" + identity + ") violated", "23000", 1));
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().is(known ? 409 : 503))
                .andExpect(jsonPath("$.code").value(known ? "SLA_LEVEL_CONFLICT" : "CONFIGURATION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString(identity))));
        verify(increment, never()).executeUpdate();
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @ParameterizedTest @ValueSource(strings = {"KEY", "READBACK"})
    void constraintDiagnosticOutsideChildWriteIsNotMisclassified(String phase) throws Exception {
        var mvc = mvc(false);
        var error = new SQLIntegrityConstraintViolationException(
                "ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UK_CFG_SLA_LEVEL) violated", "23000", 1);
        if (phase.equals("KEY")) when(child.getGeneratedKeys()).thenThrow(error);
        else when(find.executeQuery()).thenReturn(before).thenThrow(error);
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
