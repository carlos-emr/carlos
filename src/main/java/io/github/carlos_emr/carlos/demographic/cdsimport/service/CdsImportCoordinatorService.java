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

import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** One physical unpooled coordinator excludes duplicate preflight as well as execution across nodes. */
@Service
public class CdsImportCoordinatorService {
    private final OscarTrackingBasicDataSource dataSource;
    public CdsImportCoordinatorService(@Qualifier("dataSource") OscarTrackingBasicDataSource dataSource) { this.dataSource=dataSource; }
    public Owner acquire() {
        CdsImportLedgerService.requireNoTransaction();
        Connection connection=null;
        try {
            connection=dataSource.openUnpooledCdsMaintenanceConnection();
            String database;
            try (PreparedStatement query=connection.prepareStatement("SELECT DATABASE()"); ResultSet rows=query.executeQuery()) {
                if (!rows.next() || (database=rows.getString(1))==null) throw new SQLException("Import database is not selected");
            }
            String name="cds.coordinator."+io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys.hash(database).substring(0, 40);
            if (scalar(connection, "SELECT GET_LOCK(?,0)", name)!=1) throw new CdsImportException("IMPORT_COORDINATOR_BUSY");
            return new Owner(connection, name);
        } catch (SQLException | RuntimeException failure) {
            if (connection!=null) try { connection.close(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
            if (failure instanceof CdsImportException known) throw known;
            throw new CdsImportException("IMPORT_COORDINATOR_UNAVAILABLE", false, failure);
        }
    }
    private static long scalar(Connection connection, String sql, String name) throws SQLException {
        try (PreparedStatement query=connection.prepareStatement(sql)) {
            query.setQueryTimeout(3); query.setString(1, name);
            try (ResultSet rows=query.executeQuery()) { if (!rows.next()) throw new SQLException("Missing coordinator lock response"); return rows.getLong(1); }
        }
    }
    public static final class Owner implements AutoCloseable {
        private final Connection connection; private final String name; private boolean closed;
        private Owner(Connection connection, String name) { this.connection=connection; this.name=name; }
        public void check() {
            if (closed) throw new CdsImportException("IMPORT_COORDINATOR_LOST");
            try (PreparedStatement query=connection.prepareStatement("SELECT IS_USED_LOCK(?)=CONNECTION_ID()")) {
                query.setQueryTimeout(3); query.setString(1, name);
                try (ResultSet rows=query.executeQuery()) { if (!rows.next() || !rows.getBoolean(1)) throw new CdsImportException("IMPORT_COORDINATOR_LOST"); }
            } catch (SQLException failure) { throw new CdsImportException("IMPORT_COORDINATOR_LOST", false, failure); }
        }
        @Override public void close() {
            if (closed) return; closed=true;
            try { scalar(connection, "SELECT RELEASE_LOCK(?)", name); }
            catch (SQLException failure) { throw new CdsImportException("IMPORT_COORDINATOR_RELEASE_FAILED", false, failure); }
            finally { try { connection.close(); } catch (SQLException failure) { throw new CdsImportException("IMPORT_COORDINATOR_RELEASE_FAILED", false, failure); } }
        }
    }
}
