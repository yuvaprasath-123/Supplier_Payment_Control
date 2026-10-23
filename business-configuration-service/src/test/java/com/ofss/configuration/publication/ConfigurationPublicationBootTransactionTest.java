package com.ofss.configuration.publication;

import com.ofss.configuration.band.DraftRiskBandRepository;
import com.ofss.configuration.readiness.PublicationReadinessRuleRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.ofss.configuration.publication.PublicationFixtures.*;

/** Actual Boot/AOP/security path, mocked connection/repositories: no server socket or Oracle. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"spring.datasource.username=mock-fixture","spring.datasource.password="})
@AutoConfigureMockMvc
class ConfigurationPublicationBootTransactionTest {
    private static final String KEY=HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("security.jwt.secret",()->KEY); }
    @Autowired MockMvc mvc;
    @Autowired ConfigurationPublicationService service;
    @MockitoBean DataSource source;
    @MockitoBean DraftRiskRuleRepository versions;
    @MockitoBean PublicationReadinessRuleRepository rules;
    @MockitoBean DraftRiskBandRepository bands;
    @MockitoBean DraftApprovalThresholdRepository thresholds;
    @MockitoBean DraftApprovalSlaRepository slas;
    @MockitoBean ConfigurationPublicationRepository repository;
    private final Connection connection=mock(Connection.class);
    @BeforeEach void setup() throws Exception {
        when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
        when(versions.findVersion(7,true)).thenAnswer(i->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(TransactionSynchronizationManager.hasResource(source)).isTrue();return Optional.of(parent("DRAFT",8));
        });
        when(rules.list(7)).thenReturn(rules());when(bands.list(7)).thenReturn(bands());when(thresholds.list(7)).thenReturn(thresholds());when(slas.list(7)).thenReturn(slas());
    }
    @Test void managerCallUsesRealWritableTransactionAndRollsBackRealPolicyRejection() throws Exception {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        var token=Jwts.builder().subject("synthetic-manager").claim("userId",42).claim("roles",List.of("FINANCE_MANAGER"))
            .expiration(Date.from(Instant.now().plusSeconds(300))).signWith(Keys.hmacShaKeyFor(KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
        mvc.perform(post("/api/v1/configuration/versions/7/publish").header("Authorization","Bearer "+token).contentType("application/json").content("{\"expectedRowVersion\":8}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFIGURATION_NOT_READY"))
            .andExpect(jsonPath("$.readiness.structurallyValid").value(false)).andExpect(jsonPath("$.readiness.ready").value(false));
        var order=inOrder(versions,rules,bands,thresholds,slas,connection);order.verify(versions).findVersion(7,true);order.verify(rules).list(7);
        order.verify(bands).list(7);order.verify(thresholds).list(7);order.verify(slas).list(7);order.verify(connection).rollback();
        verify(connection,never()).commit();verifyNoInteractions(repository);
    }
    @Test void missingAuthenticationCannotOpenTransaction() throws Exception {
        mvc.perform(post("/api/v1/configuration/versions/7/publish").contentType("application/json").content("{\"expectedRowVersion\":8}"))
            .andExpect(status().isUnauthorized());verifyNoInteractions(source,versions,rules,bands,thresholds,slas,repository);
    }
}
