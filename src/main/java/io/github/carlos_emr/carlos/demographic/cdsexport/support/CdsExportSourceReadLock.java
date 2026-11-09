/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * <p>This software is published under the GPL GNU General Public License. This program is free
 * software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * <p>CARLOS EMR Project https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.support;

import java.sql.*;
import java.util.Objects;

/**
 * One fixed legacy table's bounded READ lock, on an owned unpooled connection; no connection or
 * arbitrary SQL escapes.
 */
public final class CdsExportSourceReadLock implements AutoCloseable {
    private static final ThreadLocal<CdsExportSourceReadLock> CURRENT = new ThreadLocal<>();
    private final Connection connection;
    private final Thread owner = Thread.currentThread();
    private final long started = System.nanoTime();
    private long connectionId;
    private boolean locked;
    private volatile boolean closed, expired;
    private long maximumMillis = 150_000;
    private java.util.Timer deadline;

    private CdsExportSourceReadLock(Connection connection) {
        this.connection = connection;
    }

    public static CdsExportSourceReadLock none() {
        return new CdsExportSourceReadLock(null);
    }

    /** Takes ownership of this dedicated physical connection even when acquisition fails. */
    public static CdsExportSourceReadLock acquireUnpooled(Connection connection)
            throws SQLException {
        return acquireUnpooled(connection, 150_000);
    }

    /** A shorter caller budget may tighten, never extend, the fixed source-capture ceiling. */
    public static CdsExportSourceReadLock acquireUnpooled(Connection connection, long maximumMillis)
            throws SQLException {
        var lease = new CdsExportSourceReadLock(Objects.requireNonNull(connection));
        try {
            if (maximumMillis < 1 || maximumMillis > 150_000)
                throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_BUDGET_INVALID");
            lease.maximumMillis = maximumMillis;
            if (CURRENT.get() != null || !connection.getAutoCommit())
                throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_SCOPE_INVALID");
            var driver = connection.unwrap(org.mariadb.jdbc.Connection.class);
            var policy = driver.getContext().getConf();
            // Match the shared bounded-connection policy: a source fence must never
            // move to a replacement host/session after a connection failure.
            if (policy.haMode() != org.mariadb.jdbc.export.HaMode.NONE
                    || policy.addresses().size() != 1)
                throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_RECONNECT_REFUSED");
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(5);
                try (var rows = statement.executeQuery("select CONNECTION_ID()")) {
                    if (!rows.next())
                        throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_CONNECTION_MISSING");
                    lease.connectionId = rows.getLong(1);
                }
                try (var rows =
                        statement.executeQuery(
                                "select ENGINE from information_schema.TABLES where"
                                    + " TABLE_SCHEMA=DATABASE() and TABLE_NAME='formRourke2009' and"
                                    + " TABLE_TYPE='BASE TABLE'")) {
                    if (!rows.next())
                        throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_TABLE_MISSING");
                    String engine = rows.getString(1);
                    if (!engine.equals("InnoDB")
                            && !engine.equals("MyISAM")
                            && !engine.equals("Aria"))
                        throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_ENGINE_UNSUPPORTED");
                    if (!engine.equals("InnoDB")) lease.locked = true;
                }
                if (lease.locked) statement.execute("LOCK TABLES `formRourke2009` READ");
            }
            if (lease.locked) CURRENT.set(lease);
            lease.deadline = new java.util.Timer("CDS export legacy source deadline", true);
            lease.deadline.schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            try {
                                lease.expire();
                            } finally {
                                lease.deadline.cancel();
                            }
                        }
                    },
                    maximumMillis);
            lease.verify();
            return lease;
        } catch (SQLException | RuntimeException | Error failed) {
            try {
                lease.retire();
            } catch (SQLException cleanup) {
                failed.addSuppressed(cleanup);
            }
            throw failed;
        }
    }

    /** Both loss of the original connection and an overlong source snapshot refuse completion. */
    public void verify() throws SQLException {
        if (Thread.currentThread() != owner || closed)
            throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_SCOPE_INVALID");
        if (connection == null) return;
        if (expired
                || System.nanoTime() - started > maximumMillis * 1_000_000L
                || connection.isClosed())
            throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_EXPIRED");
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var rows = statement.executeQuery("select CONNECTION_ID()")) {
                if (!rows.next() || rows.getLong(1) != connectionId)
                    throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_CONNECTION_CHANGED");
            }
        }
    }

    public static void requirePinned() {
        var lease = CURRENT.get();
        if (lease == null || !lease.locked)
            throw new CdsExportReviewException(
                    CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED);
        try {
            lease.verify();
        } catch (SQLException failed) {
            throw new CdsExportReviewException(
                    CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED);
        }
    }

    @Override
    public void close() throws SQLException {
        if (closed) return;
        if (Thread.currentThread() != owner)
            throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_SCOPE_INVALID");
        SQLException primary = null;
        try {
            verify();
            if (locked)
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(5);
                    statement.execute("UNLOCK TABLES");
                }
        } catch (SQLException failed) {
            primary = failed;
            try {
                if (connection != null) connection.abort(Runnable::run);
            } catch (SQLException cleanup) {
                failed.addSuppressed(cleanup);
            }
        } finally {
            closed = true;
            if (deadline != null) deadline.cancel();
            if (CURRENT.get() == this) CURRENT.remove();
            if (connection != null)
                try {
                    connection.close();
                } catch (SQLException cleanup) {
                    if (primary == null) primary = cleanup;
                    else primary.addSuppressed(cleanup);
                }
        }
        if (primary != null) throw primary;
    }

    /**
     * A stalled capture cannot keep the legacy table locked after its independent wall-clock
     * budget.
     */
    private void expire() {
        if (closed) return;
        expired = true;
        try {
            connection.abort(Runnable::run);
        } catch (SQLException failure) {
            try {
                connection.close();
            } catch (SQLException cleanup) {
                org.apache.logging.log4j.LogManager.getLogger(CdsExportSourceReadLock.class)
                        .error("CDS_EXPORT_SOURCE_READ_LOCK_RETIREMENT_FAILED");
            }
        }
    }

    private void retire() throws SQLException {
        closed = true;
        if (deadline != null) deadline.cancel();
        if (CURRENT.get() == this) CURRENT.remove();
        try {
            connection.abort(Runnable::run);
        } finally {
            connection.close();
        }
    }
}
