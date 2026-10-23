package com.ofss.configuration.activation;

import com.ofss.configuration.error.ErrorResponseWriter;
import com.ofss.configuration.security.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"spring.datasource.username=offline-fixture","spring.datasource.password="})
@AutoConfigureMockMvc
class ConfigurationActivationBootTransactionTest {
    static final String KEY=HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("security.jwt.secret",()->KEY);}
    @Autowired MockMvc mvc;
    @Autowired ConfigurationActivationService service;
    @MockitoBean DataSource source;
    @MockitoBean ConfigurationActivationRepository repository;
    final Connection connection=mock(Connection.class);
    @BeforeEach void setup() throws Exception {
        when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
        when(repository.pointer()).thenAnswer(i->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.hasResource(source)).isTrue();return Optional.empty();
        });
    }
    String token(){return Jwts.builder().subject("offline-manager").claim("userId","42").claim("roles",List.of("FINANCE_MANAGER"))
            .expiration(Date.from(Instant.now().plusSeconds(300))).signWith(Keys.hmacShaKeyFor(KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();}
    @Test void realBootProxyUsesSerializableReadOnlyTransaction() throws Exception {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        mvc.perform(get("/api/v1/configuration/active").header("Authorization","Bearer "+token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NO_ACTIVE_CONFIGURATION"));
        verify(connection).setReadOnly(true);verify(connection).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        verify(connection).rollback();verify(repository,never()).lockPointer();
    }
    @Test void realBootActivationOpensWritableTransactionAndRollsBackStalePrecondition() throws Exception {
        doAnswer(i->{assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();return null;}).when(repository).lockPointer();
        mvc.perform(post("/api/v1/configuration/versions/7/activate").header("Authorization","Bearer "+token())
                .contentType("application/json").content("{\"expectedPointerRowVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_ACTIVE_CONFIGURATION"));
        verify(repository).lockPointer();verify(connection).rollback();verify(connection,never()).commit();
    }
    @Test void authenticationPreventsOpeningDatabaseConnection() throws Exception {
        mvc.perform(get("/api/v1/configuration/active")).andExpect(status().isUnauthorized());verifyNoInteractions(source,repository);
    }
}
