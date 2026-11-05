/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/** Keeps cancellation armed while Connector/J is consuming a streaming result. */
final class ReportQueryDeadline implements AutoCloseable {
    private final Statement statement;
    private final CompletableFuture<Void> cancellation = new CompletableFuture<>();
    private enum State { OPEN, EXPIRED, CLOSED }
    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);
    private volatile boolean expired;

    ReportQueryDeadline(Statement statement, long deadlineNanos) throws SQLTimeoutException {
        this.statement = statement;
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new SQLTimeoutException("Report query deadline exceeded");
        }
        // CompletableFuture uses the JDK's shared delay scheduler. Its timer only
        // dispatches work: a slow driver cannot delay other reports or hold cleanup.
        cancellation.orTimeout(remainingNanos, TimeUnit.NANOSECONDS).exceptionallyAsync(failure -> {
            expire();
            return null;
        }, Thread::startVirtualThread);
    }

    private void expire() {
        if (state.compareAndSet(State.OPEN, State.EXPIRED)) {
            expired = true;
            try {
                statement.cancel();
            } catch (SQLException e) {
                MiscUtils.getLogger().warn("Could not cancel expired report query", e);
            }
        }
    }

    boolean isExpired() {
        return expired;
    }

    /** Transfers ownership of the cursor and its cancellation timer to the returned cursor. */
    static ResultSet wrap(ResultSet owned, ReportQueryDeadline deadline) {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        deadline.close();
                        try {
                            return method.invoke(owned, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        } finally {
                            LegacyJdbcQuery.unregisterThreadResource((AutoCloseable) proxy);
                        }
                    }
                    if (deadline.expired) {
                        throw new SQLTimeoutException("Report query deadline exceeded");
                    }
                    try {
                        Object result = method.invoke(owned, args);
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
    public void close() {
        state.set(State.CLOSED);
        // Completing the future removes its pending JDK timeout task.
        cancellation.complete(null);
    }
}
