package com.ofss.configuration.publication;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Guard tests use mock JDBC only; no credentials, socket or Oracle. */
class OraclePublicationSafetyTest {
    @ParameterizedTest @ValueSource(strings={"","D2_CFG_PUBLISH_%","D2_CFG_PUBLISH_..","D2_CFG_PUBLISH_00000000-0000-0000-0000-00000000000G","other_00000000-0000-0000-0000-000000000000"})
    void invalidCleanupNamespaceIsRejectedBeforeConnection(String namespace) {
        var source=mock(DataSource.class);assertThatThrownBy(()->OraclePublicationFixtures.clean(source,namespace)).isInstanceOf(AssertionError.class);verifyNoInteractions(source);
    }
    @ParameterizedTest @ValueSource(strings={"published","changed-revision","wrong-marker","wrong-creator","non-null-hash","attached-child"})
    void unexpectedRowsRefuseDeletionAndRollback(String change) throws Exception {
        var source=mock(DataSource.class);var connection=mock(Connection.class);var select=mock(PreparedStatement.class);var counts=mock(PreparedStatement.class);var rows=mock(ResultSet.class);var countRows=mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(argThat((String sql)->sql!=null&&sql.contains("FOR UPDATE")))).thenReturn(select);
        when(connection.prepareStatement(argThat((String sql)->sql!=null&&sql.startsWith("SELECT COUNT")))).thenReturn(counts);
        when(select.executeQuery()).thenReturn(rows);when(rows.next()).thenReturn(true,false);
        var values=new LinkedHashMap<String,Object>();values.put("VERSION_ID",BigDecimal.valueOf(7));values.put("DESCRIPTION",OraclePublicationFixtures.DESCRIPTION);
        values.put("IS_DEMONSTRATION",BigDecimal.ONE);values.put("PUBLICATION_STATE","DRAFT");values.put("ROW_VERSION",BigDecimal.ZERO);
        values.put("CREATED_BY_USER_ID",BigDecimal.valueOf(OraclePublicationFixtures.ACTOR));values.put("SCORE_POLICY_CODE","CAP_AT_MAX");values.put("MAX_FINAL_SCORE",BigDecimal.valueOf(100));
        switch(change){case "published"->values.put("PUBLICATION_STATE","PUBLISHED");case "changed-revision"->values.put("ROW_VERSION",BigDecimal.ONE);
            case "wrong-marker"->values.put("DESCRIPTION","other record");case "wrong-creator"->values.put("CREATED_BY_USER_ID",BigDecimal.ONE);case "non-null-hash"->values.put("CONTENT_HASH","a".repeat(64));}
        var meta=mock(ResultSetMetaData.class);when(rows.getMetaData()).thenReturn(meta);when(meta.getColumnCount()).thenReturn(values.size());
        var entries=new ArrayList<>(values.entrySet());for(int i=0;i<entries.size();i++){when(meta.getColumnLabel(i+1)).thenReturn(entries.get(i).getKey());when(rows.getObject(i+1)).thenReturn(entries.get(i).getValue());}
        if(change.equals("attached-child")){var countMeta=mock(ResultSetMetaData.class);when(countRows.getMetaData()).thenReturn(countMeta);when(countMeta.getColumnCount()).thenReturn(1);
            when(counts.executeQuery()).thenReturn(countRows);when(countRows.next()).thenReturn(true,false);when(countRows.getLong(1)).thenReturn(1L);}
        assertThatThrownBy(()->OraclePublicationFixtures.clean(source,"D2_CFG_PUBLISH_00000000-0000-0000-0000-000000000000")).isInstanceOf(AssertionError.class);
        verify(connection).rollback();verify(connection,never()).commit();verify(connection,never()).prepareStatement(argThat((String sql)->sql!=null&&sql.startsWith("DELETE")));
    }
    @Test void bothLiveEntryPointsAreOptInAndCleanupRequiresExplicitWorkerConfirmation() {
        var live=OracleConfigurationPublicationTest.class.getAnnotationsByType(org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable.class);
        assertThat(live).anySatisfy(flag->{assertThat(flag.named()).isEqualTo("CONFIG_VERIFY_ORACLE_PUBLICATION_WRITES");assertThat(flag.matches()).isEqualTo("true");});
        var cleanup=OraclePublicationFixtureCleanupTest.class.getAnnotationsByType(org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable.class);
        assertThat(cleanup).extracting(org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable::named)
            .containsExactlyInAnyOrder("CONFIG_VERIFY_ORACLE_PUBLICATION_WRITES","CONFIG_PUBLICATION_CLEANUP_ONLY","CONFIG_PUBLICATION_WORKERS_STOPPED");
    }
}
