/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
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
import static org.mockito.ArgumentMatchers.any;

@Tag("unit")
class CdsImportLockUnitTest extends CarlosUnitTestBase {
    private DataSource source;
    private Connection connection;
    private ResultSet acquired;
    private ResultSet released;

    @BeforeEach
    void prepareConnections() throws Exception {
        source = mock(DataSource.class);
        connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        acquired = resultFor(CdsImportLock.ACQUIRE_SQL);
        released = resultFor(CdsImportLock.RELEASE_SQL);
    }

    private ResultSet resultFor(String sql) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);
        return result;
    }

    @Test
    void shouldReleaseBeforeReturningConnection_whenImportCompletes() throws Exception {
        CdsImportLock lock = CdsImportLock.acquire(source);
        assertThat(lock).isNotNull();
        verify(connection, never()).close();
        lock.close();
        lock.close();
        var order = inOrder(connection, released);
        order.verify(connection).prepareStatement(CdsImportLock.RELEASE_SQL);
        order.verify(released).close();
        order.verify(connection).close();
        verify(connection, times(1)).close();
        verify(connection, never()).abort(any());
    }

    @Test
    void shouldReleaseLock_whenImportBodyThrows() throws Exception {
        assertThatThrownBy(() -> {
            try (CdsImportLock ignored = CdsImportLock.acquire(source)) {
                throw new IllegalArgumentException("Invalid import");
            }
        }).isInstanceOf(IllegalArgumentException.class);
        verify(connection).prepareStatement(CdsImportLock.RELEASE_SQL);
        verify(connection).close();
    }

    @Test
    void shouldCloseWithoutReleasingAnotherSessionLock_whenAcquireTimesOut() throws Exception {
        when(acquired.getInt(1)).thenReturn(0);
        assertThat(CdsImportLock.acquire(source)).isNull();
        verify(connection).close();
        verify(connection, never()).prepareStatement(CdsImportLock.RELEASE_SQL);
        verify(connection, never()).abort(any());
    }

    @Test
    void shouldDiscardConnection_whenAcquireReturnsNull() throws Exception {
        when(acquired.wasNull()).thenReturn(true);
        assertThatThrownBy(() -> CdsImportLock.acquire(source)).isInstanceOf(SQLException.class);
        verify(connection).abort(any());
        verify(connection).close();
    }

    @Test
    void shouldDiscardConnection_whenReleaseFails() throws Exception {
        when(released.getInt(1)).thenReturn(0);
        CdsImportLock lock = CdsImportLock.acquire(source);
        assertThatThrownBy(lock::close).isInstanceOf(SQLException.class);
        verify(connection).abort(any());
        verify(connection).close();
    }

    @Test
    void shouldPreserveImportError_whenReleaseAlsoFails() throws Exception {
        when(released.wasNull()).thenReturn(true);
        assertThatThrownBy(() -> {
            try (CdsImportLock ignored = CdsImportLock.acquire(source)) {
                throw new IllegalArgumentException("Invalid import");
            }
        }).isInstanceOf(IllegalArgumentException.class).satisfies(failure ->
                assertThat(failure.getSuppressed()).hasSize(1));
        verify(connection).abort(any());
        verify(connection).close();
    }
    @Test
    void shouldEvictFromApplicationPool_whenReleaseFails() throws Exception {
        var pool = mock(org.apache.commons.dbcp2.BasicDataSource.class);
        when(pool.getConnection()).thenReturn(connection);
        when(released.getInt(1)).thenReturn(0);
        CdsImportLock lock = CdsImportLock.acquire(pool);
        assertThatThrownBy(lock::close).isInstanceOf(SQLException.class);
        verify(pool).invalidateConnection(connection);
        verify(connection, never()).abort(any());
        verify(connection).close();
    }

}
