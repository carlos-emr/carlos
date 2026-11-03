/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import java.sql.ResultSet;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("unit")
class ReportQueryDeadlineUnitTest {
    @Test
    void shouldCancelExpiredStreamingCursor_andStillAllowResourceCleanup() throws Exception {
        Statement statement = mock(Statement.class);
        ResultSet delegate = mock(ResultSet.class);
        CountDownLatch cancelled = new CountDownLatch(1);
        doAnswer(ignored -> { cancelled.countDown(); return null; }).when(statement).cancel();
        try (ReportQueryDeadline deadline = new ReportQueryDeadline(statement, System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
             ResultSet rs = deadline.wrap(delegate)) {
            assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(rs::next).isInstanceOf(SQLTimeoutException.class);
        }
        verify(statement).cancel();
        verify(delegate).close();
    }
    @Test
    void shouldCloseCursor_withoutWaitingForSlowDriverCancellation() throws Exception {
        Statement statement = mock(Statement.class);
        ResultSet delegate = mock(ResultSet.class);
        CountDownLatch cancelling = new CountDownLatch(1);
        CountDownLatch releaseCancellation = new CountDownLatch(1);
        doAnswer(ignored -> {
            cancelling.countDown();
            releaseCancellation.await(5, TimeUnit.SECONDS);
            return null;
        }).when(statement).cancel();
        try (ReportQueryDeadline deadline = new ReportQueryDeadline(statement,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
             ResultSet rs = deadline.wrap(delegate)) {
            assertThat(cancelling.await(5, TimeUnit.SECONDS)).isTrue();
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                try {
                    executor.submit(() -> { rs.close(); return null; }).get(1, TimeUnit.SECONDS);
                    verify(delegate).close();
                } finally {
                    releaseCancellation.countDown();
                }
            }
        }
    }

    @Test
    void shouldRejectExpiredDeadline_beforeSchedulingCancellation() {
        assertThatThrownBy(() -> new ReportQueryDeadline(mock(Statement.class), System.nanoTime() - 1))
                .isInstanceOf(SQLTimeoutException.class);
    }

}
