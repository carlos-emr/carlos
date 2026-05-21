package io.github.carlos_emr.carlos.utility;

import java.lang.reflect.Method;
import java.sql.Connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class OscarTrackingBasicDataSourceUnitTest {

    @AfterEach
    void tearDown() {
        OscarTrackingBasicDataSource.clearTrackingState();
    }

    @Test
    void shouldClearDebugMap_whenReleasingTrackingState() throws Exception {
        Connection delegate = mock(Connection.class);
        when(delegate.isClosed()).thenReturn(false);
        Connection trackedConnection = track(delegate);

        assertThat(OscarTrackingBasicDataSource.debugMap).containsKey(trackedConnection);

        OscarTrackingBasicDataSource.clearTrackingState();

        assertThat(OscarTrackingBasicDataSource.debugMap).isEmpty();
        verify(delegate).close();
    }

    private Connection track(Connection delegate) throws Exception {
        Method trackConnection = OscarTrackingBasicDataSource.class
                .getDeclaredMethod("trackConnection", Connection.class);
        trackConnection.setAccessible(true);
        return (Connection) trackConnection.invoke(null, delegate);
    }
}
