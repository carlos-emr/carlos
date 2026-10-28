/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Checks;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Jdbc;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import jakarta.persistence.EntityManagerFactory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.jdbc.ReturningWork;

/**
 * Single-table prepared JDBC DAO for mycarlos_device_approval_code (clinic code v1, mycarlos#141);
 * like the other approval DAOs it never owns a connection or transaction.
 */
final class PortalMyCarlosDeviceApprovalCodeDao {
    private static final String COLUMNS = "challenge_id,clinic_id,request_id,policy,code_key_id,code_mac,"
            + "wrong_attempts,issued_at,expires_at,emailed_at,matched_at,refused_at,version";
    final EntityManagerFactory factory;

    PortalMyCarlosDeviceApprovalCodeDao(EntityManagerFactory factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    private <T> T owned(ReturningWork<T> work) {
        return Jdbc.owned(factory, work);
    }

    private static PortalMyCarlosDeviceApprovalCode map(ResultSet rows) throws SQLException {
        return new PortalMyCarlosDeviceApprovalCode(
                Jdbc.bytes(rows.getBytes("challenge_id")),
                rows.getString("clinic_id"),
                Jdbc.uuid(rows.getBytes("request_id")),
                rows.getString("policy"),
                Jdbc.bytes(rows.getBytes("code_key_id")),
                Jdbc.bytes(rows.getBytes("code_mac")),
                rows.getInt("wrong_attempts"),
                Jdbc.instant(rows, "issued_at"),
                Jdbc.instant(rows, "expires_at"),
                Jdbc.instant(rows, "emailed_at"),
                Jdbc.instant(rows, "matched_at"),
                Jdbc.instant(rows, "refused_at"),
                rows.getLong("version"));
    }

    Optional<PortalMyCarlosDeviceApprovalCode> findByChallengeForUpdate(Bytes challengeId) {
        Checks.bytes(challengeId, 32);
        return owned(c -> {
            try (var q = c.prepareStatement("SELECT " + COLUMNS
                    + " FROM mycarlos_device_approval_code WHERE challenge_id=? FOR UPDATE")) {
                q.setBytes(1, challengeId.copy());
                return one(q);
            }
        });
    }

    Optional<PortalMyCarlosDeviceApprovalCode> findByRequestForUpdate(String clinicId, UUID requestId) {
        Checks.text(clinicId, 256);
        Checks.uuid(requestId);
        return owned(c -> {
            try (var q = c.prepareStatement("SELECT " + COLUMNS
                    + " FROM mycarlos_device_approval_code WHERE BINARY clinic_id=BINARY ? AND request_id=? FOR UPDATE")) {
                q.setString(1, clinicId);
                q.setBytes(2, Jdbc.uuid(requestId));
                return one(q);
            }
        });
    }

    /** Challenge ids of this clinic's codes that expire after {@code after}, oldest first. */
    java.util.List<Bytes> recentChallengeIds(String clinicId, java.time.Instant after, int limit) {
        Checks.text(clinicId, 256);
        Checks.require(limit > 0 && limit <= 1000);
        return owned(c -> {
            try (var q = c.prepareStatement("SELECT challenge_id FROM mycarlos_device_approval_code"
                    + " WHERE BINARY clinic_id=BINARY ? AND expires_at>? ORDER BY issued_at, challenge_id LIMIT ?")) {
                q.setString(1, clinicId);
                Jdbc.timestamp(q, 2, after);
                q.setInt(3, limit);
                var ids = new java.util.ArrayList<Bytes>();
                try (var rows = q.executeQuery()) {
                    while (rows.next()) ids.add(Jdbc.bytes(rows.getBytes(1)));
                }
                return ids;
            }
        });
    }

    private static Optional<PortalMyCarlosDeviceApprovalCode> one(java.sql.PreparedStatement q) throws SQLException {
        try (var rows = q.executeQuery()) {
            if (!rows.next()) return Optional.empty();
            var value = map(rows);
            Checks.require(!rows.next());
            return Optional.of(value);
        }
    }

    void insert(PortalMyCarlosDeviceApprovalCode row) {
        Checks.require(row.version() == 0 && row.wrongAttempts() == 0 && row.open() && row.emailedAt() == null);
        owned(c -> {
            try (var q = c.prepareStatement("INSERT INTO mycarlos_device_approval_code (" + COLUMNS
                    + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                bind(q, row);
                Jdbc.changed(q.executeUpdate());
            }
            return null;
        });
    }

    /** Moves one row forward, only from the exact state read under lock. */
    void update(PortalMyCarlosDeviceApprovalCode before, PortalMyCarlosDeviceApprovalCode after) {
        Checks.require(before.challengeId().equals(after.challengeId()) && after.version() == before.version() + 1
                && before.mac().equals(after.mac()) && before.keyId().equals(after.keyId()));
        owned(c -> {
            try (var q = c.prepareStatement("UPDATE mycarlos_device_approval_code SET wrong_attempts=?,"
                    + "emailed_at=?,matched_at=?,refused_at=?,version=? WHERE challenge_id=? AND version=?"
                    + " AND matched_at IS NULL AND refused_at IS NULL")) {
                q.setInt(1, after.wrongAttempts());
                Jdbc.timestamp(q, 2, after.emailedAt());
                Jdbc.timestamp(q, 3, after.matchedAt());
                Jdbc.timestamp(q, 4, after.refusedAt());
                q.setLong(5, after.version());
                q.setBytes(6, before.challengeId().copy());
                q.setLong(7, before.version());
                Jdbc.changed(q.executeUpdate());
            }
            return null;
        });
    }

    /** Replaces a code that was never emailed (see {@link PortalMyCarlosDeviceApprovalCode#reissued}). */
    void reissue(PortalMyCarlosDeviceApprovalCode before, PortalMyCarlosDeviceApprovalCode after) {
        Checks.require(before.challengeId().equals(after.challengeId()) && after.version() == before.version() + 1
                && before.emailedAt() == null && after.emailedAt() == null && after.wrongAttempts() == 0 && after.open());
        owned(c -> {
            try (var q = c.prepareStatement("UPDATE mycarlos_device_approval_code SET code_key_id=?,code_mac=?,version=?"
                    + " WHERE challenge_id=? AND version=? AND emailed_at IS NULL AND wrong_attempts=0"
                    + " AND matched_at IS NULL AND refused_at IS NULL")) {
                q.setBytes(1, after.keyId().copy());
                q.setBytes(2, after.mac().copy());
                q.setLong(3, after.version());
                q.setBytes(4, before.challengeId().copy());
                q.setLong(5, before.version());
                Jdbc.changed(q.executeUpdate());
            }
            return null;
        });
    }

    private static void bind(java.sql.PreparedStatement q, PortalMyCarlosDeviceApprovalCode row) throws SQLException {
        q.setBytes(1, row.challengeId().copy());
        q.setString(2, row.clinicId());
        q.setBytes(3, Jdbc.uuid(row.requestId()));
        q.setString(4, row.policy());
        q.setBytes(5, row.keyId().copy());
        q.setBytes(6, row.mac().copy());
        q.setInt(7, row.wrongAttempts());
        Jdbc.timestamp(q, 8, row.issuedAt());
        Jdbc.timestamp(q, 9, row.expiresAt());
        Jdbc.timestamp(q, 10, row.emailedAt());
        Jdbc.timestamp(q, 11, row.matchedAt());
        Jdbc.timestamp(q, 12, row.refusedAt());
        q.setLong(13, row.version());
    }
}
