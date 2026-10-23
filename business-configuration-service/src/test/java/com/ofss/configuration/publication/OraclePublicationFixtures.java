package com.ofss.configuration.publication;

import com.ofss.configuration.version.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Narrow test-only cleanup: one canonical UUID label, unchanged DRAFT, no children/pointer. */
final class OraclePublicationFixtures {
    static final String PREFIX="D2_CFG_PUBLISH_";
    static final String DESCRIPTION="Synthetic publication rejection fixture only";
    static final long ACTOR=9223372036854775730L;
    static DataSource externalSource() {
        String password=System.getenv("CONFIG_DB_PASSWORD");
        if(password==null||password.isBlank())throw new AssertionError("Supply CONFIG_DB_PASSWORD externally");
        var source=new DriverManagerDataSource("jdbc:oracle:thin:@//localhost:1521/FREEPDB1","D2_CONFIGURATION",password);
        var jdbc=new JdbcTemplate(source);
        try {
            var identity=jdbc.queryForMap("SELECT SYS_CONTEXT('USERENV','CON_NAME') AS CONTAINER_NAME, SYS_CONTEXT('USERENV','SESSION_USER') AS OWNER_NAME FROM DUAL");
            require("FREEPDB1".equals(identity.get("CONTAINER_NAME"))&&"D2_CONFIGURATION".equals(identity.get("OWNER_NAME")));
        }catch(RuntimeException e){throw new AssertionError("Oracle publication connection/identity guard failed; details withheld");}
        return source;
    }
    static void requireNamespace(String namespace) {
        require(namespace!=null&&namespace.matches("D2_CFG_PUBLISH_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
    static void clean(DataSource source,String namespace) {
        requireNamespace(namespace);
        var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new JdbcTransactionManager(source));
        tx.executeWithoutResult(status->{
            var rows=jdbc.queryForList("SELECT VERSION_ID,DESCRIPTION,IS_DEMONSTRATION,PUBLICATION_STATE,ROW_VERSION,CREATED_BY_USER_ID,PUBLISHED_AT,PUBLISHED_BY_USER_ID,CONTENT_HASH,SCORE_POLICY_CODE,MAX_FINAL_SCORE FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_LABEL=? FOR UPDATE",namespace);
            require(rows.size()<=1);
            for(var row:rows) {
                long id=number(row.get("VERSION_ID"));
                require(DESCRIPTION.equals(row.get("DESCRIPTION"))&&"DRAFT".equals(row.get("PUBLICATION_STATE"))&&number(row.get("ROW_VERSION"))==0
                    &&number(row.get("IS_DEMONSTRATION"))==1&&number(row.get("CREATED_BY_USER_ID"))==ACTOR
                    &&row.get("PUBLISHED_AT")==null&&row.get("PUBLISHED_BY_USER_ID")==null&&row.get("CONTENT_HASH")==null
                    &&"CAP_AT_MAX".equals(row.get("SCORE_POLICY_CODE"))&&number(row.get("MAX_FINAL_SCORE"))==100);
                for(String table:List.of("RISK_RULE_CONFIGS","RISK_BAND_CONFIGS","APPROVAL_THRESHOLD_CONFIGS","SLA_CONFIGS","CURRENT_CONFIGURATION"))
                    require(jdbc.queryForObject("SELECT COUNT(*) FROM "SUPPLIER_PAYMENT_APP""+table+" WHERE VERSION_ID=?",Long.class,id)==0);
                require(jdbc.update("DELETE FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_ID=? AND VERSION_LABEL=? AND PUBLICATION_STATE='DRAFT' AND ROW_VERSION=0",id,namespace)==1);
            }
            require(jdbc.queryForObject("SELECT COUNT(*) FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_LABEL=?",Long.class,namespace)==0);
        });
    }
    static long number(Object value) { require(value instanceof Number&&!(value instanceof Double)&&!(value instanceof Float));return new java.math.BigDecimal(value.toString()).longValueExact(); }
    static void require(boolean condition){if(!condition)throw new AssertionError("Publication fixture guard refused unexpected data; no broad cleanup permitted");}
}
