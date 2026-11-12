/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic.cds.jdbc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.ServerPreparedStatement;

@Tag("unit")
class CdsBinaryReadsUnitTest {
    private static final String SQL = "SELECT ? AS controlled_value";

    private record Fixture(Connection owner, ServerPreparedStatement raw) {}

    private Fixture fixture() throws Exception {
        var owner = mock(Connection.class);
        var driver = mock(org.mariadb.jdbc.Connection.class);
        var raw = mock(ServerPreparedStatement.class);
        var metadata = mock(ParameterMetaData.class);
        when(owner.unwrap(org.mariadb.jdbc.Connection.class)).thenReturn(driver);
        when(driver.prepareInternal(
                        SQL,
                        Statement.NO_GENERATED_KEYS,
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY,
                        true))
                .thenReturn(raw);
        when(raw.getParameterMetaData()).thenReturn(metadata);
        when(metadata.getParameterCount()).thenReturn(1);
        return new Fixture(owner, raw);
    }

    @Test
    void shouldRejectATextOrUnverifiableResult_beforeAnyClinicalValueCanEscape() throws Exception {
        var f = fixture();
        var text = mock(ResultSet.class);
        when(f.raw().executeQuery()).thenReturn(text);
        try (var query = CdsBinaryReads.prepareDriver(f.owner(), SQL)) {
            query.setLong(1, 17);
            assertThatThrownBy(query::executeQuery)
                    .isInstanceOf(SQLException.class)
                    .hasMessage("CDS_BINARY_RESULT_PROTOCOL_REQUIRED");
        }
        verify(text, never()).next();
        verify(text).close();
        verify(f.raw(), atLeastOnce()).close();
    }

    @Test
    void shouldRejectAReplacementSqlOrMutation_withoutInvokingTheDriver() throws Exception {
        var f = fixture();
        try (var query = CdsBinaryReads.prepareDriver(f.owner(), SQL)) {
            assertThatThrownBy(() -> query.executeQuery("SELECT another_value"))
                    .isInstanceOf(SQLException.class)
                    .hasMessage("CDS_BINARY_PREPARED_READ_REQUIRED");
            assertThatThrownBy(query::executeUpdate).isInstanceOf(SQLException.class);
            assertThatThrownBy(query::addBatch).isInstanceOf(SQLException.class);
        }
        verify(f.raw(), never()).executeQuery(anyString());
        verify(f.raw(), never()).executeUpdate();
        verify(f.raw(), never()).addBatch();
    }

    @Test
    void shouldRejectBindingOutsideThePreparedShape_withoutGrowingDriverFallbackState()
            throws Exception {
        var f = fixture();
        try (var query = CdsBinaryReads.prepareDriver(f.owner(), SQL)) {
            assertThatThrownBy(() -> query.setLong(2, 18))
                    .isInstanceOf(SQLException.class)
                    .hasMessage("CDS_BINARY_PARAMETER_INDEX_INVALID");
            assertThatThrownBy(() -> query.setLong(70_000, 18)).isInstanceOf(SQLException.class);
            query.setLong(1, 17);
        }
        verify(f.raw(), never()).setLong(2, 18);
        verify(f.raw(), never()).setLong(70_000, 18);
        verify(f.raw()).setLong(1, 17);
    }

    @Test
    void shouldRetainOwnerIdentityAndRefuseVendorUnwrapping_forBinaryReadsContract() throws Exception {
        var f = fixture();
        try (var query = CdsBinaryReads.prepareDriver(f.owner(), SQL)) {
            assertThat(query.getConnection()).isSameAs(f.owner());
            assertThat(query.unwrap(java.sql.PreparedStatement.class)).isSameAs(query);
            assertThatThrownBy(() -> query.unwrap(ServerPreparedStatement.class))
                    .isInstanceOf(SQLException.class)
                    .hasMessage("CDS_BINARY_UNWRAP_REFUSED");
            assertThat(query.toString()).isEqualTo("CDS reviewed binary read");
        }
    }
}
