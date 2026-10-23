package com.ofss.configuration.publication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Explicit recovery only after both test workers have stopped. No wildcard cleanup. */
@EnabledIfEnvironmentVariable(named="CONFIG_VERIFY_ORACLE_PUBLICATION_WRITES",matches="true")
@EnabledIfEnvironmentVariable(named="CONFIG_PUBLICATION_CLEANUP_ONLY",matches="true")
@EnabledIfEnvironmentVariable(named="CONFIG_PUBLICATION_WORKERS_STOPPED",matches="true")
class OraclePublicationFixtureCleanupTest {
    @Test void removesOnlyAnUntouchedCanonicalSyntheticDraft() {
        String namespace=System.getenv("CONFIG_PUBLICATION_FIXTURE_NAMESPACE");OraclePublicationFixtures.requireNamespace(namespace);
        var source=OraclePublicationFixtures.externalSource();
        try {OraclePublicationFixtures.clean(source,namespace);}
        catch(RuntimeException failure){throw new AssertionError("Guarded publication cleanup failed; details withheld");}
        System.out.println("Publication fixture cleanup remaining parents: 0");
    }
}
