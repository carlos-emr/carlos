/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.db;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import jakarta.servlet.ServletException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual request cleanup and DBCP leases, with a controlled driver's delayed abort. @since 2026-11-16 */
@Tag("unit")
@Isolated
class ReportAbortRequestCleanupUnitTest extends CarlosUnitTestBase {
    @AfterEach
    void releaseTheCallingThreadsResources() {
        DbConnectionFilter.releaseAllThreadDbResources();
    }

    @ParameterizedTest
    @ValueSource(strings = {"explicit", "filter", "execution", "deadline"})
    void shouldQuarantineTheOldLease_untilAbortAndCleanupFinish(String scenario) throws Exception {
        var acquired = new CountDownLatch(1);
        var read = new CountDownLatch(1);
        try (var pool = new SlowAbortPool(scenario); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            registerMock(DataSource.class, pool);
            warm(pool);
            try {
                var request = workers.submit(() -> {
                    try (var spring = mockStatic(SpringUtils.class)) {
                        spring.when(() -> SpringUtils.getBean(DataSource.class)).thenReturn(pool);
                        pool.requestThread = Thread.currentThread();
                        new DbConnectionFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
                            try {
                                var cursor = query(scenario.equals("deadline") ? 1 : 30);
                                cursor.next();
                                if (!scenario.equals("filter")) cursor.close();
                            } catch (SQLException failure) {
                                throw new ServletException(failure);
                            } finally {
                                pool.endingRequest = true;
                            }
                        });
                        return (Throwable) null;
                    } catch (ServletException failure) {
                        Throwable root = failure;
                        while (root instanceof ServletException && root.getCause() != null) root = root.getCause();
                        return root;
                    }
                });
                assertThat(pool.aborting.await(5, TimeUnit.SECONDS)).isTrue();
                // The refusal/request must finish while the driver is still held by the latch.
                Throwable failure = request.get(3, TimeUnit.SECONDS);
                if (scenario.equals("deadline")) assertThat(failure).isInstanceOf(SQLTimeoutException.class);
                else if (scenario.equals("execution")) assertThat(failure).isInstanceOf(SQLException.class);
                else assertThat(failure).isNull();
                int idleBeforeAbortFinished = pool.getNumIdle();
                var next = workers.submit(() -> {
                    try (Connection connection = pool.getConnection()) {
                        acquired.countDown();
                        if (!read.await(5, TimeUnit.SECONDS)) throw new AssertionError("New reader was not released");
                        try { return connection.nativeSQL("FAKE-PROBE").startsWith("FAKE-"); }
                        catch (SQLException closedByOldAbort) { return false; }
                    } finally {
                        OscarTrackingBasicDataSource.releaseThreadConnections();
                    }
                });
                await(() -> acquired.getCount() == 0 || pool.waiters() > 0 || next.isDone());
                boolean borrowedBeforeAbortFinished = acquired.getCount() == 0;
                pool.finishAbort.countDown();
                assertThat(pool.aborted.await(5, TimeUnit.SECONDS)).isTrue();
                read.countDown();
                boolean healthy = next.get(5, TimeUnit.SECONDS);
                await(() -> pool.getNumActive() == 0 && OscarTrackingBasicDataSource.trackedConnectionCount() == 0);
                System.out.println("FAKE report cleanup " + scenario + ": idleBeforeAbort=" + idleBeforeAbortFinished
                        + ", borrowedBeforeAbort=" + borrowedBeforeAbortFinished + ", nextLeaseHealthy=" + healthy);
                assertThat(idleBeforeAbortFinished).isZero();
                assertThat(borrowedBeforeAbortFinished).isFalse();
                assertThat(healthy).isTrue();
            } finally {
                pool.finishAbort.countDown();
                read.countDown();
            }
        }
    }

    @Test
    void shouldReturnAnExhaustedLeaseNormally_withoutAbortingOrLeakingIt() throws Exception {
        try (var pool = new SlowAbortPool("exhausted")) {
            registerMock(DataSource.class, pool);
            warm(pool);
            new DbConnectionFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
                try (ResultSet cursor = query(30)) { while (cursor.next()) { /* Consume the controlled row. */ } }
                catch (SQLException failure) { throw new ServletException(failure); }
            });
            assertThat(pool.aborting.getCount()).isEqualTo(1);
            assertThat(pool.getNumActive()).isZero();
            assertThat(pool.getNumIdle()).isEqualTo(1);
            try (Connection connection = pool.getConnection()) {
                assertThat(connection.getCatalog()).startsWith("FAKE-");
            }
            OscarTrackingBasicDataSource.releaseThreadConnections();
            assertThat(OscarTrackingBasicDataSource.trackedConnectionCount()).isZero();
        }
    }

    private static ResultSet query(int seconds) throws SQLException {
        return LegacyJdbcQuery.getBoundedReportResultSet(LegacyJdbcQuery.trustedReportSelectSql("SELECT 1"),
                2, System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds));
    }

    private static void warm(SlowAbortPool pool) throws SQLException {
        try (Connection connection = pool.getConnection()) { assertThat(connection.getCatalog()).startsWith("FAKE-"); }
        OscarTrackingBasicDataSource.releaseThreadConnections();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(1);
        assertThat(condition.getAsBoolean()).as("Controlled pool transition completed").isTrue();
    }

    private static final class SlowAbortPool extends OscarTrackingBasicDataSource {
        final CountDownLatch aborting = new CountDownLatch(1);
        final CountDownLatch finishAbort = new CountDownLatch(1);
        final CountDownLatch aborted = new CountDownLatch(1);
        final AtomicInteger identities = new AtomicInteger();
        final String scenario;
        volatile Thread requestThread;
        volatile boolean endingRequest;

        SlowAbortPool(String scenario) {
            this.scenario = scenario;
            setMaxTotal(1);
            setMaxIdle(1);
            setMaxWait(Duration.ofSeconds(10));
            setTestOnBorrow(true);
        }

        int waiters() { return getConnectionPool().getNumWaiters(); }

        @Override protected ConnectionFactory createConnectionFactory() { return this::physical; }

        private Connection physical() throws SQLException {
            int id = identities.incrementAndGet();
            var closed = new AtomicBoolean();
            Connection connection = mock(Connection.class);
            when(connection.isClosed()).thenAnswer(call -> {
                // Hold request cleanup at the driver boundary until asynchronous abort enters.
                // This selects the valid interleaving where abort has started but is unfinished.
                if (Thread.currentThread() == requestThread && endingRequest) {
                    if (!aborting.await(5, TimeUnit.SECONDS)) throw new SQLException("FAKE abort never entered");
                }
                return closed.get();
            });
            when(connection.isValid(anyInt())).thenAnswer(call -> !closed.get());
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.nativeSQL("FAKE-PROBE")).thenAnswer(call -> {
                if (closed.get()) throw new SQLException("FAKE physical connection is closed");
                return "FAKE-" + id;
            });
            when(connection.getCatalog()).thenAnswer(call -> {
                if (closed.get()) throw new SQLException("FAKE physical connection is closed");
                return "FAKE-" + id;
            });
            doAnswer(call -> { closed.set(true); return null; }).when(connection).close();
            doAnswer(call -> {
                aborting.countDown();
                try {
                    if (!finishAbort.await(10, TimeUnit.SECONDS)) throw new SQLException("FAKE abort was not released");
                    closed.set(true);
                } finally { aborted.countDown(); }
                return null;
            }).when(connection).abort(any());
            DatabaseMetaData metadata = mock(DatabaseMetaData.class);
            when(metadata.getDatabaseProductName()).thenReturn("MySQL");
            when(connection.getMetaData()).thenReturn(metadata);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(statement);
            ResultSet result = mock(ResultSet.class);
            when(statement.executeQuery()).thenAnswer(call -> {
                if (scenario.equals("execution")) throw new SQLException("FAKE execution failure");
                return result;
            });
            var first = new AtomicBoolean(true);
            when(result.next()).thenAnswer(call -> {
                if (scenario.equals("deadline")) {
                    if (!aborting.await(5, TimeUnit.SECONDS)) throw new SQLException("FAKE deadline never aborted");
                    throw new SQLException("FAKE streaming socket aborted");
                }
                return first.getAndSet(false);
            });
            return connection;
        }
    }
}
