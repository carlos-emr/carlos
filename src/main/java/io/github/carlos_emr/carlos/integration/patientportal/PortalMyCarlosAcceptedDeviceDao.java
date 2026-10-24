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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hibernate.jdbc.ReturningWork;

/** Single-table prepared JDBC DAO for mycarlos_accepted_device; never owns a connection or transaction. */
final class PortalMyCarlosAcceptedDeviceDao {
    private static final String COLUMNS = "id,binding_id,device_key_id,x25519_public_key,ed25519_public_key,approval_kind,approval_challenge_id,approved_by_provider_no,approved_by_security_no,staff_verification_method,predecessor_accepted_id,predecessor_version,predecessor_approval,clinic_acceptance,clinic_acceptance_sha256,accepted_at,removed_at,removal_id,removal_reason,removed_by_provider_no,version";
    final EntityManagerFactory factory;
    PortalMyCarlosAcceptedDeviceDao(EntityManagerFactory factory) { this.factory = java.util.Objects.requireNonNull(factory);
    }
    private <T> T owned(ReturningWork<T> work) { return Jdbc.owned(factory, work);
    }
    private static PortalMyCarlosAcceptedDevice map(ResultSet rows) throws SQLException {
        var result = new PortalMyCarlosAcceptedDevice(
                rows.getLong("id"),
                rows.getLong("binding_id"),
                rows.getString("device_key_id"),
                Jdbc.bytes(rows.getBytes("x25519_public_key")),
                Jdbc.bytes(rows.getBytes("ed25519_public_key")),
                rows.getString("approval_kind"),
                Jdbc.bytes(rows.getBytes("approval_challenge_id")),
                rows.getString("approved_by_provider_no"),
                rows.getObject("approved_by_security_no", Integer.class),
                rows.getString("staff_verification_method"),
                rows.getObject("predecessor_accepted_id", Long.class),
                rows.getObject("predecessor_version", Long.class),
                Jdbc.ascii(rows.getBytes("predecessor_approval")),
                Jdbc.ascii(rows.getBytes("clinic_acceptance")),
                Jdbc.bytes(rows.getBytes("clinic_acceptance_sha256")),
                Jdbc.instant(rows, "accepted_at"),
                Jdbc.instant(rows, "removed_at"),
                Jdbc.uuid(rows.getBytes("removal_id")),
                rows.getString("removal_reason"),
                rows.getString("removed_by_provider_no"),
                rows.getLong("version"));
        Checks.positive(result.id());
        return result;
    }
    private Optional<PortalMyCarlosAcceptedDevice> find(Connection c, String where, Jdbc.Parameters parameters) throws SQLException {
        try (var q = c.prepareStatement("SELECT " + COLUMNS + " FROM mycarlos_accepted_device WHERE " + where + " FOR UPDATE")) {
            parameters.bind(q);
            try (var rows = q.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                var value = map(rows);
                Checks.require(!rows.next());
                return Optional.of(value);
            }
        }
    }
    PortalMyCarlosAcceptedDevice insert(PortalMyCarlosAcceptedDevice row) {
        return owned(c -> {
            Checks.require(row.id() == 0);
            try (var q = c.prepareStatement("INSERT INTO mycarlos_accepted_device (binding_id,device_key_id,x25519_public_key,ed25519_public_key,approval_kind,approval_challenge_id,approved_by_provider_no,approved_by_security_no,staff_verification_method,predecessor_accepted_id,predecessor_version,predecessor_approval,clinic_acceptance,clinic_acceptance_sha256,accepted_at,removed_at,removal_id,removal_reason,removed_by_provider_no,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                q.setLong(1, row.bindingId());
                q.setString(2, row.kid());
                q.setBytes(3, Jdbc.raw(row.x25519()));
                q.setBytes(4, Jdbc.raw(row.ed25519()));
                q.setString(5, row.kind());
                q.setBytes(6, Jdbc.raw(row.challengeId()));
                q.setString(7, row.providerNo());
                q.setObject(8, row.securityNo(), java.sql.Types.INTEGER);
                q.setString(9, row.staffMethod());
                q.setObject(10, row.predecessorId(), java.sql.Types.BIGINT);
                q.setObject(11, row.predecessorVersion(), java.sql.Types.BIGINT);
                q.setBytes(12, Jdbc.ascii(row.predecessorApproval()));
                q.setBytes(13, Jdbc.ascii(row.acceptance()));
                q.setBytes(14, Jdbc.raw(row.acceptanceHash()));
                Jdbc.timestamp(q, 15, row.acceptedAt());
                Jdbc.timestamp(q, 16, row.removedAt());
                q.setBytes(17, Jdbc.uuid(row.removalId()));
                q.setString(18, row.removalReason());
                q.setString(19, row.removedBy());
                q.setLong(20, row.version());
                Jdbc.changed(q.executeUpdate());
                try (var keys = q.getGeneratedKeys()) {
                    Checks.require(keys.next());
                    long id = keys.getLong(1);
                    Checks.positive(id);
                    Checks.require(!keys.next());
                    return new PortalMyCarlosAcceptedDevice(id, row.bindingId(), row.kid(), row.x25519(), row.ed25519(), row.kind(), row.challengeId(), row.providerNo(), row.securityNo(), row.staffMethod(), row.predecessorId(), row.predecessorVersion(), row.predecessorApproval(), row.acceptance(), row.acceptanceHash(), row.acceptedAt(), row.removedAt(), row.removalId(), row.removalReason(), row.removedBy(), row.version());
                }
            }
        });
    }
    Optional<PortalMyCarlosAcceptedDevice> findByKidForUpdate(String kid) {
        return owned(c -> find(c, "BINARY device_key_id=BINARY ?", q -> q.setString(1, kid)));
    }
    List<PortalMyCarlosAcceptedDevice> historyForUpdate(long binding, long after, int limit) {
        return owned(c -> {
            Checks.positive(binding);
            Checks.nonnegative(after);
            Checks.require(limit >= 1 && limit <= 51);
            var result = new ArrayList<PortalMyCarlosAcceptedDevice>();
            try (var q = c.prepareStatement("SELECT " + COLUMNS + " FROM mycarlos_accepted_device WHERE binding_id=? AND id>? ORDER BY id LIMIT ? FOR UPDATE")) {
                q.setLong(1, binding);
                q.setLong(2, after);
                q.setInt(3, limit);
                try (var rows = q.executeQuery()) { while (rows.next()) result.add(map(rows));
                }
            }
            return List.copyOf(result);
        });
    }
    void markRemoved(PortalMyCarlosAcceptedDevice before, PortalMyCarlosAcceptedDevice after) {
        owned(c -> {
            Checks.require(before.current() && !after.current() && before.id() == after.id() && before.version() < Long.MAX_VALUE && after.version() == before.version() + 1);
            Checks.require(after.equals(before.removed(after.removalId(), after.removedBy(), after.removedAt())));
            try (var q = c.prepareStatement("UPDATE mycarlos_accepted_device SET removed_at=?,removal_id=?,removal_reason=?,removed_by_provider_no=?,version=? WHERE id=? AND binding_id=? AND BINARY device_key_id=BINARY ? AND version=? AND removed_at IS NULL AND x25519_public_key=? AND ed25519_public_key=? AND approval_challenge_id=? AND BINARY clinic_acceptance=BINARY ?")) {
                Jdbc.timestamp(q, 1, after.removedAt());
                q.setBytes(2, Jdbc.uuid(after.removalId()));
                q.setString(3, after.removalReason());
                q.setString(4, after.removedBy());
                q.setLong(5, after.version());
                q.setLong(6, before.id());
                q.setLong(7, before.bindingId());
                q.setString(8, before.kid());
                q.setLong(9, before.version());
                q.setBytes(10, before.x25519().copy());
                q.setBytes(11, before.ed25519().copy());
                q.setBytes(12, before.challengeId().copy());
                q.setBytes(13, Jdbc.ascii(before.acceptance()));
                Jdbc.changed(q.executeUpdate());
            }
            return null;
        });
    }
}
