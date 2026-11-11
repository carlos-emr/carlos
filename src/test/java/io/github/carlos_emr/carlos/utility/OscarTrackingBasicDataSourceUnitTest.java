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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Isolated
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
        Object owner = newOwner();
        Method add = owner.getClass().getDeclaredMethod("add", Connection.class);
        add.setAccessible(true);
        add.invoke(owner, connection);
        trackedThreadConnectionSets().add(owner);

        OscarTrackingBasicDataSource.clearTrackingState();

        assertThat(ownedConnections(owner)).isEmpty();
        assertThat(trackedThreadConnectionSets()).isEmpty();
        verify(connection).close();
    }

    @Test
    void shouldCloseTheDrainedSnapshotOutsideTheOwnerMonitor_whenClosingTrackedConnections() throws Exception {
        Connection delegate = mock(Connection.class);
        Connection laterDelegate = mock(Connection.class);
        try (var ownerThread = Executors.newSingleThreadExecutor()) {
            Connection tracked = ownerThread.submit(() -> track(delegate)).get(5, TimeUnit.SECONDS);
            Object owner = ownerThread.submit(this::currentOwner).get(5, TimeUnit.SECONDS);
            AtomicReference<Connection> later = new AtomicReference<>();
            doAnswer(call -> {
                // A driver callback may need the same owner from a different thread.
                // Holding the owner's monitor across close would deadlock this operation.
                later.set(ownerThread.submit(() -> track(laterDelegate)).get(5, TimeUnit.SECONDS));
                return null;
            }).when(delegate).close();

            Method close = OscarTrackingBasicDataSource.class.getDeclaredMethod(
                    "closeTrackedConnections", owner.getClass(), String.class);
            close.setAccessible(true);
            close.invoke(null, owner, "FAKE close failed");

            assertThat(ownedConnections(owner)).containsExactly(later.get()).doesNotContain(tracked);
            assertThat(OscarTrackingBasicDataSource.debugMap).doesNotContainKey(tracked).containsKey(later.get());
            verify(delegate).close();
            verify(laterDelegate, never()).close();
        }
    }

    @Test
    void shouldRemoveClosedConnectionFromItsOwner_whenClosedOnAnotherThread() throws Exception {
        AtomicReference<Object> ownerConnections = new AtomicReference<>();
        Connection delegate = mock(Connection.class);
        try (var owner = Executors.newSingleThreadExecutor()) {
            Connection connection = owner.submit(() -> {
                Connection tracked = track(delegate);
                ownerConnections.set(currentOwner());
                return tracked;
            }).get(5, TimeUnit.SECONDS);

            connection.close();

            assertThat(ownedConnections(ownerConnections.get())).isEmpty();
            assertThat(OscarTrackingBasicDataSource.debugMap).doesNotContainKey(connection);
            verify(delegate).close();
        }
    }

    @Test
    void shouldPreserveAnotherOwnersConnections_whenOneThreadReleasesItsConnections() throws Exception {
        Connection firstDelegate = mock(Connection.class);
        Connection secondDelegate = mock(Connection.class);
        try (var firstThread = Executors.newSingleThreadExecutor();
             var secondThread = Executors.newSingleThreadExecutor()) {
            firstThread.submit(() -> track(firstDelegate)).get(5, TimeUnit.SECONDS);
            Connection second = secondThread.submit(() -> track(secondDelegate)).get(5, TimeUnit.SECONDS);
            Object secondOwner = secondThread.submit(this::currentOwner).get(5, TimeUnit.SECONDS);

            firstThread.submit(OscarTrackingBasicDataSource::releaseThreadConnections).get(5, TimeUnit.SECONDS);

            assertThat(ownedConnections(secondOwner)).containsExactly(second);
            assertThat(trackedThreadConnectionSets()).containsExactly(secondOwner);
            verify(firstDelegate).close();
            verify(secondDelegate, never()).close();
        }
    }

    private Connection track(Connection delegate) throws Exception {
        Method trackConnection = OscarTrackingBasicDataSource.class
                .getDeclaredMethod("trackConnection", Connection.class);
        trackConnection.setAccessible(true);
        return (Connection) trackConnection.invoke(null, delegate);
    }

    @SuppressWarnings("unchecked")
    private Set<Object> trackedThreadConnectionSets() throws Exception {
        Field field = OscarTrackingBasicDataSource.class.getDeclaredField("trackedThreadConnectionSets");
        field.setAccessible(true);
        return (Set<Object>) field.get(null);
    }

    private Object currentOwner() throws Exception {
        Field field = OscarTrackingBasicDataSource.class.getDeclaredField("connections");
        field.setAccessible(true);
        return ((ThreadLocal<?>) field.get(null)).get();
    }

    private Object newOwner() throws Exception {
        Class<?> type = Class.forName(OscarTrackingBasicDataSource.class.getName() + "$OwnerState");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @SuppressWarnings("unchecked")
    private Set<Connection> ownedConnections(Object owner) throws Exception {
        Field field = owner.getClass().getDeclaredField("ownedConnections");
        field.setAccessible(true);
        return (Set<Connection>) field.get(owner);
    }
}
