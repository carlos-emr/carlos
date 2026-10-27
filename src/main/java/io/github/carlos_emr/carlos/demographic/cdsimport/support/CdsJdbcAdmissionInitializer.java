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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** JDBC-only startup ordering: Flyway, admission schema/engine verification, then Hibernate. */
public final class CdsJdbcAdmissionInitializer {
    private final OscarTrackingBasicDataSource dataSource;
    private final boolean enabled;
    private final String schemaMode;
    public CdsJdbcAdmissionInitializer(OscarTrackingBasicDataSource dataSource, boolean enabled, String schemaMode) {
        this.dataSource = dataSource; this.enabled = enabled; this.schemaMode = schemaMode;
    }
    public void initialize() throws SQLException {
        if (enabled && !"validate".equals(schemaMode) && !"migrate".equals(schemaMode)) {
            throw new IllegalStateException("CDS maintenance requires the Flyway startup schema gate");
        }
        try (Connection connection = dataSource.openUnpooledCdsMaintenanceConnection()) {
            try (PreparedStatement engine = connection.prepareStatement("""
                    SELECT ENGINE FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cds_import_admission'
                    """); ResultSet rows = engine.executeQuery()) {
                if (!rows.next()) {
                    if (!enabled) return; // Existing databases before this feature retain their normal startup.
                    throw new IllegalStateException("CDS admission migration is missing");
                }
                if (!"InnoDB".equals(rows.getString(1))) {
                    throw new IllegalStateException("CDS admission requires its migrated InnoDB table");
                }
            }
            try (PreparedStatement gate = connection.prepareStatement("""
                    SELECT maintenance_active OR EXISTS (SELECT 1 FROM cds_import_patient WHERE held=TRUE)
                        OR IS_USED_LOCK(?) IS NOT NULL,
                        maintenance_generation,maintenance_owner_token,maintenance_connection_id
                    FROM cds_import_admission WHERE id=?
                    """)) {
                gate.setString(1, new CdsJdbcAdmissionState(connection.getCatalog()).lockName());
                gate.setString(2, CdsJdbcAdmissionState.ROW);
                try (ResultSet rows = gate.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("CDS maintenance admission row is missing");
                    if (!enabled && rows.getBoolean(1)) {
                        throw new IllegalStateException("CDS maintenance/holds require an enabled admission guard before this node can start");
                    }
                }
            }
            if (enabled) dataSource.installCdsAdmissionState(new CdsJdbcAdmissionState(connection.getCatalog()));
            else dataSource.enableCdsMetadataGuard();
        }
    }
}
