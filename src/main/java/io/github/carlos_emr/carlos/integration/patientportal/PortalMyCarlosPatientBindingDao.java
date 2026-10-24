/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Checks;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Jdbc;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.hibernate.jdbc.ReturningWork;

/** Single-table prepared JDBC DAO for mycarlos_patient_binding; never owns a connection or transaction. */
final class PortalMyCarlosPatientBindingDao {
    private static final String COLUMNS = "id,clinic_id,demographic_no,recipient_id,public_portal_base_url,internal_portal_origin,config_binding_sha256,root_device_key_id,created_at,version";
    final EntityManagerFactory factory;
    PortalMyCarlosPatientBindingDao(EntityManagerFactory factory) { this.factory = java.util.Objects.requireNonNull(factory);
    }
    private <T> T owned(ReturningWork<T> work) { return Jdbc.owned(factory, work);
    }
    private static PortalMyCarlosPatientBinding map(ResultSet rows) throws SQLException {
        var result = new PortalMyCarlosPatientBinding(
                rows.getLong("id"),
                rows.getString("clinic_id"),
                rows.getInt("demographic_no"),
                rows.getString("recipient_id"),
                rows.getString("public_portal_base_url"),
                rows.getString("internal_portal_origin"),
                Jdbc.bytes(rows.getBytes("config_binding_sha256")),
                rows.getString("root_device_key_id"),
                Jdbc.instant(rows, "created_at"),
                rows.getLong("version"));
        Checks.positive(result.id());
        return result;
    }
    private Optional<PortalMyCarlosPatientBinding> find(Connection c, String where, Jdbc.Parameters parameters) throws SQLException {
        try (var q = c.prepareStatement("SELECT " + COLUMNS + " FROM mycarlos_patient_binding WHERE " + where + " FOR UPDATE")) {
            parameters.bind(q);
            try (var rows = q.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                var value = map(rows);
                Checks.require(!rows.next());
                return Optional.of(value);
            }
        }
    }
    PortalMyCarlosPatientBinding insert(PortalMyCarlosPatientBinding row) {
        return owned(c -> {
            Checks.require(row.id() == 0);
            try (var q = c.prepareStatement("INSERT INTO mycarlos_patient_binding (clinic_id,demographic_no,recipient_id,public_portal_base_url,internal_portal_origin,config_binding_sha256,root_device_key_id,created_at,version) VALUES (?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                q.setString(1, row.clinicId());
                q.setInt(2, row.demographicNo());
                q.setString(3, row.recipientId());
                q.setString(4, row.publicBase());
                q.setString(5, row.internalOrigin());
                q.setBytes(6, Jdbc.raw(row.configHash()));
                q.setString(7, row.rootKid());
                Jdbc.timestamp(q, 8, row.createdAt());
                q.setLong(9, row.version());
                Jdbc.changed(q.executeUpdate());
                try (var keys = q.getGeneratedKeys()) {
                    Checks.require(keys.next());
                    long id = keys.getLong(1);
                    Checks.positive(id);
                    Checks.require(!keys.next());
                    return new PortalMyCarlosPatientBinding(id, row.clinicId(), row.demographicNo(), row.recipientId(), row.publicBase(), row.internalOrigin(), row.configHash(), row.rootKid(), row.createdAt(), row.version());
                }
            }
        });
    }
    Optional<PortalMyCarlosPatientBinding> findByClinicPatientForUpdate(String clinic, int patient) {
        return owned(c -> find(c, "BINARY clinic_id=BINARY ? AND demographic_no=?", q -> { q.setString(1, clinic);
            q.setInt(2, patient);
        }));
    }
    Optional<PortalMyCarlosPatientBinding> findByClinicRecipientForUpdate(String clinic, String recipient) {
        return owned(c -> find(c, "BINARY clinic_id=BINARY ? AND BINARY recipient_id=BINARY ?", q -> { q.setString(1, clinic);
            q.setString(2, recipient);
        }));
    }
}
