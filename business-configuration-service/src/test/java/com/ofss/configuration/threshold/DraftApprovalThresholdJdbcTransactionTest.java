package com.ofss.configuration.threshold;

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
class DraftApprovalThresholdJdbcTransactionTest {
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
    private static final String PATH = "/api/v1/configuration/versions/7/approval-thresholds/1";
    private static final String BODY = """
            {"minAmountInclusive":"10.01","maxAmountExclusive":null,
             "expectedRowVersion":"0"}
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
        when(connection.prepareStatement(DraftApprovalThresholdRepository.FIND_QUERY)).thenReturn(find);
        when(find.executeQuery()).thenReturn(before, after);
        if (update) thresholdRows(before, 99);
        thresholdRows(after, 99);
        when(connection.prepareStatement(eq(DraftApprovalThresholdRepository.INSERT_THRESHOLD), org.mockito.ArgumentMatchers.<String[]>any()))
                .thenReturn(child);
        when(connection.prepareStatement(DraftApprovalThresholdRepository.UPDATE_THRESHOLD)).thenReturn(child);
        when(child.executeUpdate()).thenReturn(1);
        when(child.getGeneratedKeys()).thenReturn(keys);
        var metadata = mock(ResultSetMetaData.class);
        when(keys.next()).thenReturn(true, false);
        when(keys.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("THRESHOLD_CONFIG_ID");
        when(keys.getObject(1)).thenReturn(BigDecimal.valueOf(99));
        when(connection.prepareStatement(argThat((String sql) ->
                sql != null && sql.contains("UPDATE "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS")))).thenReturn(increment);
        when(increment.executeUpdate()).thenReturn(1);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.setExceptionTranslator(new SQLExceptionSubclassTranslator());
        var service = new DraftApprovalThresholdService(new DraftRiskRuleRepository(jdbc, JsonMapper.builder().build()),
                new DraftApprovalThresholdRepository(jdbc));
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(new JdbcTransactionManager(dataSource));
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(advice);
        return MockMvcBuilders.standaloneSetup(new DraftApprovalThresholdController((DraftApprovalThresholdService) proxy.getProxy()))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private void thresholdRows(ResultSet rows, long id) throws Exception {
        when(rows.next()).thenReturn(true, false);
        when(rows.getLong("THRESHOLD_CONFIG_ID")).thenReturn(id);
        when(rows.getLong("VERSION_ID")).thenReturn(7L);
        when(rows.getBigDecimal("MIN_AMOUNT_INCLUSIVE")).thenReturn(new BigDecimal("10.01"));
        when(rows.getInt("APPROVAL_LEVEL")).thenReturn(1);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void commitsChildAndParentOnOneConnectionPreservingIdentity(boolean update) throws Exception {
        var mvc = mvc(update);
        mvc.perform(put(PATH).contentType("application/json").content(BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.threshold.thresholdConfigId").value("99"))
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
        if (update) { verify(child).setLong(3, 99); verify(child, never()).getGeneratedKeys(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exactMoneySurvivesHttpBindingReadbackAndResponse(boolean update) throws Exception {
        var mvc = mvc(update);
        var minimum = new java.util.concurrent.atomic.AtomicReference<BigDecimal>();
        var maximum = new java.util.concurrent.atomic.AtomicReference<BigDecimal>();
        doAnswer(invocation -> { minimum.set(invocation.getArgument(1)); return null; })
                .when(child).setBigDecimal(eq(update ? 1 : 3), org.mockito.ArgumentMatchers.any(BigDecimal.class));
        doAnswer(invocation -> { maximum.set(invocation.getArgument(1)); return null; })
                .when(child).setBigDecimal(eq(update ? 2 : 4), org.mockito.ArgumentMatchers.any(BigDecimal.class));
        when(after.getBigDecimal("MIN_AMOUNT_INCLUSIVE")).thenAnswer(invocation -> minimum.get());
        when(after.getBigDecimal("MAX_AMOUNT_EXCLUSIVE")).thenAnswer(invocation -> maximum.get());
        String body = """
                {"minAmountInclusive":"9999999999999.98","maxAmountExclusive":"9999999999999.99",
                 "expectedRowVersion":"0"}
                """;
        mvc.perform(put(PATH).contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.threshold.minAmountInclusive").value("9999999999999.98"))
                .andExpect(jsonPath("$.threshold.maxAmountExclusive").value("9999999999999.99"));
        org.assertj.core.api.Assertions.assertThat(minimum.get()).isEqualTo(new BigDecimal("9999999999999.98"));
        org.assertj.core.api.Assertions.assertThat(maximum.get()).isEqualTo(new BigDecimal("9999999999999.99"));
        verify(child, never()).setDouble(anyInt(), anyDouble());
        verify(after, never()).getDouble(anyString());
        verify(connection).commit();
        verify(connection, never()).rollback();
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
            case "READBACK_MAPPING" -> when(after.getBigDecimal("MAX_AMOUNT_EXCLUSIVE")).thenThrow(new SQLException("private Oracle detail", "99999", 17004));
            case "READBACK_MISSING" -> when(after.next()).thenReturn(false);
            case "IDENTITY_CHANGED" -> when(after.getLong("THRESHOLD_CONFIG_ID")).thenReturn(100L);
        }
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().is(scenario.equals("PARENT_ZERO") ? 409 : 503))
                .andExpect(jsonPath("$.threshold").doesNotExist())
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
                "ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UK_CFG_THRESHOLD_LEVEL) violated", "23000", 1));
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().is(scenario.equals("READBACK") ? 503 : 409))
                .andExpect(jsonPath("$.threshold").doesNotExist());
        verify(child).setLong(3, 99);
        verify(child, never()).getGeneratedKeys();
        verify(connection).rollback();
        verify(connection, never()).commit();
        if (scenario.equals("READBACK")) verify(increment).executeUpdate();
    }

    @ParameterizedTest @ValueSource(strings = {""SUPPLIER_PAYMENT_APP"UK_CFG_THRESHOLD_LEVEL", ""SUPPLIER_PAYMENT_APP"PK_CFG_THRESHOLDS",
            ""SUPPLIER_PAYMENT_APP"UNKNOWN", "OTHER.UK_CFG_THRESHOLD_LEVEL"})
    void onlyNamedOwnedThresholdLevelConstraintIsAConflict(String identity) throws Exception {
        var mvc = mvc(false);
        boolean known = identity.equals(""SUPPLIER_PAYMENT_APP"UK_CFG_THRESHOLD_LEVEL");
        when(child.executeUpdate()).thenThrow(new SQLIntegrityConstraintViolationException(
                "ORA-00001: unique constraint (" + identity + ") violated", "23000", 1));
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().is(known ? 409 : 503))
                .andExpect(jsonPath("$.code").value(known ? "THRESHOLD_LEVEL_CONFLICT" : "CONFIGURATION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString(identity))));
        verify(increment, never()).executeUpdate();
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @ParameterizedTest @ValueSource(strings = {"KEY", "READBACK"})
    void constraintDiagnosticOutsideChildWriteIsNotMisclassified(String phase) throws Exception {
        var mvc = mvc(false);
        var error = new SQLIntegrityConstraintViolationException(
                "ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UK_CFG_THRESHOLD_LEVEL) violated", "23000", 1);
        if (phase.equals("KEY")) when(child.getGeneratedKeys()).thenThrow(error);
        else when(find.executeQuery()).thenReturn(before).thenThrow(error);
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
