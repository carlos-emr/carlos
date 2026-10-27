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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportControlDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** Fixed queries on the physical unpooled coordinator; the lease exposes no JDBC capability. */
@Repository
public class CdsImportCoordinatorDao {
    private final OscarTrackingBasicDataSource dataSource;

    public CdsImportCoordinatorDao(@Qualifier("dataSource") OscarTrackingBasicDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** A null result means the physical coordinator is already owned by another process. */
    @NativeSql
    public Lease acquire() throws SQLException {
        Connection connection = dataSource.openUnpooledCdsMaintenanceConnection();
        boolean transferred = false;
        Throwable primaryFailure = null;
        try {
            // This connection is newly created and has performed no work. Each
            // later control SELECT must observe a fresh autocommit snapshot.
            connection.setAutoCommit(true);
            String database;
            try (PreparedStatement query = connection.prepareStatement("SELECT DATABASE()")) {
                query.setQueryTimeout(3);
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next() || (database = rows.getString(1)) == null || rows.next())
                        throw new SQLException("Import database is not selected");
                }
            }
            String name = "cds.coordinator." + CdsImportKeys.hash(database).substring(0, 40);
            long acquired = scalar(connection, "SELECT GET_LOCK(?,0)", name);
            if (acquired == 0) return null;
            if (acquired != 1) throw new SQLException("Invalid coordinator acquisition result");
            Lease result = new Lease(connection, name);
            transferred = true;
            return result;
        } catch (SQLException | RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (!transferred) {
                try { connection.close(); }
                catch (SQLException | RuntimeException | Error cleanup) {
                    if (primaryFailure == null) throw cleanup;
                    if (cleanup != primaryFailure) primaryFailure.addSuppressed(cleanup);
                }
            }
        }
    }

    private static long scalar(Connection connection, String sql, String name) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setQueryTimeout(3);
            query.setString(1, name);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw new SQLException("Missing coordinator response");
                long value = rows.getLong(1);
                if (rows.wasNull() || rows.next()) throw new SQLException("Invalid coordinator response");
                return value;
            }
        }
    }

    /** Only fixed lock and typed control operations are available on an acquired lease. */
    public static final class Lease implements AutoCloseable {
        private static final String CONTROL_SQL = """
                SELECT IS_USED_LOCK(?)=CONNECTION_ID(), r.id, r.owner_epoch,
                       r.cancel_requested, a.owner_run_id
                FROM (SELECT 1) AS control_scope
                LEFT JOIN cds_import_run r ON r.id=?
                LEFT JOIN cds_import_admission a ON a.id='global'
                """;
        private final Connection connection;
        private final String name;
        private PreparedStatement physical;
        private PreparedStatement control;
        private boolean closed;

        private Lease(Connection connection, String name) {
            this.connection = connection;
            this.name = name;
        }

        @NativeSql
        public boolean physicalOwned() throws SQLException {
            requireOpen();
            if (physical == null) {
                physical = connection.prepareStatement("SELECT IS_USED_LOCK(?)=CONNECTION_ID()");
                physical.setQueryTimeout(3);
                physical.setString(1, name);
            }
            try (ResultSet rows = physical.executeQuery()) {
                if (!rows.next()) throw new SQLException("Missing coordinator response");
                boolean owned = flag(rows, 1, true);
                if (rows.next()) throw new SQLException("Duplicate coordinator response");
                return owned;
            }
        }

        @NativeSql({"cds_import_run", "cds_import_admission"})
        public CdsImportControlDto control(String runId) throws SQLException {
            requireOpen();
            if (runId == null || runId.isBlank() || runId.length() > 36)
                throw new IllegalArgumentException("Invalid control run identity");
            if (control == null) {
                control = connection.prepareStatement(CONTROL_SQL);
                control.setQueryTimeout(3);
                control.setString(1, name);
            }
            control.setString(2, runId);
            try (ResultSet rows = control.executeQuery()) {
                if (!rows.next()) throw new SQLException("Missing import control response");
                boolean physicalOwned = flag(rows, 1, true);
                String selectedRun = rows.getString(2);
                long epoch = rows.getLong(3);
                boolean missingEpoch = rows.wasNull();
                boolean cancelRequested = flag(rows, 4, selectedRun == null);
                String admissionOwner = rows.getString(5);
                if (selectedRun != null && (!runId.equals(selectedRun) || missingEpoch || epoch < 0))
                    throw new SQLException("Invalid import control identity");
                if (rows.next()) throw new SQLException("Duplicate import control response");
                return new CdsImportControlDto(physicalOwned, selectedRun != null,
                        selectedRun == null ? -1 : epoch, cancelRequested,
                        selectedRun != null && runId.equals(admissionOwner));
            }
        }

        private static boolean flag(ResultSet rows, int column, boolean allowMissing) throws SQLException {
            int value = rows.getInt(column);
            if (rows.wasNull()) {
                if (allowMissing) return false;
                throw new SQLException("Missing import control flag");
            }
            if (value != 0 && value != 1) throw new SQLException("Invalid import control flag");
            return value == 1;
        }

        private void requireOpen() throws SQLException {
            if (closed) throw new SQLException("Coordinator lease is closed");
        }

        @Override
        @NativeSql
        public void close() throws SQLException {
            if (closed) return;
            closed = true;
            // Every cached statement and the physical connection retire even if
            // releasing a lost coordinator fails. No ordinary pool is involved.
            try (Connection retiring = connection;
                    PreparedStatement retiredPhysical = physical;
                    PreparedStatement retiredControl = control) {
                if (scalar(retiring, "SELECT RELEASE_LOCK(?)", name) != 1)
                    throw new SQLException("Coordinator release failed");
            }
        }
    }
}
