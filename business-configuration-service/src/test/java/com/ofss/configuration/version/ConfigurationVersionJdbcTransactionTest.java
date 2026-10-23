package com.ofss.configuration.version;

import com.ofss.configuration.error.ApiExceptionHandler;
import com.ofss.configuration.security.JwtPrincipal;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLRecoverableException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring JDBC/transaction flow over mocked JDBC objects; NOT an Oracle test.
 * The test actor resolver bypasses JWT solely here; JWT coverage is in API tests. */
class ConfigurationVersionJdbcTransactionTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement insert = mock(PreparedStatement.class);
    private final PreparedStatement readback = mock(PreparedStatement.class);
    private final ResultSet generatedKeys = mock(ResultSet.class);
    private final ResultSet rows = mock(ResultSet.class);

    private MockMvc mvc() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        Statement maximum = mock(Statement.class);
        ResultSet maximumRows = mock(ResultSet.class);
        ResultSetMetaData maximumMetadata = mock(ResultSetMetaData.class);
        when(connection.createStatement()).thenReturn(maximum);
        when(maximum.executeQuery(ConfigurationVersionRepository.LATEST_VERSION_QUERY)).thenReturn(maximumRows);
        when(maximumRows.next()).thenReturn(true, false);
        when(maximumRows.getMetaData()).thenReturn(maximumMetadata);
        when(maximumMetadata.getColumnCount()).thenReturn(1);
        when(maximumRows.getLong(1)).thenReturn(0L);
        when(connection.prepareStatement(eq(ConfigurationVersionRepository.INSERT_DRAFT),
                org.mockito.ArgumentMatchers.<String[]>any())).thenReturn(insert);
        when(insert.getGeneratedKeys()).thenReturn(generatedKeys);
        when(connection.prepareStatement(ConfigurationVersionRepository.FIND_BY_ID_QUERY)).thenReturn(readback);
        when(readback.executeQuery()).thenReturn(rows);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.setExceptionTranslator(new SQLExceptionSubclassTranslator());
        var service = new ConfigurationVersionService(new ConfigurationVersionRepository(jdbc));
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(new JdbcTransactionManager(dataSource));
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(advice);
        var controller = new ConfigurationVersionController((ConfigurationVersionService) proxy.getProxy());
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == JwtPrincipal.class;
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binder) {
                        return new JwtPrincipal(42, "mocked-manager");
                    }
                }).build();
    }

    private void keys(Object value) throws Exception {
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(generatedKeys.next()).thenReturn(true, false);
        when(generatedKeys.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("VERSION_ID");
        when(generatedKeys.getObject(1)).thenReturn(value);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        return post(ConfigurationVersionController.PATH).contentType("application/json").content("""
                {"versionLabel":"Mock transaction", "isDemonstration":true, "scorePolicyCode":"SUM"}
                """);
    }

    private void rolledBack() throws Exception {
        verify(insert, times(1)).executeUpdate();
        verify(connection).setAutoCommit(false);
        verify(connection, times(1)).rollback();
        verify(connection, never()).commit();
        // JdbcTemplate participated in the transaction-bound connection; no
        // second/autocommit connection was used for INSERT or readback.
        verify(dataSource, times(1)).getConnection();
    }

    static Stream<Arguments> invalidIdentities() {
        return Stream.of(Arguments.of("missing", (Object) null),
                Arguments.of("zero", BigDecimal.ZERO), Arguments.of("negative", BigDecimal.valueOf(-1)),
                Arguments.of("fractional", new BigDecimal("1.5")),
                Arguments.of("overflow", new BigDecimal("9223372036854775808")),
                Arguments.of("ROWID", "mock-row-id"));
    }

    @ParameterizedTest @MethodSource("invalidIdentities")
    void identityFailureAfterInsertRollsBackAndCannotReturn201(String scenario, Object identity) throws Exception {
        var mvc = mvc();
        when(insert.executeUpdate()).thenReturn(1);
        if (identity != null) keys(identity);
        mvc.perform(request()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.versionId").doesNotExist());
        rolledBack();
        verify(readback, never()).executeQuery();
    }

    @ParameterizedTest @ValueSource(strings = {"SQL_FAILURE", "NO_ROW", "MAPPING_FAILURE", "DUPLICATE_ON_READBACK"})
    void readbackFailureAfterInsertRollsBackAndCannotReturn201(String scenario) throws Exception {
        var mvc = mvc();
        when(insert.executeUpdate()).thenReturn(1);
        keys(BigDecimal.ONE);
        switch (scenario) {
            case "SQL_FAILURE" -> when(readback.executeQuery()).thenThrow(
                    new SQLRecoverableException("private Oracle diagnostic", "08006", 17002));
            case "MAPPING_FAILURE" -> {
                when(rows.next()).thenReturn(true, false);
                when(rows.getObject("CREATED_AT", OffsetDateTime.class)).thenThrow(
                        new SQLException("private Oracle diagnostic", "99999", 17004));
            }
            case "DUPLICATE_ON_READBACK" -> when(readback.executeQuery()).thenThrow(
                    new SQLIntegrityConstraintViolationException(
                            "ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UK_CFG_VERSION_NUMBER) violated",
                            "23000", 1));
            default -> { /* mocked empty readback */ }
        }
        mvc.perform(request()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.versionId").doesNotExist())
                .andExpect(content().string(not(containsString("Oracle diagnostic"))))
                .andExpect(content().string(not(containsString("UK_CFG_VERSION_NUMBER"))))
                .andExpect(content().string(not(containsString("refresh and try again"))));
        rolledBack();
        verify(readback, times(1)).executeQuery();
    }

    @Test
    void namedDuplicateDuringGeneratedKeyRetrievalIsNotAnInsertConflict() throws Exception {
        var mvc = mvc();
        when(insert.executeUpdate()).thenReturn(1);
        when(insert.getGeneratedKeys()).thenThrow(new SQLIntegrityConstraintViolationException(
                "ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UK_CFG_VERSION_NUMBER) violated", "23000", 1));
        mvc.perform(request()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("refresh and try again"))));
        rolledBack();
        verify(readback, never()).executeQuery();
    }

    static Stream<Arguments> insertConstraints() {
        return Stream.of(
                Arguments.of("ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UK_CFG_VERSION_NUMBER) violated", 1, 409),
                Arguments.of("ORA-00001: restriccion unica (\"D2_CONFIGURATION\".\"UK_CFG_VERSION_NUMBER\") violada", 1, 409),
                Arguments.of("ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"PK_CFG_VERSIONS) violated", 1, 503),
                Arguments.of("ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UNKNOWN_CONSTRAINT) violated", 1, 503),
                Arguments.of("ORA-00001: unique constraint (OTHER.UK_CFG_VERSION_NUMBER) violated", 1, 503),
                Arguments.of("ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UK_CFG_VERSION_NUMBER_EXTRA) violated", 1, 503),
                Arguments.of("ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"PK_CFG_VERSIONS) violated; detail ("SUPPLIER_PAYMENT_APP"UK_CFG_VERSION_NUMBER)", 1, 503),
                Arguments.of("ORA-00001: missing identity", 1, 503),
                Arguments.of("ORA-00001: unique constraint ("SUPPLIER_PAYMENT_APP"UK_CFG_VERSION_NUMBER) violated", 2290, 503));
    }

    @ParameterizedTest @MethodSource("insertConstraints")
    void onlyRecognizedOracleInsertConstraintReceives409(String diagnostic, int code, int expectedStatus) throws Exception {
        var mvc = mvc();
        when(insert.executeUpdate()).thenThrow(new SQLIntegrityConstraintViolationException(diagnostic, "23000", code));
        mvc.perform(request()).andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedStatus == 409 ? "VERSION_ALLOCATION_CONFLICT" : "CONFIGURATION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("ORA-00001"))))
                .andExpect(content().string(not(containsString("D2_CONFIGURATION"))));
        rolledBack();
        verify(readback, never()).executeQuery();
    }
}
