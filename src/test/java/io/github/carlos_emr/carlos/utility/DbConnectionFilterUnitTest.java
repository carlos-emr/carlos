package io.github.carlos_emr.carlos.utility;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class DbConnectionFilterUnitTest {

    @AfterEach
    void tearDown() throws Exception {
        trackedConnections().clear();
    }

    @Test
    void shouldCloseAllKnownRawThreadLocalConnectionsDuringShutdown() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isClosed()).thenReturn(false);
        trackedConnections().add(connection);

        DbConnectionFilter.releaseAllKnownDbResources();

        verify(connection).close();
        org.assertj.core.api.Assertions.assertThat(trackedConnections()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Set<Connection> trackedConnections() throws Exception {
        Field field = DbConnectionFilter.class.getDeclaredField("trackedConnections");
        field.setAccessible(true);
        return (Set<Connection>) field.get(null);
    }
}
