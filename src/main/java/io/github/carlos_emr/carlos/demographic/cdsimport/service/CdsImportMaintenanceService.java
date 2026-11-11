/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionState;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionState.Lease;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Acquires one database-wide drained maintenance window. The advisory connection is physical,
 * unpooled and retained until Owner.close. A durable active flag survives connection/process loss.
 * Ordinary transactions retain shared row locks through their real commit; entering/reopening
 * maintenance exclusively locks that same row, so old admitted transactions must finish first.
 */
@Service
public class CdsImportMaintenanceService {
    private final OscarTrackingBasicDataSource dataSource;
    private final CdsImportConfigurationService configuration;

    public CdsImportMaintenanceService(@Qualifier("dataSource") OscarTrackingBasicDataSource dataSource,
            CdsImportConfigurationService configuration) {
        this.dataSource = dataSource;
        this.configuration = configuration;
    }

    @FunctionalInterface
    public interface IoRead<T> { T get() throws java.io.IOException; }

    /** Explicit operational retry result; this never changes a completed clinical run. */
    public enum ReleaseStatus { RELEASED, GLOBAL_HOLDS_REMAIN, OWNER_BUSY, REQUIRES_RECONCILIATION }

    /**
     * Retries only the operational release boundary. A fresh physical owner and verified target
     * catalogue are required; another run's held chart retains maintenance without blaming this run.
     * RELEASED includes a committed row readback before relinquishing the advisory connection.
     */
    public ReleaseStatus reconcileCompletedRelease(CdsImportTargetSafetyService safety) {
        Owner owner = null;
        try {
            owner = acquire();
            safety.verify(owner);
            owner.allowReopen();
            owner.close();
            return owner.releaseVerified ? ReleaseStatus.RELEASED : ReleaseStatus.GLOBAL_HOLDS_REMAIN;
        } catch (CdsImportException failed) {
            return "MAINTENANCE_OWNER_BUSY".equals(failed.getCode())
                    || "LOCAL_MAINTENANCE_OWNER_BUSY".equals(failed.getCode())
                    ? ReleaseStatus.OWNER_BUSY : ReleaseStatus.REQUIRES_RECONCILIATION;
        } finally {
            if (owner != null && !owner.closed) {
                try { owner.close(); }
                catch (CdsImportException retained) { /* The durable active flag remains fail closed. */ }
            }
        }
    }

    /** Must surround ledger ownership and every clinical unit; never call inside a Spring transaction. */
    public Owner acquire() {
        configuration.requireEnabled();
        if (!Boolean.parseBoolean(configuration.value("cds.import.maintenanceMode", "false"))) {
            throw new CdsImportException("MAINTENANCE_MODE_NOT_ACTIVATED");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CdsImportException("MAINTENANCE_REQUIRES_NO_TRANSACTION");
        }
        CdsJdbcAdmissionState state = dataSource.getCdsAdmissionState();
        if (state == null) throw new CdsImportException("MAINTENANCE_GUARD_NOT_READY");
        Connection connection = null;
        boolean locked = false;
        try {
            connection = dataSource.openUnpooledCdsMaintenanceConnection();
            io.github.carlos_emr.carlos.utility.CdsJdbcConnectionTimeouts
                    .retainOwnerForDuration(connection, configuration.limits().maxDurationMillis());
            // Keep transaction-duration DDL pins without a run-long consistent data read view.
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            if (scalar(connection, "SELECT GET_LOCK(?, 0)", state.lockName()) != 1) {
                throw new CdsImportException("MAINTENANCE_OWNER_BUSY");
            }
            locked = true;
            long id = scalar(connection, "SELECT CONNECTION_ID()", null);
            connection.setAutoCommit(false);
            long generation;
            try (PreparedStatement gate = connection.prepareStatement(
                    "SELECT maintenance_generation FROM cds_import_admission WHERE id=? FOR UPDATE")) {
                gate.setQueryTimeout(Math.toIntExact(configuration.number("cds.import.drainTimeoutSeconds", 30)));
                gate.setString(1, CdsJdbcAdmissionState.ROW);
                try (ResultSet rows = gate.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Maintenance admission row is missing", "45000");
                    generation = Math.addExact(rows.getLong(1), 1);
                }
            }
            String token = UUID.randomUUID().toString();
            try (PreparedStatement activate = connection.prepareStatement("""
                    UPDATE cds_import_admission SET maintenance_active=TRUE, maintenance_generation=?,
                    maintenance_owner_token=?, maintenance_connection_id=? WHERE id=?
                    """)) {
                activate.setLong(1, generation); activate.setString(2, token); activate.setLong(3, id);
                activate.setString(4, CdsJdbcAdmissionState.ROW);
                if (activate.executeUpdate() != 1) throw new SQLException("Maintenance admission activation failed", "45000");
            }
            connection.commit();
            connection.setAutoCommit(true);
            Lease lease = new Lease(token, generation, id);
            if (!state.install(lease)) throw new CdsImportException("LOCAL_MAINTENANCE_OWNER_BUSY");
            return new Owner(connection, state, lease);
        } catch (SQLException | RuntimeException failure) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                if (locked) {
                    try { scalar(connection, "SELECT RELEASE_LOCK(?)", state.lockName()); }
                    catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                }
                try { connection.close(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
            }
            if (failure instanceof CdsImportException domain) throw domain;
            throw new CdsImportException("MAINTENANCE_ACQUISITION_FAILED", false, failure);
        }
    }

    /** Reads durable maintenance/holds without touching clinical charts; permitted for status/login filters. */
    public boolean isClinicalAccessBlocked() {
        if (dataSource.getCdsAdmissionState() == null) return false;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement("""
                    SELECT maintenance_active OR EXISTS (SELECT 1 FROM cds_import_patient WHERE held=TRUE)
                        OR IS_USED_LOCK(?) IS NOT NULL
                    FROM cds_import_admission WHERE id=?
                    """)) {
            query.setString(1, dataSource.getCdsAdmissionState().lockName());
            query.setString(2, CdsJdbcAdmissionState.ROW); query.setQueryTimeout(3);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw new SQLException("Maintenance admission row is missing", "45000");
                return rows.getBoolean(1);
            }
        } catch (SQLException failure) { throw new CdsImportException("MAINTENANCE_STATUS_UNAVAILABLE", false, failure); }
    }

    private static long scalar(Connection connection, String sql, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(3);
            if (value != null) statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Maintenance lock result is missing", "45000");
                long result = rows.getLong(1);
                return rows.wasNull() ? -1 : result;
            }
        }
    }

    /** The dedicated connection is never returned to a pool or reclaimed by abandoned-pool cleanup. */
    public final class Owner implements AutoCloseable {
        private final Connection connection;
        private final CdsJdbcAdmissionState state;
        private final Lease lease;
        private boolean closed;
        private boolean reopenRequested;
        private boolean releaseVerified;
        private Owner(Connection connection, CdsJdbcAdmissionState state, Lease lease) {
            this.connection = connection; this.state = state; this.lease = lease;
        }
        /** Dedicated metadata pins share this owner's lifetime, without exposing a raw connection. */
        synchronized void verifyTargets(CdsImportTargetSafetyService verifier) {
            if (closed) throw new CdsImportException("MAINTENANCE_OWNER_CLOSED");
            try {
                if (scalar(connection, "SELECT IS_USED_LOCK(?)", state.lockName())!=lease.connectionId()) {
                    throw new CdsImportException("MAINTENANCE_OWNER_FENCED");
                }
            } catch (SQLException unavailable) { throw new CdsImportException("MAINTENANCE_OWNER_FENCED"); }
            state.clearCatalogueVerification(lease);
            verifier.verifyAndPin(connection);
            state.catalogueVerified(lease);
        }

        public long generation() { return lease.generation(); }
        /** Operational connection identifier, also used for controlled lost-owner fault injection. */
        public long connectionId() { return lease.connectionId(); }
        /** Exercise the physical lease at the same bounded cadence as durable coordinator polling. */
        public synchronized void heartbeat() {
            if (closed) throw new CdsImportException("MAINTENANCE_OWNER_CLOSED");
            try {
                if (scalar(connection, "SELECT IS_USED_LOCK(?)", state.lockName()) != lease.connectionId())
                    throw new CdsImportException("MAINTENANCE_OWNER_FENCED");
            } catch (SQLException unavailable) {
                throw new CdsImportException("MAINTENANCE_OWNER_FENCED", false, unavailable);
            }
        }
        /** True only after this owner closed and proved committed reopening under its advisory lock. */
        public synchronized boolean releaseVerified() { return closed && releaseVerified; }

        /** Short current-read proof for a run-locked recovery claim; the catalogue remains pinned by this owner. */
        synchronized void requireVerifiedRecoveryOwner() {
            if (closed || !state.hasVerifiedCatalogue(lease)) throw new CdsImportException("MAINTENANCE_RECOVERY_OWNER_REQUIRED");
            try {
                if (scalar(connection, "SELECT IS_USED_LOCK(?)", state.lockName())!=lease.connectionId()) {
                    throw new CdsImportException("MAINTENANCE_OWNER_FENCED");
                }
                try (PreparedStatement query=connection.prepareStatement("""
                        SELECT maintenance_active,maintenance_generation,maintenance_owner_token,maintenance_connection_id
                        FROM cds_import_admission WHERE id=? LOCK IN SHARE MODE
                        """)) {
                    query.setQueryTimeout(3); query.setString(1, CdsJdbcAdmissionState.ROW);
                    try (ResultSet rows=query.executeQuery()) {
                        if (!rows.next() || !rows.getBoolean(1) || rows.getLong(2)!=lease.generation()
                                || !lease.token().equals(rows.getString(3)) || rows.getLong(4)!=lease.connectionId()) {
                            throw new CdsImportException("MAINTENANCE_OWNER_FENCED");
                        }
                    }
                }
            } catch (SQLException unavailable) { throw new CdsImportException("MAINTENANCE_OWNER_FENCED", false, unavailable); }
        }

        /** Bounded coordinator read capability; SQL writes still require the active clinical-unit marker. */
        public synchronized <T> T read(java.util.function.Supplier<T> work) {
            if (closed) throw new IllegalStateException("Maintenance owner is already closed");
            return state.read(lease, work);
        }

        /** Checked-I/O coordinator work receives the same read-only capability, with no added DB transaction. */
        public synchronized <T> T readIo(IoRead<T> work) throws java.io.IOException {
            try {
                return read(()-> {
                    try { return work.get(); }
                    catch (java.io.IOException failed) { throw new java.io.UncheckedIOException(failed); }
                });
            } catch (java.io.UncheckedIOException failed) { throw failed.getCause(); }
        }

        /** Explicit successful clinical reconciliation/backlog-policy boundary; errors never imply reopening. */
        public synchronized void allowReopen() {
            if (closed) throw new IllegalStateException("Maintenance owner is already closed");
            reopenRequested = true;
        }

        /** Held/incomplete charts keep the durable window closed for recovery after this owner exits. */
        @Override public synchronized void close() {
            if (closed) return;
            closed = true;
            state.release(lease); // Stop admitting new units while previously admitted transactions drain.
            SQLException failure = null;
            try {
                if (scalar(connection, "SELECT IS_USED_LOCK(?)", state.lockName()) != lease.connectionId()) {
                    throw new SQLException("Maintenance advisory ownership was lost", "45000");
                }
                connection.setAutoCommit(false);
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT maintenance_owner_token FROM cds_import_admission WHERE id=? FOR UPDATE")) {
                    lock.setQueryTimeout(30); lock.setString(1, CdsJdbcAdmissionState.ROW);
                    try (ResultSet rows = lock.executeQuery()) {
                        if (!rows.next() || !lease.token().equals(rows.getString(1))) {
                            throw new SQLException("Maintenance owner was fenced", "45000");
                        }
                    }
                }
                boolean held;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT id FROM cds_import_patient WHERE held=TRUE LIMIT 1 LOCK IN SHARE MODE")) {
                    // Current locking read: the metadata-pin transaction can predate imported holds.
                    query.setQueryTimeout(30);
                    try (ResultSet rows = query.executeQuery()) { held = rows.next(); }
                }
                if (reopenRequested && !held) {
                    try (PreparedStatement reopen = connection.prepareStatement("""
                            UPDATE cds_import_admission SET maintenance_active=FALSE,
                            maintenance_owner_token=NULL, maintenance_connection_id=NULL WHERE id=?
                            """)) {
                        reopen.setString(1, CdsJdbcAdmissionState.ROW); reopen.executeUpdate();
                    }
                }
                connection.commit();
                if (reopenRequested && !held) {
                    // The advisory lock is still ours. A different node cannot acquire a new
                    // maintenance generation between this commit and its proof readback.
                    connection.setAutoCommit(true);
                    try (PreparedStatement proof = connection.prepareStatement("""
                            SELECT maintenance_active,maintenance_generation,maintenance_owner_token,
                                maintenance_connection_id,EXISTS(SELECT 1 FROM cds_import_patient WHERE held=TRUE)
                            FROM cds_import_admission WHERE id=?
                            """)) {
                        proof.setQueryTimeout(3); proof.setString(1, CdsJdbcAdmissionState.ROW);
                        try (ResultSet rows=proof.executeQuery()) {
                            if (!rows.next() || rows.getBoolean(1) || rows.getLong(2)!=lease.generation()
                                    || rows.getString(3)!=null || rows.getObject(4)!=null || rows.getBoolean(5)) {
                                throw new SQLException("Maintenance release readback did not prove reopening", "45000");
                            }
                        }
                    }
                    releaseVerified=true;
                }
            } catch (SQLException problem) {
                failure = problem;
                try { connection.rollback(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
            } finally {
                state.release(lease);
                try { requireAdvisoryRelease(connection, state.lockName()); }
                catch (SQLException cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
                try { connection.close(); }
                catch (SQLException cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
            }
            if (failure != null) {
                releaseVerified=false; // A retained/uncertain advisory connection can still deny ordinary access.
                throw new CdsImportException("MAINTENANCE_RELEASE_REQUIRES_RECONCILIATION", false, failure);
            }
        }
    }
    private static void requireAdvisoryRelease(Connection connection, String lockName) throws SQLException {
        if (scalar(connection, "SELECT RELEASE_LOCK(?)", lockName) != 1) {
            throw new SQLException("Maintenance advisory release was not acknowledged", "45000");
        }
    }

}
