/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class OscarTrackingBasicDataSourceUnitTest extends CarlosUnitTestBase {

    @AfterEach
    void tearDown() throws Exception {
        OscarTrackingBasicDataSource.clearTrackingState();
        trackedThreadConnectionSets().clear();
    }

    @Test
    @Tag("update")
    void shouldClearDebugMap_whenReleasingTrackingState() throws Exception {
        Connection delegate = mock(Connection.class);
        when(delegate.isClosed()).thenReturn(false);
        Connection trackedConnection = track(delegate);

        assertThat(OscarTrackingBasicDataSource.debugMap).containsKey(trackedConnection);

        OscarTrackingBasicDataSource.clearTrackingState();

        assertThat(OscarTrackingBasicDataSource.debugMap).isEmpty();
        verify(delegate).close();
    }

    @Test
    @Tag("delete")
    void shouldClearRetainedThreadConnectionSets_whenReleasingTrackingState() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isClosed()).thenReturn(false);
        HashSet<Connection> retainedConnections = new HashSet<Connection>();
        retainedConnections.add(connection);
        trackedThreadConnectionSets().add(retainedConnections);

        OscarTrackingBasicDataSource.clearTrackingState();

        assertThat(retainedConnections).isEmpty();
        assertThat(trackedThreadConnectionSets()).isEmpty();
        verify(connection).close();
    }

    private Connection track(Connection delegate) throws Exception {
        Method trackConnection = OscarTrackingBasicDataSource.class
                .getDeclaredMethod("trackConnection", Connection.class);
        trackConnection.setAccessible(true);
        return (Connection) trackConnection.invoke(null, delegate);
    }

    @SuppressWarnings("unchecked")
    private Set<HashSet<Connection>> trackedThreadConnectionSets() throws Exception {
        Field field = OscarTrackingBasicDataSource.class.getDeclaredField("trackedThreadConnectionSets");
        field.setAccessible(true);
        return (Set<HashSet<Connection>>) field.get(null);
    }
}
