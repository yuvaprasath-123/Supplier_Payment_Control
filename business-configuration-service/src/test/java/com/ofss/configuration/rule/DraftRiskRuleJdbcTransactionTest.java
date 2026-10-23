package com.ofss.configuration.rule;

import com.ofss.configuration.error.ApiExceptionHandler;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLRecoverableException;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import java.io.Reader;
import java.io.BufferedReader;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

/** Actual Spring JDBC and transaction flow over mocked JDBC objects, NOT Oracle.
 * Standalone MockMvc bypasses security here; API tests exercise actual JWT checks. */
class DraftRiskRuleJdbcTransactionTest {
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
    private static final String PATH = "/api/v1/configuration/versions/7/risk-rules/STRUCTURING";
    private static final String BODY = """
            {"displayName":"Under review", "enabled":false, "weight":0,
             "parameterSchemaVersion":"draft-v1", "parameters":{},
             "evaluationOrder":1, "expectedRowVersion":"0"}
            """;

    private MockMvc mvc(boolean update) throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(DraftRiskRuleRepository.LOCK_VERSION_QUERY)).thenReturn(parent);
        when(parent.executeQuery()).thenReturn(parentRows);
        when(parentRows.next()).thenReturn(true, false);
        when(parentRows.getLong("VERSION_ID")).thenReturn(7L);
        when(parentRows.getString("PUBLICATION_STATE")).thenReturn("DRAFT");
        when(parentRows.getString("SCORE_POLICY_CODE")).thenReturn("SUM");
        when(parentRows.getObject("CREATED_AT", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
        when(parentRows.wasNull()).thenReturn(true);
        when(connection.prepareStatement(DraftRiskRuleRepository.FIND_RULE_QUERY)).thenReturn(find);
        when(find.executeQuery()).thenReturn(before, after);
        if (update) ruleRows(before, 99);
        ruleRows(after, 99);
        when(connection.prepareStatement(eq(DraftRiskRuleRepository.INSERT_RULE), org.mockito.ArgumentMatchers.<String[]>any()))
                .thenReturn(child);
        when(connection.prepareStatement(DraftRiskRuleRepository.UPDATE_RULE)).thenReturn(child);
        when(child.executeUpdate()).thenReturn(1);
        when(child.getGeneratedKeys()).thenReturn(keys);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(keys.next()).thenReturn(true, false);
        when(keys.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("RULE_CONFIG_ID");
        when(keys.getObject(1)).thenReturn(BigDecimal.valueOf(99));
        when(connection.prepareStatement(DraftRiskRuleRepository.INCREMENT_PARENT)).thenReturn(increment);
        when(increment.executeUpdate()).thenReturn(1);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.setExceptionTranslator(new SQLExceptionSubclassTranslator());
        var service = new DraftRiskRuleService(new DraftRiskRuleRepository(jdbc, JsonMapper.builder().build()));
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(new JdbcTransactionManager(dataSource));
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(advice);
        return MockMvcBuilders.standaloneSetup(new DraftRiskRuleController((DraftRiskRuleService) proxy.getProxy()))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private void ruleRows(ResultSet rows, long id) throws Exception {
        when(rows.next()).thenReturn(true, false);
        when(rows.getLong("RULE_CONFIG_ID")).thenReturn(id);
        when(rows.getLong("VERSION_ID")).thenReturn(7L);
        when(rows.getString("RULE_CODE")).thenReturn("STRUCTURING");
        when(rows.getString("DISPLAY_NAME")).thenReturn("Under review");
        when(rows.getString("PARAMETER_SCHEMA_VERSION")).thenReturn("draft-v1");
        when(rows.getString("PARAMETERS_JSON")).thenReturn("{}");
        when(rows.getInt("EVALUATION_ORDER")).thenReturn(1);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void commitsChildAndParentOnOneConnectionAndRetainsRuleIdentity(boolean update) throws Exception {
        var mvc = mvc(update);
        mvc.perform(put(PATH).contentType("application/json").content(BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rule.ruleConfigId").value("99"))
                .andExpect(jsonPath("$.rowVersion").value("1"));
        var order = inOrder(parent, find, child, increment, connection);
        order.verify(parent).executeQuery();
        order.verify(find).executeQuery();
        order.verify(child).executeUpdate();
        order.verify(increment).executeUpdate();
        order.verify(find).executeQuery();
        order.verify(connection).commit();
        verify(connection, never()).rollback();
        verify(dataSource, times(1)).getConnection();
        if (update) { verify(child).setLong(7, 99); verify(child, never()).getGeneratedKeys(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void httpClobReadbackAndResponseKeepExactNumbersForCreateAndUpdate(boolean update) throws Exception {
        var mvc = mvc(update);
        var clob = new AtomicReference<String>();
        doAnswer(invocation -> {
            Reader reader = invocation.getArgument(1);
            String json = new BufferedReader(reader).lines().collect(java.util.stream.Collectors.joining("\n"));
            org.assertj.core.api.Assertions.assertThat((Integer) invocation.getArgument(2)).isEqualTo(json.length());
            ExactParameterNumbers.assertValues(ExactParameterNumbers.object(json));
            clob.set(json);
            return null;
        }).when(child).setCharacterStream(eq(update ? 5 : 7), org.mockito.ArgumentMatchers.any(Reader.class), anyInt());
        when(after.getString("PARAMETERS_JSON")).thenAnswer(invocation -> clob.get());
        String body = BODY.replace("\"parameters\":{}", "\"parameters\":" + ExactParameterNumbers.JSON);
        String response = mvc.perform(put(PATH).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(clob.get()).isNotNull();
        ExactParameterNumbers.assertPutResponse(response);
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(dataSource, times(1)).getConnection();
    }

    @ParameterizedTest @ValueSource(strings = {"STALE", "PUBLISHED"})
    void rejectsProtectedParentBeforeAnyChildChange(String scenario) throws Exception {
        var mvc = mvc(false);
        if (scenario.equals("STALE")) when(parentRows.getLong("ROW_VERSION")).thenReturn(1L);
        else when(parentRows.getString("PUBLICATION_STATE")).thenReturn("PUBLISHED");
        mvc.perform(put(PATH).contentType("application/json").content(BODY)).andExpect(status().isConflict());
        verify(child, never()).executeUpdate();
        verify(increment, never()).executeUpdate();
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @ParameterizedTest @ValueSource(strings = {"MISSING_KEY", "INVALID_KEY", "PARENT_SQL", "PARENT_ZERO", "READBACK_SQL", "READBACK_JSON", "READBACK_MISSING", "IDENTITY_CHANGED"})
    void failureRollsBackParentAndChildTogetherWithoutSuccess(String scenario) throws Exception {
        var mvc = mvc(false);
        switch (scenario) {
            case "MISSING_KEY" -> when(keys.next()).thenReturn(false);
            case "INVALID_KEY" -> when(keys.getObject(1)).thenReturn(new BigDecimal("1.5"));
            case "PARENT_SQL" -> when(increment.executeUpdate()).thenThrow(new SQLRecoverableException("private Oracle detail", "08006", 17002));
            case "PARENT_ZERO" -> when(increment.executeUpdate()).thenReturn(0);
            case "READBACK_SQL" -> when(find.executeQuery()).thenReturn(before).thenThrow(new SQLException("private Oracle detail", "99999", 17004));
            case "READBACK_JSON" -> when(after.getString("PARAMETERS_JSON")).thenReturn("[]");
            case "READBACK_MISSING" -> when(after.next()).thenReturn(false);
            case "IDENTITY_CHANGED" -> when(after.getLong("RULE_CONFIG_ID")).thenReturn(100L);
        }
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().is(scenario.equals("PARENT_ZERO") ? 409 : 503))
                .andExpect(jsonPath("$.rule").doesNotExist())
                .andExpect(content().string(not(containsString("private Oracle detail"))));
        verify(child).executeUpdate();
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(dataSource, times(1)).getConnection();
        if (scenario.startsWith("READBACK") || scenario.equals("IDENTITY_CHANGED")) verify(increment).executeUpdate();
    }

    static Stream<Arguments> constraints() {
        return Stream.of(Arguments.of("UK_CFG_RULE_ORDER", 409, "RULE_ORDER_CONFLICT"),
                Arguments.of("UK_CFG_RULE_CODE", 409, "RULE_CODE_CONFLICT"),
                Arguments.of("PK_CFG_RULES", 503, "CONFIGURATION_UNAVAILABLE"),
                Arguments.of("UNKNOWN", 503, "CONFIGURATION_UNAVAILABLE"));
    }

    @ParameterizedTest @ValueSource(strings = {"READBACK", "ORDER_CONFLICT"})
    void updatingExistingRuleRollsBackOnFailure(String scenario) throws Exception {
        var mvc = mvc(true);
        if (scenario.equals("READBACK")) {
            when(after.getString("PARAMETERS_JSON")).thenReturn("invalid JSON");
        } else {
            when(child.executeUpdate()).thenThrow(new SQLIntegrityConstraintViolationException(
                    "ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UK_CFG_RULE_ORDER) violated", "23000", 1));
        }
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().is(scenario.equals("READBACK") ? 503 : 409))
                .andExpect(jsonPath("$.rule").doesNotExist());
        verify(child).setLong(7, 99);
        verify(child, never()).getGeneratedKeys();
        verify(connection).rollback();
        verify(connection, never()).commit();
        if (scenario.equals("READBACK")) verify(increment).executeUpdate();
    }
    @ParameterizedTest @MethodSource("constraints")
    void classifiesOnlyKnownRuleConstraintFailures(String constraint, int status, String code) throws Exception {
        var mvc = mvc(false);
        when(child.executeUpdate()).thenThrow(new SQLIntegrityConstraintViolationException(
                "ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"" + constraint + ") violated", "23000", 1));
        mvc.perform(put(PATH).contentType("application/json").content(BODY))
                .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code))
                .andExpect(content().string(not(containsString("D2_CONFIGURATION"))));
        verify(increment, never()).executeUpdate();
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
