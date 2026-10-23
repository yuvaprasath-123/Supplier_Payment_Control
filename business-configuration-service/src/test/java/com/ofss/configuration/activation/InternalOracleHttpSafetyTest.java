package com.ofss.configuration.activation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Offline annotation/namespace checks only; no fixture connection or server startup. */
class InternalOracleHttpSafetyTest {
    @ParameterizedTest @ValueSource(strings={"","D2_CFG_ACTIVE_%","D2_CFG_ACTIVE_../x","D2_CFG_ACTIVE_00000000-0000-0000-0000-00000000000G","D2_CFG_ACTIVE_00000000-0000-0000-0000-000000000000_A"})
    void recoveryRefusesNoncanonicalNamespaceBeforeConnecting(String namespace){assertThrows(IllegalArgumentException.class,()->OracleInternalConfigurationCleanupTest.namespace(namespace));}
    @Test void liveServerSuiteAndRecoveryBothRequireExplicitWriteAndIsolationOptIns(){
        var live=OracleInternalConfigurationHttpTest.class.getAnnotationsByType(EnabledIfEnvironmentVariable.class);
        assertEquals(2,live.length);assertTrue(java.util.Arrays.stream(live).anyMatch(a->a.named().equals("D2_VERIFY_INTERNAL_HTTP_ORACLE_WRITES")&&a.matches().equals("true")));
        assertTrue(java.util.Arrays.stream(live).anyMatch(a->a.named().equals("CONFIG_ORACLE_INTERNAL_HTTP_ISOLATED")&&a.matches().equals("true")));
        var cleanup=OracleInternalConfigurationCleanupTest.class.getAnnotationsByType(EnabledIfEnvironmentVariable.class);
        assertEquals(3,cleanup.length);assertTrue(java.util.Arrays.stream(cleanup).anyMatch(a->a.named().equals("CONFIG_INTERNAL_HTTP_WORKERS_STOPPED")&&a.matches().equals("true")));
    }
}
