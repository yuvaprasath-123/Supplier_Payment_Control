package com.ofss.configuration.internal;

import com.ofss.configuration.activation.*;
import com.ofss.configuration.error.*;
import com.ofss.configuration.security.*;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({InternalConfigurationController.class,ConfigurationActivationController.class})
@Import({InternalSecurityConfiguration.class,SecurityConfiguration.class,JwtVerifier.class,ErrorResponseWriter.class,ApiExceptionHandler.class})
class InternalConfigurationDisabledTest {
    static final String KEY=HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {r.add("security.jwt.secret",()->KEY);r.add("internal-access.enabled",()->false);}
    @Autowired MockMvc mvc;
    @MockitoBean ConfigurationActivationService service;
    @Test void disabledModeNeedsNoServiceKeysAndCannotReadOrWrite() throws Exception {
        for(String path:List.of("/internal/v1/configuration/active","/internal/v1/configuration/versions/7"))
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INTERNAL_ACCESS_DISABLED"));
        mvc.perform(post("/internal/v1/configuration/versions/7/activate").header("Authorization","Bearer invalid")).andExpect(status().isNotFound());verifyNoInteractions(service);
    }
    @Test void managerApiStillWorksWithNoInternalCredentialConfiguration() throws Exception {
        String token=Jwts.builder().subject("manager").claim("userId",42).claim("roles",List.of("FINANCE_MANAGER"))
            .expiration(Date.from(Instant.now().plusSeconds(300))).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
        mvc.perform(get("/api/v1/configuration/active").header("Authorization","Bearer "+token)).andExpect(status().isOk());verify(service).active();
    }
}
