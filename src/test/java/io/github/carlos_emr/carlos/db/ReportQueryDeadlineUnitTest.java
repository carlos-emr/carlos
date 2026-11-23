/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class ReportQueryDeadlineUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldAbortConnection_whenDeadlineExpiresInsideStreamingNext() throws Exception {
        Connection connection = mock(Connection.class);
        ResultSet delegate = mock(ResultSet.class);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch aborted = new CountDownLatch(1);
        doAnswer(ignored -> { aborted.countDown(); return null; }).when(connection).abort(any());
        when(delegate.next()).thenAnswer(ignored -> {
            reading.countDown();
            assertThat(aborted.await(5, TimeUnit.SECONDS)).isTrue();
            throw new SQLException("Socket closed by abort");
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             ReportQueryDeadline deadline = new ReportQueryDeadline(connection,
                     System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
             ResultSet rs = ReportQueryDeadline.wrap(delegate, deadline)) {
            var read = executor.submit(() -> assertThatThrownBy(rs::next).isInstanceOf(SQLTimeoutException.class));
            assertThat(reading.await(1, TimeUnit.SECONDS)).isTrue();
            read.get(5, TimeUnit.SECONDS);
        }
        verify(connection).abort(any());
        verify(delegate, timeout(1000)).close();
    }

    @Test
    void shouldReturnEarlyClose_withoutDrainingOrWaitingForSlowAbort() throws Exception {
        Connection connection = mock(Connection.class);
        ResultSet delegate = mock(ResultSet.class);
        CountDownLatch aborting = new CountDownLatch(1);
        CountDownLatch releaseAbort = new CountDownLatch(1);
        doAnswer(ignored -> {
            aborting.countDown();
            assertThat(releaseAbort.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(connection).abort(any());
        try (ReportQueryDeadline deadline = new ReportQueryDeadline(connection,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
             ResultSet rs = ReportQueryDeadline.wrap(delegate, deadline);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                executor.submit(() -> { rs.close(); return null; }).get(1, TimeUnit.SECONDS);
                assertThat(aborting.await(1, TimeUnit.SECONDS)).isTrue();
                verify(delegate, never()).close();
            } finally {
                releaseAbort.countDown();
            }
        }
        verify(delegate, timeout(1000)).close();
    }

    @Test
    void shouldRetainConnection_whenCursorIsFullyConsumed() throws Exception {
        Connection connection = mock(Connection.class);
        ResultSet delegate = mock(ResultSet.class);
        try (ReportQueryDeadline deadline = new ReportQueryDeadline(connection,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
             ResultSet rs = ReportQueryDeadline.wrap(delegate, deadline)) {
            assertThat(rs.next()).isFalse();
        }
        verify(connection, never()).abort(any());
        verify(delegate).close();
    }

    @Test
    void shouldWarnAboutBothFailures_whenConnectionAbortFailsBeforeCursorClose() throws Exception {
        Connection connection = mock(Connection.class);
        ResultSet delegate = mock(ResultSet.class);
        SQLException abortFailure = new SQLException("Synthetic abort failure");
        SQLException closeFailure = new SQLNonTransientConnectionException("Synthetic cursor socket failure", "08000");
        doThrow(abortFailure).when(connection).abort(any());
        doThrow(closeFailure).when(delegate).close();
        try (LogCapture logs = LogCapture.forLogger(ReportQueryDeadline.class);
             ReportQueryDeadline deadline = new ReportQueryDeadline(connection,
                     System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
             ResultSet rs = ReportQueryDeadline.wrap(delegate, deadline)) {
            rs.close();
            assertThat(awaitLog(logs, abortFailure).getLevel()).isEqualTo(Level.WARN);
            assertThat(awaitLog(logs, closeFailure).getLevel()).isEqualTo(Level.WARN);
        }
        verify(connection, timeout(1000)).abort(any());
        verify(delegate, timeout(1000)).close();
    }

    @Test
    void shouldRejectExpiredDeadline_beforeSchedulingCancellation() {
        assertThatThrownBy(() -> new ReportQueryDeadline(mock(Connection.class), System.nanoTime() - 1))
                .isInstanceOf(SQLTimeoutException.class);
    }

    @Test
    void shouldPreserveTheDriverFailure_whenTheDeadlineHasNotExpired() throws Exception {
        ResultSet delegate = mock(ResultSet.class);
        SQLException failure = new SQLException("Synthetic ordinary driver failure");
        when(delegate.next()).thenThrow(failure);
        try (ReportQueryDeadline deadline = new ReportQueryDeadline(mock(Connection.class),
                System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
             ResultSet rs = ReportQueryDeadline.wrap(delegate, deadline)) {
            assertThatThrownBy(rs::next).isSameAs(failure);
        }
        verify(delegate, timeout(1000)).close();
    }

    @Test
    void shouldPreserveNonSqlFailures_whenTheDeadlineExpiresDuringTheDriverCall() throws Exception {
        Connection connection = mock(Connection.class);
        ResultSet delegate = mock(ResultSet.class);
        CountDownLatch aborted = new CountDownLatch(1);
        doAnswer(call -> { aborted.countDown(); return null; }).when(connection).abort(any());
        IllegalStateException failure = new IllegalStateException("Synthetic non-SQL driver failure");
        when(delegate.next()).thenAnswer(call -> {
            assertThat(aborted.await(5, TimeUnit.SECONDS)).isTrue();
            throw failure;
        });
        try (ReportQueryDeadline deadline = new ReportQueryDeadline(connection,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
             ResultSet rs = ReportQueryDeadline.wrap(delegate, deadline)) {
            assertThatThrownBy(rs::next).isSameAs(failure);
        }
        verify(delegate, timeout(1000)).close();
    }

    @Test
    void shouldRefuseALateSuccessfulRead_andAnyFurtherReadAfterExpiry() throws Exception {
        Connection connection = mock(Connection.class);
        ResultSet delegate = mock(ResultSet.class);
        CountDownLatch aborted = new CountDownLatch(1);
        doAnswer(call -> { aborted.countDown(); return null; }).when(connection).abort(any());
        when(delegate.next()).thenAnswer(call -> {
            assertThat(aborted.await(5, TimeUnit.SECONDS)).isTrue();
            return true;
        });
        try (ReportQueryDeadline deadline = new ReportQueryDeadline(connection,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
             ResultSet rs = ReportQueryDeadline.wrap(delegate, deadline)) {
            assertThatThrownBy(rs::next).isInstanceOf(SQLTimeoutException.class);
            assertThatThrownBy(rs::next).isInstanceOf(SQLTimeoutException.class);
            verify(delegate).next();
        }
        verify(delegate, timeout(1000)).close();
    }

    @ParameterizedTest
    @MethodSource("cursorCloseFailures")
    void shouldLogAsynchronousCursorCloseFailureOnce(Throwable failure, Level level) throws Exception {
        Connection connection = mock(Connection.class);
        ResultSet delegate = mock(ResultSet.class);
        doThrow(failure).when(delegate).close();
        try (LogCapture logs = LogCapture.forLogger(ReportQueryDeadline.class);
             ReportQueryDeadline deadline = new ReportQueryDeadline(connection,
                     System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
             ResultSet rs = ReportQueryDeadline.wrap(delegate, deadline)) {
            rs.close();
            rs.close();
            assertThat(awaitLog(logs, failure).getLevel()).isEqualTo(level);
        }
        verify(connection).abort(any());
        verify(delegate).close();
    }

    private static Stream<Arguments> cursorCloseFailures() {
        return Stream.of(
                Arguments.of(new SQLNonTransientConnectionException("Synthetic cursor socket failure", "08000"), Level.DEBUG),
                Arguments.of(new SQLException("Synthetic non-connection cursor failure", "HY000"), Level.WARN),
                Arguments.of(new SQLException("Synthetic cursor failure without SQL state"), Level.WARN),
                Arguments.of(new IllegalStateException("Synthetic unexpected cursor failure"), Level.WARN));
    }

    private static LogEvent awaitLog(LogCapture logs, Throwable failure) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            for (LogEvent event : logs.events()) {
                if (event.getThrown() == failure) return event;
            }
            Thread.sleep(5);
        } while (System.nanoTime() < end);
        throw new AssertionError("Expected report cleanup log", failure);
    }
}
