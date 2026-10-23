package com.ofss.configuration.activation;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Exact-namespace recovery, only after user confirms every receiver/request/writer is stopped. */
@EnabledIfEnvironmentVariable(named="D2_VERIFY_INTERNAL_HTTP_ORACLE_WRITES",matches="true")
@EnabledIfEnvironmentVariable(named="CONFIG_ORACLE_INTERNAL_HTTP_ISOLATED",matches="true")
@EnabledIfEnvironmentVariable(named="CONFIG_INTERNAL_HTTP_WORKERS_STOPPED",matches="true")
class OracleInternalConfigurationCleanupTest {
    static void namespace(String value){if(value==null||!value.matches("D2_CFG_ACTIVE_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Exact canonical activation-fixture namespace required");}
    @Test void recoverOnlyReviewedControlledVersionsAndTheirOwnPointer(){
        String namespace=System.getenv("CONFIG_INTERNAL_HTTP_FIXTURE_NAMESPACE");namespace(namespace);
        var fixture=new OracleConfigurationActivationTest();fixture.connect();fixture.namespace=namespace;
        try{fixture.tx.executeWithoutResult(status->{
            fixture.jdbc.execute(ConfigurationActivationRepository.LOCK);
            var rows=fixture.jdbc.queryForList("SELECT VERSION_ID FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_LABEL IN (?,?) FOR UPDATE",namespace+"_A",namespace+"_B");
            for(var row:rows){long id=ConfigurationActivationRepository.number(row.get("VERSION_ID"),false);var parent=fixture.versions.findVersion(id,false).orElseThrow();
                fixture.expectedParents.put(id,parent);fixture.expectedHashes.put(id,fixture.hashes.compute(parent,OracleConfigurationActivationTest.ACTOR,List.of(),List.of(),List.of(),List.of()).hash());}
            fixture.cleanup(); // REQUIRED propagation joins this same lock/verification/delete transaction
        });assertEquals(0L,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM "SUPPLIER_PAYMENT_APP"CONFIGURATION_VERSIONS WHERE VERSION_LABEL IN (?,?)",Long.class,namespace+"_A",namespace+"_B"));}
        catch(RuntimeException failure){throw new AssertionError("Guarded configuration HTTP recovery refused/failed; no broad fallback; details withheld");}
        finally{fixture.close();}
    }
}
