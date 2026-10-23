package com.ofss.internal.verification;

import java.net.URI;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Opt-in test caller controls invocation. Starts only the supplied D2 application on loopback/port zero. */
public final class EphemeralReceiver implements AutoCloseable {
    private final ConfigurableApplicationContext context;
    private EphemeralReceiver(ConfigurableApplicationContext context){this.context=context;}
    public static Map<String,Object> properties(EphemeralCredentials credentials,String audience,String grants,boolean enabled,Map<String,Object> database) {
        var properties=new HashMap<String,Object>(database);
        properties.putAll(Map.of("internal-access.enabled",enabled,"internal-access.issuer",enabled?EphemeralCredentials.ISSUER:"","internal-access.audience",enabled?audience:"",
            "internal-access.public-keys",enabled?credentials.publicKeys():"","internal-access.service-permissions",enabled?grants:"","security.jwt.secret",credentials.userSecret()));
        properties.putAll(Map.of("server.address","127.0.0.1","server.port",0,"server.shutdown","graceful","spring.lifecycle.timeout-per-shutdown-phase","10s",
            "spring.sql.init.mode","never","spring.jpa.hibernate.ddl-auto","none","spring.jpa.generate-ddl",false,"spring.flyway.enabled",false,"spring.liquibase.enabled",false));
        properties.putAll(Map.of("logging.level.root","WARN","logging.level.org.springframework.security","OFF","logging.level.org.springframework.web","OFF",
            "spring.mvc.log-request-details",false,"server.tomcat.accesslog.enabled",false));
        properties.putAll(Map.of("spring.main.web-application-type","servlet","server.ssl.enabled",false,"spring.datasource.hikari.connection-init-sql",""));
        properties.put("risk-submission.enabled",false);
        return properties;
    }
    public static EphemeralReceiver start(Class<?> app,EphemeralCredentials credentials,String audience,String grants,boolean enabled,Map<String,Object> database) {
        return start(app,credentials,audience,grants,enabled,database,c->{});
    }
    public static EphemeralReceiver start(Class<?> app,EphemeralCredentials credentials,String audience,String grants,boolean enabled,Map<String,Object> database,
            java.util.function.Consumer<ConfigurableApplicationContext> testCustomization) {
        var properties=properties(credentials,audience,grants,enabled,database);
        var application=new SpringApplication(app);application.setBannerMode(Banner.Mode.OFF);application.setLogStartupInfo(false);
        application.addInitializers(c->{c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("ephemeral-test-only",properties));testCustomization.accept(c);});
        try{return new EphemeralReceiver(application.run());}
        catch(RuntimeException failure){throw new AssertionError("Ephemeral test receiver startup failed; no credentials or exception payload displayed");}
    }
    public URI base(){return URI.create("http://127.0.0.1:"+context.getEnvironment().getRequiredProperty("local.server.port",Integer.class));}
    public <T>T bean(Class<T> type){return context.getBean(type);}
    @Override public void close(){try{context.close();}catch(RuntimeException e){throw new AssertionError("Test receiver shutdown failed; cleanup must be deferred");}}
}
