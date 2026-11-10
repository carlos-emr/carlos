/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Failures must retain both the operation outcome and uncertain lock cleanup. @since 2026-11-09 */
@Tag("unit")
class TeleplanExportCoordinatorUnitTest {
    private Connection connection;
    private TeleplanExportCoordinator coordinator;

    @BeforeEach
    void prepare() throws Exception {
        DataSource source = mock(DataSource.class);
        connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("FAKE_6097");
        PreparedStatement acquire = mock(PreparedStatement.class);
        ResultSet acquired = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(acquire);
        when(acquire.executeQuery()).thenReturn(acquired);
        when(acquired.next()).thenReturn(true);
        when(acquired.getInt(1)).thenReturn(1);
        coordinator = new TeleplanExportCoordinator(source);
    }

    @Test
    void shouldRetainTheOperationFailure_whenReleaseAndAbortAlsoFail() throws Exception {
        var original = new IllegalStateException("synthetic export failure");
        var release = new SQLException("synthetic release failure");
        var abort = new SQLException("synthetic abort failure");
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenThrow(release);
        doThrow(abort).when(connection).abort(any());
        assertThatThrownBy(() -> coordinator.generate(() -> { throw original; })).isSameAs(original);
        assertThat(original.getSuppressed()).containsExactly(release);
        assertThat(release.getSuppressed()).containsExactly(abort);
        verify(connection).abort(any());
    }

    @Test
    void shouldReportUncertainRelease_whenTheOperationSucceeded() throws Exception {
        var release = new SQLException("synthetic release failure");
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenThrow(release);
        assertThatThrownBy(() -> coordinator.generate(() -> 42)).isSameAs(release);
        verify(connection).abort(any());
    }

    @Test
    void shouldDiscardTheConnection_whenAcquisitionOutcomeIsUnknown() throws Exception {
        var acquisition = new SQLException("synthetic acquisition failure");
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenThrow(acquisition);
        assertThatThrownBy(() -> coordinator.generate(() -> { throw new AssertionError("must not execute"); }))
                .isSameAs(acquisition);
        verify(connection).abort(any());
    }
}
