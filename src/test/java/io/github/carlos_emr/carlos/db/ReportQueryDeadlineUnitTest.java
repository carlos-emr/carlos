/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
    void shouldFinishCleanup_whenConnectionAbortFails() throws Exception {
        Connection connection = mock(Connection.class);
        ResultSet delegate = mock(ResultSet.class);
        doThrow(new SQLException("Synthetic abort failure")).when(connection).abort(any());
        try (ReportQueryDeadline deadline = new ReportQueryDeadline(connection,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
             ResultSet rs = ReportQueryDeadline.wrap(delegate, deadline)) {
            rs.close();
        }
        verify(connection, timeout(1000)).abort(any());
        verify(delegate, timeout(1000)).close();
    }

    @Test
    void shouldRejectExpiredDeadline_beforeSchedulingCancellation() {
        assertThatThrownBy(() -> new ReportQueryDeadline(mock(Connection.class), System.nanoTime() - 1))
                .isInstanceOf(SQLTimeoutException.class);
    }
}
