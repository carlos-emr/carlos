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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * The READ lock of the one legacy table that may not be transactional ({@code formRourke2009},
 * Aria or MyISAM in the baseline schema), held on a dedicated unpooled connection only while a
 * capture reads that table. No connection or arbitrary SQL escapes.
 *
 * <p>An InnoDB table is read from the capture's snapshot and needs no lock. A non-transactional
 * table has no snapshot, so its rows are held still by a table READ lock (a <em>pin</em>) from
 * the first statement that reads them to the last: the count, every page and every chunk of one
 * reader's turn see one state of the table. The lock is released when that turn ends
 * ({@link #release}). It is never held for the length of a capture, and it has no deadline: how
 * long a pin lasts follows from the patient's rows in that one table, and a capture of any
 * length finishes. Between pins the clinic writes to the table freely.
 *
 * <p>Consistency. The pinned rows are the table's state at a moment during the capture, not at
 * the instant of the capture's InnoDB snapshot. Nothing is lost by that: the engine has no
 * transactions, so no writer could ever change this table atomically with another, and holding
 * the lock for the whole capture (as before) only chose an earlier moment, with the same gap
 * between it and the snapshot's tables. The capture reads this table by the patient predicate
 * alone and exports each row as its own summary; no value of another table is derived from it.
 * Each presence check is one statement under its own pin: a row that appears between two checks
 * is seen by the later one and holds the patient. The table's structure cannot change while
 * pinned, and a change between pins is caught by the capture's second schema read.
 */
public final class CdsExportSourceReadLock implements AutoCloseable {
    /** The only table of the reviewed profiles that may use a non-transactional engine. */
    public static final String TABLE = "formRourke2009";

    /** Opens the dedicated physical connection a pin locks through. */
    @FunctionalInterface
    public interface Connections {
        Connection open() throws SQLException;
    }

    private static final ThreadLocal<CdsExportSourceReadLock> CURRENT = new ThreadLocal<>();
    private final Connections connections;
    private final Thread owner = Thread.currentThread();
    private Connection connection;
    private long connectionId;
    private boolean pinned;
    private boolean closed;

    private CdsExportSourceReadLock(Connections connections) {
        this.connections = connections;
    }

    /** The guard of a capture that reads no legacy table, or whose legacy table is InnoDB. */
    public static CdsExportSourceReadLock none() {
        return new CdsExportSourceReadLock(null);
    }

    /**
     * The guard of one capture on the calling thread. It reads the legacy table's engine and
     * takes no lock: an InnoDB table needs none, and a non-transactional one is pinned when a
     * reader first reads it.
     */
    public static CdsExportSourceReadLock guard(Connections connections) throws SQLException {
        Objects.requireNonNull(connections);
        if (CURRENT.get() != null)
            throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_SCOPE_INVALID");
        var guard = new CdsExportSourceReadLock(connections);
        try {
            guard.connect();
            if (engine(guard.connection).equals("InnoDB")) {
                guard.discard();
                return none();
            }
        } catch (SQLException | RuntimeException | Error failed) {
            guard.discard();
            throw failed;
        }
        CURRENT.set(guard);
        return guard;
    }

    /**
     * A guard over this one connection, pinned at once and until it is closed. It takes
     * ownership of the connection even when acquisition fails.
     */
    public static CdsExportSourceReadLock acquireUnpooled(Connection connection)
            throws SQLException {
        Objects.requireNonNull(connection);
        boolean[] opened = new boolean[1];
        try {
            var guard =
                    guard(
                            () -> {
                                if (opened[0] || connection.isClosed())
                                    throw new SQLException(
                                            "CDS_EXPORT_SOURCE_READ_LOCK_SCOPE_INVALID");
                                opened[0] = true;
                                return connection;
                            });
            try {
                guard.pin();
            } catch (SQLException | RuntimeException | Error failed) {
                try {
                    guard.close();
                } catch (SQLException cleanup) {
                    failed.addSuppressed(cleanup);
                }
                throw failed;
            }
            return guard;
        } finally {
            // Refused before the connection was taken: it is still this method's to close.
            if (!opened[0]) connection.close();
        }
    }

    /**
     * Takes the table's READ lock, or proves it is still held. The lock waits for a writer as
     * long as the server lets a lock wait ({@code lock_wait_timeout}); no limit of ours ends it.
     */
    public void pin() throws SQLException {
        if (Thread.currentThread() != owner || closed)
            throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_SCOPE_INVALID");
        if (connections == null) return;
        if (pinned) {
            verify();
            return;
        }
        try {
            // The connection idles between pins; one the server has closed since is replaced,
            // which loses nothing because no lock was held on it.
            if (connection != null && (connection.isClosed() || !connection.isValid(5))) discard();
            if (connection == null) connect();
            engine(connection);
            try (var statement = connection.createStatement()) {
                statement.execute("LOCK TABLES `" + TABLE + "` READ");
            }
            pinned = true;
        } catch (SQLException | RuntimeException | Error failed) {
            discard();
            throw failed;
        }
    }

    /**
     * Ends a pin. The lock's connection is checked first: a lock lost while pinned means the
     * rows read under it were not held still, and the capture is refused.
     */
    public void release() throws SQLException {
        if (Thread.currentThread() != owner)
            throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_SCOPE_INVALID");
        if (!pinned) return;
        try {
            verify();
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(5);
                statement.execute("UNLOCK TABLES");
            }
            pinned = false;
        } catch (SQLException | RuntimeException | Error failed) {
            discard();
            throw failed;
        }
    }

    /** Loss of the pinned connection refuses completion; an unpinned guard holds nothing. */
    public void verify() throws SQLException {
        if (Thread.currentThread() != owner || closed)
            throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_SCOPE_INVALID");
        if (!pinned) return;
        if (connection == null || connection.isClosed())
            throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_EXPIRED");
        if (connectionId(connection) != connectionId)
            throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_CONNECTION_CHANGED");
    }

    /**
     * Before a statement reads the non-transactional table: pins it for the calling capture, or
     * proves the pin is still held. Outside a guarded capture the table is not read.
     */
    public static void requirePinned() {
        var guard = CURRENT.get();
        if (guard == null || guard.connections == null)
            throw new CdsExportReviewException(
                    CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED);
        try {
            guard.pin();
        } catch (SQLException failed) {
            throw new CdsExportReviewException(
                    CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED);
        }
    }

    /**
     * After the last statement of one read of the non-transactional table: ends the calling
     * capture's pin, if it holds one. A lock that was lost during the read refuses it.
     */
    public static void releasePinned() {
        var guard = CURRENT.get();
        if (guard == null) return;
        try {
            guard.release();
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
        try {
            release();
        } finally {
            closed = true;
            if (CURRENT.get() == this) CURRENT.remove();
            discard();
        }
    }

    private void connect() throws SQLException {
        var opened = Objects.requireNonNull(connections.open());
        connection = opened;
        if (!opened.getAutoCommit())
            throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_SCOPE_INVALID");
        var policy = opened.unwrap(org.mariadb.jdbc.Connection.class).getContext().getConf();
        // Match the shared bounded-connection policy: a source fence must never
        // move to a replacement host/session after a connection failure.
        if (policy.haMode() != org.mariadb.jdbc.export.HaMode.NONE
                || policy.addresses().size() != 1)
            throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_RECONNECT_REFUSED");
        connectionId = connectionId(opened);
    }

    private static long connectionId(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var rows = statement.executeQuery("select CONNECTION_ID()")) {
                if (!rows.next())
                    throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_CONNECTION_MISSING");
                return rows.getLong(1);
            }
        }
    }

    private static String engine(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var rows =
                    statement.executeQuery(
                            "select ENGINE from information_schema.TABLES where"
                                    + " TABLE_SCHEMA=DATABASE() and TABLE_NAME='"
                                    + TABLE
                                    + "' and TABLE_TYPE='BASE TABLE'")) {
                if (!rows.next())
                    throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_TABLE_MISSING");
                String engine = rows.getString(1);
                if (!"InnoDB".equals(engine) && !"MyISAM".equals(engine) && !"Aria".equals(engine))
                    throw new SQLException("CDS_EXPORT_SOURCE_READ_LOCK_ENGINE_UNSUPPORTED");
                return engine;
            }
        }
    }

    /** Ends the dedicated connection, and with it any lock it still holds. */
    private void discard() {
        var held = connection;
        connection = null;
        pinned = false;
        if (held == null) return;
        try {
            held.abort(Runnable::run);
        } catch (SQLException | RuntimeException failure) {
            // Closed below; a connection that cannot be aborted is reported there.
        }
        try {
            held.close();
        } catch (SQLException cleanup) {
            org.apache.logging.log4j.LogManager.getLogger(CdsExportSourceReadLock.class)
                    .error("CDS_EXPORT_SOURCE_READ_LOCK_RETIREMENT_FAILED");
        }
    }
}
