package io.github.carlos_emr.carlos.utility;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class DbConnectionFilterUnitTest {

    @AfterEach
    void tearDown() throws Exception {
        trackedConnections().clear();
        threadLocalConnection().remove();
    }

    @Test
    void shouldCloseTrackedConnections_whenShutdownReleasesKnownResources() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isClosed()).thenReturn(false);
        trackedConnections().add(connection);

        DbConnectionFilter.releaseAllKnownDbResources();

        verify(connection).close();
        assertThat(trackedConnections()).isEmpty();
    }

    @Test
    void shouldReplaceClosedConnection_whenThreadLocalIsReset() throws Exception {
        Connection oldConnection = mock(Connection.class);
        Connection newConnection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        when(oldConnection.isClosed()).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(newConnection);
        threadLocalConnection().set(oldConnection);
        trackedConnections().add(oldConnection);

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(DataSource.class)).thenReturn(dataSource);

            Connection actual = DbConnectionFilter.getThreadLocalDbConnection();

            assertThat(actual).isSameAs(newConnection);
            assertThat(trackedConnections()).doesNotContain(oldConnection);
            assertThat(trackedConnections()).contains(newConnection);
            verify(newConnection).setAutoCommit(true);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<Connection> trackedConnections() throws Exception {
        Field field = DbConnectionFilter.class.getDeclaredField("trackedConnections");
        field.setAccessible(true);
        return (Set<Connection>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private ThreadLocal<Connection> threadLocalConnection() throws Exception {
        Field field = DbConnectionFilter.class.getDeclaredField("dbConnection");
        field.setAccessible(true);
        return (ThreadLocal<Connection>) field.get(null);
    }
}
