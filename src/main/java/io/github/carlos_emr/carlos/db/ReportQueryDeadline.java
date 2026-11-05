/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/** Keeps connection interruption armed while Connector/J consumes a streaming result. */
final class ReportQueryDeadline implements AutoCloseable {
    private final Connection connection;
    private final CompletableFuture<Void> cancellation = new CompletableFuture<>();
    private CompletableFuture<Void> aborted;
    private boolean closed;
    private volatile boolean expired;

    ReportQueryDeadline(Connection connection, long deadlineNanos) throws SQLTimeoutException {
        this.connection = connection;
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new SQLTimeoutException("Report query deadline exceeded");
        }
        // The shared timer only dispatches work; driver calls run on a virtual thread.
        cancellation.orTimeout(remainingNanos, TimeUnit.NANOSECONDS).exceptionallyAsync(failure -> {
            expire();
            return null;
        }, Thread::startVirtualThread);
    }

    private synchronized void expire() {
        if (!closed) {
            expired = true;
            abort();
        }
    }

    private void abort() {
        if (aborted == null) {
            aborted = CompletableFuture.runAsync(() -> {
                try {
                    // Connector/J Statement.cancel() is a no-op after executeQuery().
                    // abort closes its socket without waiting for the streaming read lock.
                    connection.abort(Runnable::run);
                } catch (SQLException | RuntimeException e) {
                    MiscUtils.getLogger().warn("Could not abort report connection", e);
                }
            }, Thread::startVirtualThread);
        }
    }

    boolean isExpired() {
        return expired;
    }

    private synchronized CompletableFuture<Void> stop(boolean discard) {
        if (!closed && discard) {
            abort();
        }
        close();
        return aborted;
    }

    /** Aborts failed execution before releasing its JDBC resources, without blocking the request. */
    void discard(Runnable cleanup) {
        stop(true).thenRunAsync(cleanup, Thread::startVirtualThread);
    }

    /** Transfers ownership of the cursor and its cancellation timer to the returned cursor. */
    static ResultSet wrap(ResultSet owned, ReportQueryDeadline deadline) {
        AtomicBoolean released = new AtomicBoolean();
        AtomicBoolean exhausted = new AtomicBoolean();
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        if (!released.compareAndSet(false, true)) {
                            return null;
                        }
                        try {
                            CompletableFuture<Void> abort = deadline.stop(!exhausted.get());
                            if (abort == null) {
                                owned.close();
                            } else {
                                // Never drain unread rows or return the connection to its pool
                                // before abort finishes. Slow driver cleanup cannot stall refusal.
                                abort.thenRunAsync(() -> {
                                    try {
                                        owned.close();
                                    } catch (SQLException | RuntimeException e) {
                                        MiscUtils.getLogger().warn("Could not close aborted report resources", e);
                                    }
                                }, Thread::startVirtualThread);
                            }
                            return null;
                        } finally {
                            LegacyJdbcQuery.unregisterThreadResource((AutoCloseable) proxy);
                        }
                    }
                    if (deadline.expired) {
                        throw new SQLTimeoutException("Report query deadline exceeded");
                    }
                    try {
                        Object result = method.invoke(owned, args);
                        if ("next".equals(method.getName()) && Boolean.FALSE.equals(result)) {
                            exhausted.set(true);
                        }
                        if (deadline.expired) {
                            throw new SQLTimeoutException("Report query deadline exceeded");
                        }
                        return result;
                    } catch (InvocationTargetException e) {
                        if (deadline.expired && e.getCause() instanceof SQLException) {
                            throw new SQLTimeoutException("Report query deadline exceeded", e.getCause());
                        }
                        throw e.getCause();
                    }
                });
    }

    @Override
    public synchronized void close() {
        closed = true;
        // Completing the future removes its pending JDK timeout task.
        cancellation.complete(null);
    }
}
