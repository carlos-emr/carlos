/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic.cds.jdbc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.mariadb.jdbc.ServerPreparedStatement;
import org.mariadb.jdbc.client.result.Result;
import org.mariadb.jdbc.client.result.rowdecoder.BinaryRowDecoder;
import org.mariadb.jdbc.client.result.rowdecoder.RowDecoder;

/** Shared strict binary-result adapter for the release's pinned MariaDB Connector/J. */
final class CdsMariaDbBinaryRead {
    private static final MethodHandle ROW_DECODER = rowDecoderAccessor();

    private CdsMariaDbBinaryRead() {}

    static PreparedStatement prepare(Connection owner, String sql) throws SQLException {
        if (ROW_DECODER == null) throw refused("CDS_BINARY_PROTOCOL_INSPECTION_UNAVAILABLE");
        var raw =
                owner.unwrap(org.mariadb.jdbc.Connection.class)
                        .prepareInternal(
                                sql,
                                Statement.NO_GENERATED_KEYS,
                                ResultSet.TYPE_FORWARD_ONLY,
                                ResultSet.CONCUR_READ_ONLY,
                                true);
        try {
            if (!(raw instanceof ServerPreparedStatement))
                throw refused("CDS_BINARY_RESULT_PROTOCOL_REQUIRED");
            // Preparing metadata surfaces unsupported prepares before any execute. The binding
            // bound also excludes Connector/J's oversized-parameter text fallback.
            int parameters = raw.getParameterMetaData().getParameterCount();
            if (parameters < 0 || parameters > 65 * 1024)
                throw refused("CDS_BINARY_PARAMETER_COUNT_UNSUPPORTED");
            return new ReadStatement(raw, owner, parameters).proxy();
        } catch (SQLException | RuntimeException failure) {
            try {
                raw.close();
            } catch (SQLException close) {
                failure.addSuppressed(close);
            }
            throw failure;
        }
    }

    /**
     * Connector/J can retain ServerPreparedStatement's class while privately switching to text
     * decoding. Its pinned 3.5.11 API has no public result-protocol flag. Inspect only the
     * immutable decoder type, never row/credential values; unavailable access or driver drift fails
     * closed.
     */
    private static MethodHandle rowDecoderAccessor() {
        try {
            return MethodHandles.privateLookupIn(Result.class, MethodHandles.lookup())
                    .findGetter(Result.class, "rowDecoder", RowDecoder.class);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    static void requireBinaryResult(ResultSet rows) throws SQLException {
        if (ROW_DECODER == null || !(rows instanceof Result result))
            throw refused("CDS_BINARY_RESULT_PROTOCOL_REQUIRED");
        final RowDecoder decoder;
        try {
            decoder = (RowDecoder) ROW_DECODER.invokeExact(result);
        } catch (Throwable unavailable) {
            throw new SQLException("CDS_BINARY_PROTOCOL_INSPECTION_FAILED", "45000", unavailable);
        }
        if (!(decoder instanceof BinaryRowDecoder))
            throw refused("CDS_BINARY_RESULT_PROTOCOL_REQUIRED");
    }

    private static SQLException refused(String code) {
        return new SQLException(code, "45000");
    }

    private static Object invoke(Method method, Object receiver, Object[] arguments)
            throws Throwable {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "toString" -> "CDS reviewed binary read";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> throw new IllegalArgumentException("CDS_BINARY_OBJECT_METHOD_UNSUPPORTED");
        };
    }

    private static Object wrapper(Object proxy, Method method, Object[] arguments)
            throws SQLException {
        Class<?> requested = (Class<?>) arguments[0];
        boolean matches = requested != null && requested.isInstance(proxy);
        if (method.getName().equals("isWrapperFor")) return matches;
        if (!matches) throw refused("CDS_BINARY_UNWRAP_REFUSED");
        return proxy;
    }

    /**
     * IMP-2: Connector/J applies a query timeout to a server-prepared statement only as a
     * {@code SET STATEMENT max_statement_time=N FOR} prefix on its first prepare, which already
     * happened above (parameter metadata), so {@code setQueryTimeout} alone is a silent no-op and
     * a read blocked on a metadata lock waits up to {@code lock_wait_timeout}. The deadline is
     * enforced client side instead: a daemon timer cancels the statement (Connector/J sends
     * {@code KILL QUERY} on a separate connection) and the read fails with
     * {@link java.sql.SQLTimeoutException}. No session variable is changed.
     */
    private static final java.util.concurrent.ScheduledExecutorService DEADLINES =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                    task -> {
                        var thread = new Thread(task, "cds-binary-read-deadline");
                        thread.setDaemon(true);
                        return thread;
                    });

    private static final class ReadStatement implements InvocationHandler {
        private final PreparedStatement raw;
        private final Connection owner;
        private final int parameters;
        private PreparedStatement proxy;
        private int timeoutSeconds;

        private ReadStatement(PreparedStatement raw, Connection owner, int parameters) {
            this.raw = raw;
            this.owner = owner;
            this.parameters = parameters;
        }

        private PreparedStatement proxy() {
            proxy =
                    (PreparedStatement)
                            Proxy.newProxyInstance(
                                    CdsBinaryReads.class.getClassLoader(),
                                    new Class<?>[] {PreparedStatement.class},
                                    this);
            return proxy;
        }

        @Override
        public Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class)
                return objectMethod(target, method, arguments);
            if (name.equals("unwrap") || name.equals("isWrapperFor"))
                return wrapper(target, method, arguments);
            if (name.equals("getConnection")) return owner;
            if (name.equals("addBatch")
                    || name.contains("Batch")
                    || name.equals("executeUpdate")
                    || name.equals("executeLargeUpdate")
                    || name.startsWith("execute") && method.getParameterCount() != 0)
                throw refused("CDS_BINARY_PREPARED_READ_REQUIRED");
            if (method.getDeclaringClass() == PreparedStatement.class
                    && name.startsWith("set")
                    && arguments != null
                    && arguments.length > 0
                    && arguments[0] instanceof Integer index
                    && (index < 1 || index > parameters))
                throw refused("CDS_BINARY_PARAMETER_INDEX_INVALID");
            if (name.equals("setQueryTimeout")) {
                int seconds = (Integer) arguments[0];
                if (seconds < 0) throw refused("CDS_BINARY_QUERY_TIMEOUT_INVALID");
                timeoutSeconds = seconds;
                return null;
            }
            if (name.equals("getQueryTimeout")) return timeoutSeconds;
            Object returned =
                    name.startsWith("execute") && timeoutSeconds > 0
                            ? bounded(method, arguments)
                            : CdsMariaDbBinaryRead.invoke(method, raw, arguments);
            if (returned instanceof ResultSet rows) return checked(rows);
            if ((name.equals("execute") || name.equals("getMoreResults"))
                    && Boolean.TRUE.equals(returned)) checked(raw.getResultSet());
            return returned;
        }

        /** Runs one execution under the configured deadline; see {@link #DEADLINES}. */
        private Object bounded(Method method, Object[] arguments) throws Throwable {
            var lock = new Object();
            boolean[] running = {true}, cancelled = {false};
            var deadline =
                    DEADLINES.schedule(
                            () -> {
                                synchronized (lock) {
                                    if (!running[0]) return;
                                    cancelled[0] = true;
                                    try {
                                        raw.cancel();
                                    } catch (SQLException | RuntimeException ignored) {
                                        // The execution still fails or completes on its own;
                                        // a cancellation failure is reported by its outcome.
                                    }
                                }
                            },
                            timeoutSeconds,
                            java.util.concurrent.TimeUnit.SECONDS);
            try {
                Object returned = CdsMariaDbBinaryRead.invoke(method, raw, arguments);
                synchronized (lock) {
                    running[0] = false;
                    // A cancelled SLEEP() and some interrupted reads return normally; a read
                    // that reached its deadline is never treated as complete.
                    if (cancelled[0]) {
                        if (returned instanceof ResultSet rows) rows.close();
                        throw new java.sql.SQLTimeoutException("CDS_BINARY_READ_TIMEOUT", "70100");
                    }
                }
                return returned;
            } catch (SQLException failure) {
                if (failure instanceof java.sql.SQLTimeoutException
                        && "CDS_BINARY_READ_TIMEOUT".equals(failure.getMessage())) throw failure;
                synchronized (lock) {
                    if (cancelled[0])
                        throw new java.sql.SQLTimeoutException(
                                "CDS_BINARY_READ_TIMEOUT", "70100", failure);
                }
                throw failure;
            } finally {
                synchronized (lock) {
                    running[0] = false;
                }
                deadline.cancel(false);
            }
        }

        private ResultSet checked(ResultSet rows) throws SQLException {
            try {
                requireBinaryResult(rows);
            } catch (SQLException failure) {
                if (rows != null) {
                    try {
                        rows.close();
                    } catch (SQLException close) {
                        failure.addSuppressed(close);
                    }
                }
                try {
                    raw.close();
                } catch (SQLException close) {
                    failure.addSuppressed(close);
                }
                throw failure;
            }
            return (ResultSet)
                    Proxy.newProxyInstance(
                            CdsBinaryReads.class.getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            (target, method, arguments) -> {
                                if (method.getDeclaringClass() == Object.class)
                                    return objectMethod(target, method, arguments);
                                String name = method.getName();
                                if (name.equals("getStatement")) return proxy;
                                if (name.equals("unwrap") || name.equals("isWrapperFor"))
                                    return wrapper(target, method, arguments);
                                return CdsMariaDbBinaryRead.invoke(method, rows, arguments);
                            });
        }
    }
}
