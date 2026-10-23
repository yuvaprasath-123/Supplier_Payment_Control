package com.ofss.configuration.activation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

class OracleActivationSafetyTest {
    @Test void liveSuiteRequiresBothWriteAndIsolatedSingletonOptIns() {
        var guards=OracleConfigurationActivationTest.class.getAnnotationsByType(EnabledIfEnvironmentVariable.class);
        assertThat(guards).extracting(EnabledIfEnvironmentVariable::named).containsExactlyInAnyOrder("CONFIG_VERIFY_ORACLE_ACTIVATION_WRITES","CONFIG_ORACLE_ACTIVATION_ISOLATED");
        assertThat(guards).allMatch(g->g.matches().equals("true"));
    }
    @Test void guardsRejectUnexpectedStateInsteadOfRestoringUnrelatedActivePointer() {
        assertThatThrownBy(()->OracleConfigurationActivationTest.guard(false)).isInstanceOf(AssertionError.class);
    }
}
