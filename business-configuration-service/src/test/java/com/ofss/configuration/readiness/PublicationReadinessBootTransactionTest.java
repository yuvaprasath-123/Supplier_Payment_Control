package com.ofss.configuration.readiness;

import com.ofss.configuration.band.DraftRiskBandRepository;
import com.ofss.configuration.rule.DraftRiskRuleRepository;
import com.ofss.configuration.sla.DraftApprovalSlaRepository;
import com.ofss.configuration.threshold.DraftApprovalThresholdRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Production Boot auto-configuration/AOP with mocked repositories and DataSource.
 * MOCK web environment opens no server socket and never connects to Oracle. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.datasource.username=mock-fixture", "spring.datasource.password="})
@AutoConfigureMockMvc
class PublicationReadinessBootTransactionTest {
    private static final String PATH = "/api/v1/configuration/versions/7/publication-readiness";
    private static final String SECRET = HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("security.jwt.secret", () -> SECRET); }
    @Autowired private MockMvc mvc;
    @Autowired private PublicationReadinessService service;
    @Autowired private PlatformTransactionManager manager;
    @MockitoBean private DataSource dataSource;
    @MockitoBean private DraftRiskRuleRepository versions;
    @MockitoBean private PublicationReadinessRuleRepository rules;
    @MockitoBean private DraftRiskBandRepository bands;
    @MockitoBean private DraftApprovalThresholdRepository thresholds;
    @MockitoBean private DraftApprovalSlaRepository slas;
    private final Connection connection = mock(Connection.class);
    @BeforeEach void setup() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(versions.findVersion(7, false)).thenReturn(Optional.of(ReadinessFixtures.parent("SUM", null, true)));
        when(rules.list(7)).thenReturn(ReadinessFixtures.rules());
        when(bands.list(7)).thenReturn(ReadinessFixtures.bands());
        when(thresholds.list(7)).thenReturn(ReadinessFixtures.thresholds());
        when(slas.list(7)).thenReturn(ReadinessFixtures.slas());
        clearInvocations(dataSource, versions, rules, bands, thresholds, slas);
    }
    @Test void realBootWiringAppliesSerializableTransactionAroundAuthenticatedControllerCall() throws Exception {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        assertThat(manager).isInstanceOf(DataSourceTransactionManager.class);
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String token = Jwts.builder().subject("manager").claim("userId", "42").claim("roles", List.of("FINANCE_MANAGER"))
                .expiration(Date.from(Instant.now().plusSeconds(300))).signWith(key).compact();
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.structurallyValid").value(true)).andExpect(jsonPath("$.ready").value(true));
        var order = inOrder(connection, versions, rules, bands, thresholds, slas);
        order.verify(connection).setReadOnly(true);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        order.verify(versions).findVersion(7, false); order.verify(rules).list(7); order.verify(bands).list(7);
        order.verify(thresholds).list(7); order.verify(slas).list(7); order.verify(connection).commit();
        verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        verify(connection).setReadOnly(false); verify(connection, never()).rollback();
        verify(connection, never()).prepareStatement(anyString());
        verify(dataSource, times(1)).getConnection();
        verifyNoMoreInteractions(versions, rules, bands, thresholds, slas);
    }
    @Test void authenticationFailureDoesNotOpenATransactionOrReadAnyConfiguration() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(dataSource, versions, rules, bands, thresholds, slas);
        verify(connection, never()).commit();
    }
}
