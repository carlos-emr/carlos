/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Checks;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Jdbc;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.hibernate.jdbc.ReturningWork;

/** Single-table prepared JDBC DAO for mycarlos_device_approval_outbox; never owns a connection or transaction. */
final class PortalMyCarlosDeviceApprovalOutboxDao {
    private static final String COLUMNS = "id,challenge_id,message_kind,clinic_id,target_uri,config_binding_sha256,exact_body,body_sha256,created_at,next_attempt_at,failures,lease_token,lease_expires_at,published_at,version";
    final EntityManagerFactory factory;
    PortalMyCarlosDeviceApprovalOutboxDao(EntityManagerFactory factory) { this.factory = java.util.Objects.requireNonNull(factory);
    }
    private <T> T owned(ReturningWork<T> work) { return Jdbc.owned(factory, work);
    }
    private static PortalMyCarlosDeviceApprovalOutboxEntry map(ResultSet rows) throws SQLException {
        var result = new PortalMyCarlosDeviceApprovalOutboxEntry(
                rows.getLong("id"),
                Jdbc.bytes(rows.getBytes("challenge_id")),
                rows.getString("message_kind"),
                rows.getString("clinic_id"),
                rows.getString("target_uri"),
                Jdbc.bytes(rows.getBytes("config_binding_sha256")),
                Jdbc.bytes(rows.getBytes("exact_body")),
                Jdbc.bytes(rows.getBytes("body_sha256")),
                Jdbc.instant(rows, "created_at"),
                Jdbc.instant(rows, "next_attempt_at"),
                rows.getInt("failures"),
                Jdbc.bytes(rows.getBytes("lease_token")),
                Jdbc.instant(rows, "lease_expires_at"),
                Jdbc.instant(rows, "published_at"),
                rows.getLong("version"));
        Checks.positive(result.id());
        return result;
    }
    private Optional<PortalMyCarlosDeviceApprovalOutboxEntry> find(Connection c, String where, Jdbc.Parameters parameters) throws SQLException {
        try (var q = c.prepareStatement("SELECT " + COLUMNS + " FROM mycarlos_device_approval_outbox WHERE " + where + " FOR UPDATE")) {
            parameters.bind(q);
            try (var rows = q.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                var value = map(rows);
                Checks.require(!rows.next());
                return Optional.of(value);
            }
        }
    }
    PortalMyCarlosDeviceApprovalOutboxEntry insert(PortalMyCarlosDeviceApprovalOutboxEntry row) {
        return owned(c -> {
            Checks.require(row.id() == 0);
            try (var q = c.prepareStatement("INSERT INTO mycarlos_device_approval_outbox (challenge_id,message_kind,clinic_id,target_uri,config_binding_sha256,exact_body,body_sha256,created_at,next_attempt_at,failures,lease_token,lease_expires_at,published_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                q.setBytes(1, Jdbc.raw(row.challengeId()));
                q.setString(2, row.messageKind());
                q.setString(3, row.clinicId());
                q.setString(4, row.targetUri());
                q.setBytes(5, Jdbc.raw(row.configHash()));
                q.setBytes(6, Jdbc.raw(row.body()));
                q.setBytes(7, Jdbc.raw(row.bodyHash()));
                Jdbc.timestamp(q, 8, row.createdAt());
                Jdbc.timestamp(q, 9, row.nextAttemptAt());
                q.setInt(10, row.failures());
                q.setBytes(11, Jdbc.raw(row.leaseToken()));
                Jdbc.timestamp(q, 12, row.leaseExpiresAt());
                Jdbc.timestamp(q, 13, row.publishedAt());
                q.setLong(14, row.version());
                Jdbc.changed(q.executeUpdate());
                try (var keys = q.getGeneratedKeys()) {
                    Checks.require(keys.next());
                    long id = keys.getLong(1);
                    Checks.positive(id);
                    Checks.require(!keys.next());
                    return new PortalMyCarlosDeviceApprovalOutboxEntry(id, row.challengeId(), row.messageKind(), row.clinicId(), row.targetUri(), row.configHash(), row.body(), row.bodyHash(), row.createdAt(), row.nextAttemptAt(), row.failures(), row.leaseToken(), row.leaseExpiresAt(), row.publishedAt(), row.version());
                }
            }
        });
    }
    Optional<PortalMyCarlosDeviceApprovalOutboxEntry> findMessageForUpdate(Bytes challenge, String kind) {
        return owned(c -> find(c, "challenge_id=? AND BINARY message_kind=BINARY ?", q -> { q.setBytes(1, challenge.copy());
            q.setString(2, kind);
        }));
    }
    void updateLeaseState(PortalMyCarlosDeviceApprovalOutboxEntry before,
            PortalMyCarlosDeviceApprovalOutboxEntry after) {
        Checks.require(before.id() == after.id() && before.publishedAt() == null
                && before.challengeId().equals(after.challengeId())
                && before.messageKind().equals(after.messageKind())
                && before.clinicId().equals(after.clinicId()) && before.targetUri().equals(after.targetUri())
                && before.configHash().equals(after.configHash()) && before.body().equals(after.body())
                && before.bodyHash().equals(after.bodyHash()) && before.createdAt().equals(after.createdAt())
                && after.version() == before.version() + 1);
        owned(c -> {
            try (var q = c.prepareStatement("UPDATE mycarlos_device_approval_outbox SET next_attempt_at=?,failures=?,lease_token=?,lease_expires_at=?,published_at=?,version=? WHERE id=? AND version=? AND published_at IS NULL")) {
                Jdbc.timestamp(q, 1, after.nextAttemptAt());
                q.setInt(2, after.failures());
                q.setBytes(3, Jdbc.raw(after.leaseToken()));
                Jdbc.timestamp(q, 4, after.leaseExpiresAt());
                Jdbc.timestamp(q, 5, after.publishedAt());
                q.setLong(6, after.version());
                q.setLong(7, before.id());
                q.setLong(8, before.version());
                Jdbc.changed(q.executeUpdate());
            }
            return null;
        });
    }

}
