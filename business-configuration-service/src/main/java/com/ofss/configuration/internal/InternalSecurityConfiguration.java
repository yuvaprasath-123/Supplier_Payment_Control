package com.ofss.configuration.internal;

import com.ofss.configuration.error.ErrorResponseWriter;
import com.ofss.internal.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods=false)
public class InternalSecurityConfiguration {
    @Bean InternalServiceVerifier configurationServiceVerifier(Environment env) { return InternalServiceVerifier.from(env,ServicePermissions.CONFIGURATION); }
    @Bean @Order(1)
    SecurityFilterChain configurationInternalSecurity(HttpSecurity http,InternalServiceVerifier verifier,ErrorResponseWriter errors) throws Exception {
        return http.securityMatcher("/internal/**").csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable())
            .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.GET,"/internal/v1/configuration/active").hasAuthority(ServicePermissions.authority(ServicePermissions.CONFIG_ACTIVE))
                .requestMatchers(HttpMethod.GET,"/internal/v1/configuration/versions/*").hasAuthority(ServicePermissions.authority(ServicePermissions.CONFIG_VERSION)).anyRequest().denyAll())
            .exceptionHandling(e->e.authenticationEntryPoint((request,response,failure)->errors.unauthorized(request,response,false))
                .accessDeniedHandler((request,response,failure)->errors.write(request,response,403,"FORBIDDEN","Access is denied")))
            .addFilterBefore(new InternalAuthenticationFilter(verifier,errors),UsernamePasswordAuthenticationFilter.class).build();
    }
}
