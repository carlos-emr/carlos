/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Checks;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Jdbc;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import jakarta.persistence.EntityManagerFactory;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.jdbc.ReturningWork;

/** Single-table prepared JDBC DAO for mycarlos_device_approval_challenge; never owns a connection or transaction. */
final class PortalMyCarlosDeviceApprovalChallengeDao {
    private static final String COLUMNS = "challenge_id,request_id,binding_id,clinic_id,demographic_no,recipient_id,public_portal_base_url,config_binding_sha256,candidate_device_key_id,candidate_x25519_public_key,candidate_ed25519_public_key,candidate_intent,candidate_intent_sha256,signed_challenge,challenge_sha256,approval_kind,predecessor_accepted_id,predecessor_device_key_id,predecessor_version,issued_at,expires_at,consumed_at,consumed_approval_jti,consumed_approval,consumed_approval_sha256,cancelled_at,cancellation_reason";
    private static final String FIND_BY_REQUEST = "SELECT " + COLUMNS
            + " FROM mycarlos_device_approval_challenge WHERE BINARY clinic_id=BINARY ? AND request_id=? FOR UPDATE";
    private static final String FIND_BY_CHALLENGE = "SELECT " + COLUMNS
            + " FROM mycarlos_device_approval_challenge WHERE challenge_id=? FOR UPDATE";
    private static final String FIND_BY_CONSUMED_JTI = "SELECT " + COLUMNS
            + " FROM mycarlos_device_approval_challenge WHERE predecessor_accepted_id=? AND consumed_approval_jti=? FOR UPDATE";
    final EntityManagerFactory factory;
    PortalMyCarlosDeviceApprovalChallengeDao(EntityManagerFactory factory) { this.factory = java.util.Objects.requireNonNull(factory);
    }
    private <T> T owned(ReturningWork<T> work) { return Jdbc.owned(factory, work);
    }
    private static PortalMyCarlosDeviceApprovalChallenge map(ResultSet rows) throws SQLException {
        var result = new PortalMyCarlosDeviceApprovalChallenge(
                Jdbc.bytes(rows.getBytes("challenge_id")),
                Jdbc.uuid(rows.getBytes("request_id")),
                rows.getObject("binding_id", Long.class),
                rows.getString("clinic_id"),
                rows.getInt("demographic_no"),
                rows.getString("recipient_id"),
                rows.getString("public_portal_base_url"),
                Jdbc.bytes(rows.getBytes("config_binding_sha256")),
                rows.getString("candidate_device_key_id"),
                Jdbc.bytes(rows.getBytes("candidate_x25519_public_key")),
                Jdbc.bytes(rows.getBytes("candidate_ed25519_public_key")),
                Jdbc.ascii(rows.getBytes("candidate_intent")),
                Jdbc.bytes(rows.getBytes("candidate_intent_sha256")),
                Jdbc.ascii(rows.getBytes("signed_challenge")),
                Jdbc.bytes(rows.getBytes("challenge_sha256")),
                rows.getString("approval_kind"),
                rows.getObject("predecessor_accepted_id", Long.class),
                rows.getString("predecessor_device_key_id"),
                rows.getObject("predecessor_version", Long.class),
                Jdbc.instant(rows, "issued_at"),
                Jdbc.instant(rows, "expires_at"),
                Jdbc.instant(rows, "consumed_at"),
                Jdbc.uuid(rows.getBytes("consumed_approval_jti")),
                Jdbc.ascii(rows.getBytes("consumed_approval")),
                Jdbc.bytes(rows.getBytes("consumed_approval_sha256")),
                Jdbc.instant(rows, "cancelled_at"),
                rows.getString("cancellation_reason"));
        return result;
    }
    private Optional<PortalMyCarlosDeviceApprovalChallenge> find(PreparedStatement q) throws SQLException {
        try (var rows = q.executeQuery()) {
            if (!rows.next()) return Optional.empty();
            var value = map(rows);
            Checks.require(!rows.next());
            return Optional.of(value);
        }
    }
    PortalMyCarlosDeviceApprovalChallenge insert(PortalMyCarlosDeviceApprovalChallenge row) {
        return owned(c -> {
            try (var q = c.prepareStatement("INSERT INTO mycarlos_device_approval_challenge (challenge_id,request_id,binding_id,clinic_id,demographic_no,recipient_id,public_portal_base_url,config_binding_sha256,candidate_device_key_id,candidate_x25519_public_key,candidate_ed25519_public_key,candidate_intent,candidate_intent_sha256,signed_challenge,challenge_sha256,approval_kind,predecessor_accepted_id,predecessor_device_key_id,predecessor_version,issued_at,expires_at,consumed_at,consumed_approval_jti,consumed_approval,consumed_approval_sha256,cancelled_at,cancellation_reason) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                q.setBytes(1, Jdbc.raw(row.challengeId()));
                q.setBytes(2, Jdbc.uuid(row.requestId()));
                q.setObject(3, row.bindingId(), java.sql.Types.BIGINT);
                q.setString(4, row.clinicId());
                q.setInt(5, row.demographicNo());
                q.setString(6, row.recipientId());
                q.setString(7, row.publicBase());
                q.setBytes(8, Jdbc.raw(row.configHash()));
                q.setString(9, row.candidateKid());
                q.setBytes(10, Jdbc.raw(row.candidateX()));
                q.setBytes(11, Jdbc.raw(row.candidateEd()));
                q.setBytes(12, Jdbc.ascii(row.intent()));
                q.setBytes(13, Jdbc.raw(row.intentHash()));
                q.setBytes(14, Jdbc.ascii(row.signedChallenge()));
                q.setBytes(15, Jdbc.raw(row.challengeHash()));
                q.setString(16, row.kind());
                q.setObject(17, row.predecessorId(), java.sql.Types.BIGINT);
                q.setString(18, row.predecessorKid());
                q.setObject(19, row.predecessorVersion(), java.sql.Types.BIGINT);
                Jdbc.timestamp(q, 20, row.issuedAt());
                Jdbc.timestamp(q, 21, row.expiresAt());
                Jdbc.timestamp(q, 22, row.consumedAt());
                q.setBytes(23, Jdbc.uuid(row.approvalJti()));
                q.setBytes(24, Jdbc.ascii(row.consumedApproval()));
                q.setBytes(25, Jdbc.raw(row.consumedApprovalHash()));
                Jdbc.timestamp(q, 26, row.cancelledAt());
                q.setString(27, row.cancellationReason());
                Jdbc.changed(q.executeUpdate());
                return row;
            }
        });
    }
    Optional<PortalMyCarlosDeviceApprovalChallenge> findByRequestForUpdate(String clinic, UUID request) {
        return owned(c -> {
            try (var q = c.prepareStatement(FIND_BY_REQUEST)) {
                q.setString(1, clinic);
                q.setBytes(2, Jdbc.uuid(request));
                return find(q);
            }
        });
    }
    /**
     * Whether this patient had a clinic code request cancelled after {@code since} because its code
     * could not be emailed (mycarlos#141): their next first-device request then goes to staff.
     */
    boolean codeNotSentSince(String clinic, int patient, java.time.Instant since) {
        return owned(c -> {
            try (var q = c.prepareStatement("SELECT 1 FROM mycarlos_device_approval_challenge WHERE BINARY clinic_id=BINARY ?"
                    + " AND demographic_no=? AND approval_kind='code' AND cancellation_reason=? AND cancelled_at>? LIMIT 1")) {
                q.setString(1, clinic);
                q.setInt(2, patient);
                q.setString(3, PortalMyCarlosDeviceApprovalService.CODE_NOT_SENT);
                Jdbc.timestamp(q, 4, since);
                try (var rows = q.executeQuery()) {
                    return rows.next();
                }
            }
        });
    }
    Optional<PortalMyCarlosDeviceApprovalChallenge> findByChallengeForUpdate(Bytes challenge) {
        return owned(c -> {
            try (var q = c.prepareStatement(FIND_BY_CHALLENGE)) {
                q.setBytes(1, challenge.copy());
                return find(q);
            }
        });
    }
    Optional<PortalMyCarlosDeviceApprovalChallenge> findByConsumedJtiForUpdate(long predecessor, UUID jti) {
        return owned(c -> {
            try (var q = c.prepareStatement(FIND_BY_CONSUMED_JTI)) {
                q.setLong(1, predecessor);
                q.setBytes(2, Jdbc.uuid(jti));
                return find(q);
            }
        });
    }
    List<PortalMyCarlosDeviceApprovalChallenge> findOpenForPatientForUpdate(String clinic, int patient) {
        return owned(c -> {
            var result = new ArrayList<PortalMyCarlosDeviceApprovalChallenge>();
            try (var q = c.prepareStatement("SELECT " + COLUMNS + " FROM mycarlos_device_approval_challenge WHERE BINARY clinic_id=BINARY ? AND demographic_no=? AND consumed_at IS NULL AND cancelled_at IS NULL ORDER BY challenge_id LIMIT 4 FOR UPDATE")) {
                q.setString(1, clinic);
                q.setInt(2, patient);
                try (var rows = q.executeQuery()) { while (rows.next()) result.add(map(rows));
                }
            }
            if (result.size() > 3) throw new IllegalStateException("approval pending row bound exceeded");
            return List.copyOf(result);
        });
    }
    void terminal(PortalMyCarlosDeviceApprovalChallenge before, PortalMyCarlosDeviceApprovalChallenge after) {
        owned(c -> {
            Checks.require(before.pending() && before.challengeId().equals(after.challengeId()) && !after.pending());
            Checks.require(after.equals(after.consumedAt() != null
                    ? before.consumed(after.bindingId(), after.consumedApproval(), after.approvalJti(), after.consumedAt())
                    : before.cancelled(after.cancelledAt(), after.cancellationReason())));
            try (var q = c.prepareStatement("UPDATE mycarlos_device_approval_challenge SET binding_id=?,consumed_at=?,consumed_approval_jti=?,consumed_approval=?,consumed_approval_sha256=?,cancelled_at=?,cancellation_reason=? WHERE challenge_id=? AND consumed_at IS NULL AND cancelled_at IS NULL AND binding_id <=> ? AND BINARY candidate_intent=BINARY ? AND BINARY signed_challenge=BINARY ?")) {
                q.setObject(1, after.bindingId(), java.sql.Types.BIGINT);
                Jdbc.timestamp(q, 2, after.consumedAt());
                q.setBytes(3, Jdbc.uuid(after.approvalJti()));
                q.setBytes(4, Jdbc.ascii(after.consumedApproval()));
                q.setBytes(5, Jdbc.raw(after.consumedApprovalHash()));
                Jdbc.timestamp(q, 6, after.cancelledAt());
                q.setString(7, after.cancellationReason());
                q.setBytes(8, before.challengeId().copy());
                q.setObject(9, before.bindingId(), java.sql.Types.BIGINT);
                q.setBytes(10, Jdbc.ascii(before.intent()));
                q.setBytes(11, Jdbc.ascii(before.signedChallenge()));
                Jdbc.changed(q.executeUpdate());
            }
            return null;
        });
    }
}
