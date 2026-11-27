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
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

        assertThat(OscarTrackingBasicDataSource.isConnectionTracked(trackedConnection)).isTrue();

        OscarTrackingBasicDataSource.clearTrackingState();

        assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isZero();
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
    void shouldLogTheAcquisitionTrace_withoutDrainingTheLease() throws Exception {
        Connection tracked = track(mock(Connection.class));
        try (LogCapture capture = LogCapture.forLogger(OscarTrackingBasicDataSource.logger.getName())) {
            OscarTrackingBasicDataSource.logDebugMapToError();
            assertThat(capture.messages()).anySatisfy(message ->
                    assertThat(message).contains(tracked.toString(), ".trackConnection("));
        }
        assertThat(OscarTrackingBasicDataSource.isConnectionTracked(tracked)).isTrue();
    }

    @Test
    void shouldLeaveExtensionHashCodeOutsideDiagnostics_whenCheckingOrClosingAnExternalWrapper() throws Exception {
        Connection delegate = mock(Connection.class);
        Connection external = new OscarTrackingBasicDataSource.TrackingJdbcConnection(delegate) {
            @Override
            public int hashCode() {
                throw new AssertionError("Diagnostics called an extension's hashCode");
            }
        };
        assertThat(OscarTrackingBasicDataSource.isConnectionTracked(external)).isFalse();
        external.close();
        verify(delegate).close();
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
            assertThat(OscarTrackingBasicDataSource.isConnectionTracked(tracked)).isFalse();
            assertThat(OscarTrackingBasicDataSource.isConnectionTracked(later.get())).isTrue();
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
            assertThat(OscarTrackingBasicDataSource.isConnectionTracked(connection)).isFalse();
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

    @Test
    void shouldTransferOnlyTheOriginalOwnersLease_whileRetainingDiagnosticsUntilClose() throws Exception {
        Connection delegate = mock(Connection.class);
        Connection unrelatedDelegate = mock(Connection.class);
        try (var ownerThread = Executors.newSingleThreadExecutor()) {
            Connection transferred = ownerThread.submit(() -> track(delegate)).get(5, TimeUnit.SECONDS);
            Object owner = ownerThread.submit(this::currentOwner).get(5, TimeUnit.SECONDS);
            Connection unrelated = track(unrelatedDelegate);

            OscarTrackingBasicDataSource.detachFromRequestCleanup(transferred);
            OscarTrackingBasicDataSource.detachFromRequestCleanup(transferred);

            assertThat(ownedConnections(owner)).isEmpty();
            assertThat(ownedConnections(currentOwner())).containsExactly(unrelated);
            assertThat(OscarTrackingBasicDataSource.isConnectionTracked(transferred)).isTrue();
            ownerThread.submit(OscarTrackingBasicDataSource::releaseThreadConnections).get(5, TimeUnit.SECONDS);
            OscarTrackingBasicDataSource.releaseThreadConnections();
            verify(delegate, never()).close();
            verify(unrelatedDelegate).close();
            assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isOne();
            assertThat(OscarTrackingBasicDataSource.isConnectionTracked(transferred)).isTrue();

            transferred.close();
            verify(delegate).close();
            assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isZero();
        }
    }

    @Test
    void shouldDrainDetachedLeaseOutsideDiagnosticsLock_atShutdown() throws Exception {
        Connection delegate = mock(Connection.class);
        Connection transferred = track(delegate);
        OscarTrackingBasicDataSource.detachFromRequestCleanup(transferred);
        OscarTrackingBasicDataSource.releaseThreadConnections();
        verify(delegate, never()).close();

        try (var observer = Executors.newSingleThreadExecutor()) {
            doAnswer(call -> {
                assertThat(observer.submit(OscarTrackingBasicDataSource::trackedConnectionCount)
                        .get(5, TimeUnit.SECONDS)).isZero();
                return null;
            }).when(delegate).close();
            OscarTrackingBasicDataSource.clearTrackingState();
        }

        verify(delegate).close();
        assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isZero();
    }

    @Test
    void shouldKeepLeaseDiscoverableOutsideDiagnosticsLock_whileDelegateCloseRuns() throws Exception {
        Connection delegate = mock(Connection.class);
        Connection tracked = track(delegate);

        try (var observer = Executors.newSingleThreadExecutor()) {
            doAnswer(call -> {
                assertThat(observer.submit(() -> OscarTrackingBasicDataSource.isConnectionTracked(tracked))
                        .get(5, TimeUnit.SECONDS)).isTrue();
                assertThat(observer.submit(OscarTrackingBasicDataSource::trackedConnectionCount)
                        .get(5, TimeUnit.SECONDS)).isOne();
                return null;
            }).when(delegate).close();
            tracked.close();
        }

        verify(delegate).close();
        assertThat(OscarTrackingBasicDataSource.isConnectionTracked(tracked)).isFalse();
        assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isZero();
    }

    @Test
    void shouldRetainDetachedLeaseAcrossGcAndRetryAtShutdown_whenCallerDropsWrapperAfterFailedClose() throws Exception {
        AtomicInteger closeAttempts = new AtomicInteger();
        try (var pool = createFirstCloseFailingH2Pool(closeAttempts)) {
            java.lang.ref.WeakReference<Connection> releasedWrapper = acquireDetachedAndFailFirstClose(pool);
            awaitGarbageCollection();

            assertThat(closeAttempts.get()).isOne();
            assertThat(releasedWrapper.get()).isNotNull();
            assertThat(pool.getNumActive()).isOne();
            assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isOne();

            OscarTrackingBasicDataSource.clearTrackingState();

            assertThat(closeAttempts.get()).isEqualTo(2);
            assertThat(pool.getNumActive()).isZero();
            assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isZero();
            awaitGarbageCollection();
            assertThat(releasedWrapper.get()).isNull();
        }
    }

    @Test
    void shouldReleaseRecoveryReference_whenSubsequentCloseSucceedsBeforeShutdown() throws Exception {
        AtomicInteger closeAttempts = new AtomicInteger();
        try (var pool = createFirstCloseFailingH2Pool(closeAttempts)) {
            java.lang.ref.WeakReference<Connection> releasedWrapper = acquireDetachedFailAndRetryClose(pool);
            awaitGarbageCollection();

            assertThat(closeAttempts.get()).isEqualTo(2);
            assertThat(pool.getNumActive()).isZero();
            assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isZero();
            assertThat(failedCloseRecoveryLeases()).isEmpty();
            assertThat(releasedWrapper.get()).isNull();
        }
    }

    @Test
    void shouldNotPinUntrackedOrExtendedWrappers_whenCloseFailsWithOpenDelegate() throws Exception {
        Connection delegate = mock(Connection.class);
        doThrow(new SQLException("close failed")).when(delegate).close();
        when(delegate.isClosed()).thenReturn(false);

        Connection untracked = new OscarTrackingBasicDataSource.TrackingJdbcConnection(delegate);
        Connection extended = new OscarTrackingBasicDataSource.TrackingJdbcConnection(delegate) { };

        assertThatThrownBy(untracked::close).isInstanceOf(SQLException.class);
        assertThatThrownBy(extended::close).isInstanceOf(SQLException.class);
        assertThat(failedCloseRecoveryLeases()).isEmpty();
        assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isZero();
    }

    private static OscarTrackingBasicDataSource createFirstCloseFailingH2Pool(AtomicInteger closeAttempts) {
        var pool = new OscarTrackingBasicDataSource() {
            @Override
            protected synchronized DataSource createDataSource() throws SQLException {
                DataSource underlying = super.createDataSource();
                return (DataSource) Proxy.newProxyInstance(
                        DataSource.class.getClassLoader(),
                        new Class<?>[] {DataSource.class},
                        (proxy, method, args) -> {
                            Object result = method.invoke(underlying, args);
                            if ("getConnection".equals(method.getName()) && result instanceof Connection pooled) {
                                return Proxy.newProxyInstance(
                                        Connection.class.getClassLoader(),
                                        new Class<?>[] {Connection.class},
                                        (connProxy, connMethod, connArgs) -> {
                                            if ("close".equals(connMethod.getName())
                                                    && closeAttempts.incrementAndGet() == 1) {
                                                throw new SQLException("simulated rollback failure on close");
                                            }
                                            return connMethod.invoke(pooled, connArgs);
                                        });
                            }
                            return result;
                        });
            }
        };
        pool.setDriverClassName("org.h2.Driver");
        pool.setUrl("jdbc:h2:mem:tracking_close_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        pool.setUsername("sa");
        pool.setPassword("");
        pool.setMaxTotal(2);
        return pool;
    }

    private static java.lang.ref.WeakReference<Connection> acquireDetachedAndFailFirstClose(
            OscarTrackingBasicDataSource pool) throws SQLException {
        Connection lease = pool.getConnection();
        OscarTrackingBasicDataSource.detachFromRequestCleanup(lease);
        OscarTrackingBasicDataSource.releaseThreadConnections();
        assertThatThrownBy(lease::close)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("rollback failure");
        assertThat(lease.isClosed()).isFalse();
        assertThat(OscarTrackingBasicDataSource.isConnectionTracked(lease)).isTrue();
        return new java.lang.ref.WeakReference<>(lease);
    }

    private static java.lang.ref.WeakReference<Connection> acquireDetachedFailAndRetryClose(
            OscarTrackingBasicDataSource pool) throws SQLException {
        Connection lease = pool.getConnection();
        OscarTrackingBasicDataSource.detachFromRequestCleanup(lease);
        OscarTrackingBasicDataSource.releaseThreadConnections();
        assertThatThrownBy(lease::close).isInstanceOf(SQLException.class);
        lease.close();
        assertThat(lease.isClosed()).isTrue();
        assertThat(OscarTrackingBasicDataSource.isConnectionTracked(lease)).isFalse();
        return new java.lang.ref.WeakReference<>(lease);
    }

    private static void awaitGarbageCollection() throws InterruptedException {
        var canary = new java.lang.ref.WeakReference<>(new Object());
        for (int attempt = 0; attempt < 50 && canary.get() != null; attempt++) {
            System.gc();
            Thread.sleep(10);
        }
        assertThat(canary.get()).as("GC canary should be collected").isNull();
    }

    @Test
    void shouldUntrackLease_whenCloseFailsAfterDelegateCloses() throws Exception {
        Connection delegate = mock(Connection.class);
        doThrow(new SQLException("cleanup warning")).when(delegate).close();
        when(delegate.isClosed()).thenReturn(true);
        Connection tracked = track(delegate);

        assertThatThrownBy(tracked::close)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("cleanup warning");
        assertThat(OscarTrackingBasicDataSource.isConnectionTracked(tracked)).isFalse();
        assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isZero();
    }

    @Test
    void shouldRetainLeaseAndSuppressStatusError_whenCloseAndIsClosedBothFail() throws Exception {
        Connection delegate = mock(Connection.class);
        SQLException closeError = new SQLException("close failed");
        SQLException statusError = new SQLException("isClosed failed");
        doThrow(closeError).doNothing().when(delegate).close();
        when(delegate.isClosed()).thenThrow(statusError).thenReturn(false);
        Connection tracked = track(delegate);
        OscarTrackingBasicDataSource.detachFromRequestCleanup(tracked);
        OscarTrackingBasicDataSource.releaseThreadConnections();

        assertThatThrownBy(tracked::close)
                .isSameAs(closeError)
                .hasSuppressedException(statusError);
        assertThat(OscarTrackingBasicDataSource.isConnectionTracked(tracked)).isTrue();
        assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isOne();

        OscarTrackingBasicDataSource.clearTrackingState();

        verify(delegate, times(2)).close();
        assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isZero();
    }

    @Test
    void shouldLeaveOtherDataSourcesAndOwnerlessWrappersUnchanged_whenTransferringCleanup() throws Exception {
        Connection ordinary = mock(Connection.class);
        Connection delegate = mock(Connection.class);
        // No lease has been tracked on this thread, so this public wrapper has no owner.
        Connection ownerless = new OscarTrackingBasicDataSource.TrackingJdbcConnection(delegate);

        assertThat(OscarTrackingBasicDataSource.isConnectionTracked(ordinary)).isFalse();
        assertThat(OscarTrackingBasicDataSource.isConnectionTracked(ownerless)).isFalse();
        assertThat(OscarTrackingBasicDataSource.isConnectionTracked(null)).isFalse();
        OscarTrackingBasicDataSource.detachFromRequestCleanup(ordinary);
        OscarTrackingBasicDataSource.detachFromRequestCleanup(ownerless);
        OscarTrackingBasicDataSource.detachFromRequestCleanup(null);

        org.mockito.Mockito.verifyNoInteractions(ordinary, delegate);
        ownerless.close();
        verify(delegate).close();
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

    @SuppressWarnings("unchecked")
    private Set<Connection> failedCloseRecoveryLeases() throws Exception {
        Field field = OscarTrackingBasicDataSource.class.getDeclaredField("failedCloseRecoveryLeases");
        field.setAccessible(true);
        return (Set<Connection>) field.get(null);
    }
}
