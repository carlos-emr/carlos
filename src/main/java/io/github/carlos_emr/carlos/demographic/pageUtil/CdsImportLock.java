/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Serializes CDS uploads to one database, including requests handled by different application instances.
 * The import uses several independently committed DAO calls, so a transaction-scoped row lock cannot
 * protect its duplicate lookup through insertion. This MariaDB advisory lock uses a dedicated connection
 * and is explicitly released before that connection returns to the pool.
 */
final class CdsImportLock implements AutoCloseable {
    static final String ACQUIRE_SQL = "SELECT GET_LOCK(CONCAT('carlos-cds-import-', MD5(DATABASE())), 30)";
    static final String RELEASE_SQL = "SELECT RELEASE_LOCK(CONCAT('carlos-cds-import-', MD5(DATABASE())))";
    private final Connection connection;
    private boolean closed;

    private CdsImportLock(Connection connection) {
        this.connection = connection;
    }

    /** Returns null if another import still owns the database lock after thirty seconds. */
    static CdsImportLock acquire(DataSource dataSource) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            int result = query(connection, ACQUIRE_SQL);
            if (result == 1) return new CdsImportLock(connection);
            if (result != 0) throw new SQLException("Unexpected CDS import lock result");
            connection.close();
            return null;
        } catch (SQLException failure) {
            // The server may have acquired the lock even if reading its reply failed.
            abort(connection, failure);
            try { connection.close(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static int query(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) throw new SQLException("Missing CDS import lock result");
            int value = result.getInt(1);
            if (result.wasNull()) throw new SQLException("CDS import lock operation failed");
            return value;
        }
    }

    @Override
    public void close() throws SQLException {
        if (closed) return;
        closed = true;
        try (connection) {
            try {
                if (query(connection, RELEASE_SQL) != 1) throw new SQLException("CDS import lock was not released");
            } catch (SQLException failure) {
                // Never return a session with an uncertain advisory lock to the connection pool.
                abort(connection, failure);
                throw failure;
            }
        }
    }

    private static void abort(Connection connection, SQLException failure) {
        try { connection.abort(Runnable::run); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
    }
}
