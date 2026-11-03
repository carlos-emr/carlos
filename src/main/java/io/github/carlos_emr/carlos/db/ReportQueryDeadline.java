/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/** Keeps cancellation armed while Connector/J is consuming a streaming result. */
final class ReportQueryDeadline implements AutoCloseable {
    private static final ScheduledThreadPoolExecutor TIMER = timer();
    private final Statement statement;
    private final ScheduledFuture<?> cancellation;
    private boolean closed;
    private volatile boolean expired;

    ReportQueryDeadline(Statement statement, int seconds) {
        this.statement = statement;
        // A slow driver cancellation must not delay other reports' deadlines.
        cancellation = TIMER.schedule(() -> Thread.startVirtualThread(this::expire), seconds, TimeUnit.SECONDS);
    }

    private static ScheduledThreadPoolExecutor timer() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "report-query-deadline");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setKeepAliveTime(1, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private synchronized void expire() {
        if (!closed) {
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

    ResultSet wrap(ResultSet owned) {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        close();
                        try {
                            return method.invoke(owned, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        } finally {
                            LegacyJdbcQuery.unregisterThreadResource((AutoCloseable) proxy);
                        }
                    }
                    if (expired) {
                        throw new SQLTimeoutException("Report query deadline exceeded");
                    }
                    try {
                        Object result = method.invoke(owned, args);
                        if (expired) {
                            throw new SQLTimeoutException("Report query deadline exceeded");
                        }
                        return result;
                    } catch (InvocationTargetException e) {
                        if (expired && e.getCause() instanceof SQLException) {
                            throw new SQLTimeoutException("Report query deadline exceeded", e.getCause());
                        }
                        throw e.getCause();
                    }
                });
    }

    @Override
    public synchronized void close() {
        closed = true;
        cancellation.cancel(false);
    }
}
