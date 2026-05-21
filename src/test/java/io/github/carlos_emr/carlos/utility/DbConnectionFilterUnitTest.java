package io.github.carlos_emr.carlos.utility;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class DbConnectionFilterUnitTest extends CarlosUnitTestBase {

    @AfterEach
    void tearDown() throws Exception {
        trackedConnections().clear();
        trackedConnectionHolders().clear();
        threadLocalConnection().remove();
    }

    @Test
    @Tag("delete")
    void shouldCloseTrackedConnections_whenShutdownReleasesKnownResources() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isClosed()).thenReturn(false);
        trackedConnections().add(connection);

        DbConnectionFilter.releaseAllKnownDbResources();

        verify(connection).close();
        assertThat(trackedConnections()).isEmpty();
    }

    @Test
    @Tag("update")
    void shouldReplaceClosedConnection_whenThreadLocalIsReset() throws Exception {
        Connection oldConnection = mock(Connection.class);
        Connection newConnection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        when(oldConnection.isClosed()).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(newConnection);
        AtomicReference<Connection> holder = new AtomicReference<Connection>(oldConnection);
        threadLocalConnection().set(holder);
        trackedConnectionHolders().add(holder);
        trackedConnections().add(oldConnection);

        springUtilsMock.when(() -> SpringUtils.getBean(DataSource.class)).thenReturn(dataSource);

        Connection actual = DbConnectionFilter.getThreadLocalDbConnection();

        assertThat(actual).isSameAs(newConnection);
        assertThat(trackedConnections()).doesNotContain(oldConnection);
        assertThat(trackedConnections()).contains(newConnection);
        assertThat(holder.get()).isSameAs(newConnection);
        verify(newConnection).setAutoCommit(true);
    }

    @Test
    @Tag("delete")
    void shouldClearRetainedHolder_whenShutdownReleasesKnownResources() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isClosed()).thenReturn(false);
        AtomicReference<Connection> holder = new AtomicReference<Connection>(connection);
        trackedConnectionHolders().add(holder);
        trackedConnections().add(connection);

        DbConnectionFilter.releaseAllKnownDbResources();

        verify(connection).close();
        assertThat(holder.get()).isNull();
        assertThat(trackedConnectionHolders()).isEmpty();
        assertThat(trackedConnections()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Set<Connection> trackedConnections() throws Exception {
        Field field = DbConnectionFilter.class.getDeclaredField("trackedConnections");
        field.setAccessible(true);
        return (Set<Connection>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private Set<AtomicReference<Connection>> trackedConnectionHolders() throws Exception {
        Field field = DbConnectionFilter.class.getDeclaredField("trackedConnectionHolders");
        field.setAccessible(true);
        return (Set<AtomicReference<Connection>>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private ThreadLocal<AtomicReference<Connection>> threadLocalConnection() throws Exception {
        Field field = DbConnectionFilter.class.getDeclaredField("dbConnection");
        field.setAccessible(true);
        return (ThreadLocal<AtomicReference<Connection>>) field.get(null);
    }
}
