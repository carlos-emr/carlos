/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.support;

/**
 * The database could not be reached for an export's read, and stayed unreachable for every
 * restart the read is allowed. It is a failure of a resource, not a limit: nothing is published
 * or truncated, and the job can be resumed once the database answers again.
 *
 * <p>{@link #lost(Throwable)} recognises the one condition a restart can cure: the connection
 * that held a read was lost (closed, reset, refused or silent past the server's own deadline for
 * the statement). A statement the server answered with an error, a refusal of the data and a
 * deadline of the transaction itself are not connection losses and are never restarted.</p>
 */
public final class CdsExportDatabaseUnavailableException extends IllegalStateException {
    /** The code recorded for the job; the only text this exception ever carries. */
    public static final String CODE = "CDS_EXPORT_DATABASE_UNAVAILABLE";

    public CdsExportDatabaseUnavailableException(Throwable cause) {
        super(CODE, cause);
    }

    /**
     * Whether a failure is the loss of a database connection, anywhere in its cause chain: the
     * JDBC connection exceptions (which is how the driver reports a closed, reset or silent
     * socket), SQLState class 08, a transaction that could not open its connection, and the
     * capture's own table lock found gone or moved to another session. A socket failure that no
     * database exception carries (a web service, a file) is not one. Only types, SQLStates and
     * fixed codes are read; never a message that could hold a value.
     */
    public static boolean lost(Throwable failure) {
        var seen = new java.util.HashSet<Throwable>();
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof CdsExportDatabaseUnavailableException
                    || cause instanceof java.sql.SQLNonTransientConnectionException
                    || cause instanceof java.sql.SQLTransientConnectionException
                    || cause instanceof java.sql.SQLRecoverableException
                    || cause
                            instanceof
                            org.springframework.transaction.CannotCreateTransactionException
                    || cause
                            instanceof
                            org.springframework.dao.DataAccessResourceFailureException
                    || cause instanceof org.hibernate.exception.JDBCConnectionException)
                return true;
            if (cause instanceof java.sql.SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && state.startsWith("08")) return true;
                String code = sql.getMessage();
                if ("CDS_EXPORT_SOURCE_READ_LOCK_EXPIRED".equals(code)
                        || "CDS_EXPORT_SOURCE_READ_LOCK_CONNECTION_CHANGED".equals(code))
                    return true;
            }
        }
        return false;
    }
}
