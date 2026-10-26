/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryCodec.invalid;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Jdbc;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.jdbc.ReturningWork;

/** Dedicated sender rows; all work joins the existing M3 transaction/coordination owner. */
final class PortalMyCarlosDeliveryOutboxDao {
    private final EntityManagerFactory factory;
    PortalMyCarlosDeliveryOutboxDao(EntityManagerFactory factory) { this.factory = Objects.requireNonNull(factory); }
    private <T> T owned(ReturningWork<T> work) { return Jdbc.owned(factory, work); }

    record Original(long id, String hash, Bytes encrypted) {
        @Override public String toString() { return "DeliveryOriginalRow[REDACTED]"; }
    }
    record Row(long id, long originalId, String clinicId, String deliveryId, String deviceKid,
               int demographicNo, long acceptedDeviceId, long acceptanceVersion, String configHash,
               Bytes encryptedSnapshot, String snapshotHash, String state, String leaseId, Long leaseUntil,
               long version, long retryAfter, Bytes encryptedReceipt, String receiptHash, String receiptJti) {
        @Override public String toString() { return "DeliveryOutboxRow[REDACTED]"; }
    }

    Optional<Original> original(String clinic, String delivery) {
        return owned(c -> {
            try (var q = c.prepareStatement("SELECT id,original_sha256,encrypted_original FROM mycarlos_delivery_original WHERE clinic_id=? AND delivery_id=? FOR UPDATE")) {
                q.setString(1, clinic); q.setString(2, delivery);
                try (var r = q.executeQuery()) {
                    if (!r.next()) return Optional.empty();
                    var value = new Original(r.getLong(1), hex(r.getBytes(2)), new Bytes(r.getBytes(3)));
                    if (r.next()) throw invalid();
                    return Optional.of(value);
                }
            }
        });
    }

    long insertOriginal(String clinic, String delivery, String hash, byte[] encrypted, long created) {
        return owned(c -> {
            try (var q = c.prepareStatement("INSERT INTO mycarlos_delivery_original (clinic_id,delivery_id,original_sha256,encrypted_original,created_at) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                q.setString(1, clinic); q.setString(2, delivery); q.setBytes(3, raw(hash));
                q.setBytes(4, encrypted); q.setLong(5, created);
                changed(q.executeUpdate());
                return generated(q);
            }
        });
    }

    Optional<Row> find(String clinic, String delivery, String kid) {
        return owned(c -> {
            try (var q = c.prepareStatement("SELECT * FROM mycarlos_delivery_outbox WHERE clinic_id=? AND delivery_id=? AND device_key_id=? FOR UPDATE")) {
                q.setString(1, clinic); q.setString(2, delivery); q.setString(3, kid);
                try (var r = q.executeQuery()) {
                    if (!r.next()) return Optional.empty();
                    Row value = row(r);
                    if (r.next()) throw invalid();
                    return Optional.of(value);
                }
            }
        });
    }

    Optional<Row> find(long id) {
        return owned(c -> {
            try (var q = c.prepareStatement("SELECT * FROM mycarlos_delivery_outbox WHERE id=? FOR UPDATE")) {
                q.setLong(1, id);
                try (var r = q.executeQuery()) { return r.next() ? Optional.of(row(r)) : Optional.empty(); }
            }
        });
    }

    record Reference(long id, String deliveryId, String deviceKid) {}

    java.util.List<Reference> historyReferences(String clinic, int patient, long before, int limit) {
        if (patient <= 0 || before < 0 || limit < 1 || limit > 51) throw invalid();
        return owned(c -> {
            try (var q = c.prepareStatement("SELECT id,delivery_id,device_key_id FROM mycarlos_delivery_outbox WHERE clinic_id=? AND demographic_no=? AND id<? ORDER BY id DESC LIMIT ? FOR UPDATE")) {
                q.setString(1, clinic); q.setInt(2, patient); q.setLong(3, before == 0 ? Long.MAX_VALUE : before); q.setInt(4, limit);
                java.util.List<Reference> result = new java.util.ArrayList<>();
                try (var r = q.executeQuery()) { while (r.next()) result.add(new Reference(r.getLong(1), r.getString(2), r.getString(3))); }
                return java.util.List.copyOf(result);
            }
        });
    }

    java.util.List<Row> findForDelivery(String clinic, String deliveryId) {
        return owned(c -> {
            try (var q = c.prepareStatement("SELECT * FROM mycarlos_delivery_outbox WHERE clinic_id=? AND delivery_id=? ORDER BY id LIMIT 2 FOR UPDATE")) {
                q.setString(1, clinic); q.setString(2, deliveryId);
                java.util.List<Row> rows = new java.util.ArrayList<>();
                try (var r = q.executeQuery()) { while (r.next()) rows.add(row(r)); }
                return java.util.List.copyOf(rows);
            }
        });
    }

    long insert(long original, PortalMyCarlosDeliverySnapshot s, byte[] encrypted, String snapshotHash) {
        return owned(c -> {
            try (var q = c.prepareStatement("INSERT INTO mycarlos_delivery_outbox (original_id,clinic_id,delivery_id,device_key_id,demographic_no,accepted_device_id,acceptance_version,config_sha256,encrypted_snapshot,snapshot_sha256) VALUES (?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                q.setLong(1, original); q.setString(2, s.clinicId()); q.setString(3, s.deliveryId());
                q.setString(4, s.device().kid()); q.setInt(5, s.demographicNo()); q.setLong(6, s.acceptedDeviceId());
                q.setLong(7, s.acceptanceVersion()); q.setBytes(8, raw(s.configSha256()));
                q.setBytes(9, encrypted); q.setBytes(10, raw(snapshotHash));
                changed(q.executeUpdate());
                return generated(q);
            }
        });
    }

    void claim(Row row, String lease, long until, long now) {
        owned(c -> {
            nextVersion(row);
            try (var q = c.prepareStatement("UPDATE mycarlos_delivery_outbox SET lease_id=?,lease_until=?,row_version=row_version+1 WHERE id=? AND row_version=? AND state='staged' AND retry_after<=? AND (lease_until IS NULL OR lease_until<=?)")) {
                q.setString(1, lease); q.setLong(2, until); q.setLong(3, row.id()); q.setLong(4, row.version());
                q.setLong(5, now); q.setLong(6, now); changed(q.executeUpdate());
            }
            return null;
        });
    }

    /** CAS protects a late callback from overwriting a replacement lease or verified pickup. */
    boolean finish(long id, String lease, long version, String state, long retryAfter, String safeFailure) {
        if (!java.util.Set.of("staged", "waiting", "expired", "removed", "fenced", "error").contains(state)
                || retryAfter < 0 || version < 0 || version == Long.MAX_VALUE
                || (safeFailure != null && !java.util.Set.of("transport_unknown", "device_removed", "config_changed",
                "delivery_conflict", "mailbox_quota_exceeded", "invalid_delivery", "local_ineligible", "portal_failure").contains(safeFailure))) throw invalid();
        return owned(c -> {
            try (var q = c.prepareStatement("UPDATE mycarlos_delivery_outbox SET state=?,retry_after=?,failure_code=?,lease_id=NULL,lease_until=NULL,row_version=row_version+1 WHERE id=? AND lease_id=? AND row_version=? AND state='staged'")) {
                q.setString(1, state); q.setLong(2, retryAfter); q.setString(3, safeFailure);
                q.setLong(4, id); q.setString(5, lease); q.setLong(6, version);
                return q.executeUpdate() == 1;
            }
        });
    }

    void pickedUp(Row row, byte[] encryptedReceipt, String receiptHash, String jti) {
        owned(c -> {
            nextVersion(row);
            try (var q = c.prepareStatement("UPDATE mycarlos_delivery_outbox SET state='picked_up',encrypted_receipt=?,receipt_sha256=?,receipt_jti=?,lease_id=NULL,lease_until=NULL,failure_code=NULL,row_version=row_version+1 WHERE id=? AND row_version=? AND state<>'picked_up'")) {
                q.setBytes(1, encryptedReceipt); q.setBytes(2, raw(receiptHash)); q.setString(3, jti);
                q.setLong(4, row.id()); q.setLong(5, row.version()); changed(q.executeUpdate());
            }
            return null;
        });
    }

    void terminal(Row row, String state) {
        if (!java.util.Set.of("expired", "removed", "fenced").contains(state)) throw invalid();
        if ("picked_up".equals(row.state())) return;
        owned(c -> {
            nextVersion(row);
            try (var q = c.prepareStatement("UPDATE mycarlos_delivery_outbox SET state=?,lease_id=NULL,lease_until=NULL,row_version=row_version+1 WHERE id=? AND row_version=? AND state<>'picked_up'")) {
                q.setString(1, state); q.setLong(2, row.id()); q.setLong(3, row.version()); changed(q.executeUpdate());
            }
            return null;
        });
    }

    boolean deviceFenced(String clinic, String kid) {
        return owned(c -> removedFence(c, clinic, kid));
    }

    void fenceDevice(String clinic, String kid) {
        owned(c -> {
            if (!removedFence(c, clinic, kid)) {
                String value = new String(PortalMyCarlosDeliveryCodec.canonical(java.util.Map.of(
                        "v", 1L, "clinic_id", clinic, "device_key_id", kid)), java.nio.charset.StandardCharsets.UTF_8);
                try (var q = c.prepareStatement("INSERT INTO property (name,value,provider_no) VALUES (?,?,NULL)")) {
                    q.setString(1, removalProperty(kid)); q.setString(2, value); changed(q.executeUpdate());
                }
            }
            try (var q = c.prepareStatement("UPDATE mycarlos_delivery_outbox SET state='fenced',lease_id=NULL,lease_until=NULL,failure_code='device_removed',row_version=row_version+1 WHERE clinic_id=? AND device_key_id=? AND state IN ('staged','waiting')")) {
                q.setString(1, clinic); q.setString(2, kid); q.executeUpdate();
            }
            return null;
        });
    }

    private static boolean removedFence(Connection connection, String clinic, String kid) throws SQLException {
        try (var q = connection.prepareStatement("SELECT value FROM property WHERE name=? AND (provider_no IS NULL OR TRIM(provider_no)='') FOR UPDATE")) {
            q.setString(1, removalProperty(kid));
            try (var r = q.executeQuery()) {
                if (!r.next()) return false;
                var stored = PortalMyCarlosDeliveryCodec.object(PortalMyCarlosDeviceApprovalCodec.utf8(r.getString(1)), 4096);
                PortalMyCarlosDeliveryCodec.fields(stored, java.util.Set.of("v", "clinic_id", "device_key_id"));
                if (r.next() || PortalMyCarlosDeliveryCodec.epoch(stored, "v") != 1
                        || !clinic.equals(PortalMyCarlosDeliveryCodec.text(stored, "clinic_id"))
                        || !kid.equals(PortalMyCarlosDeliveryCodec.text(stored, "device_key_id"))) throw invalid();
                return true;
            }
        }
    }

    private static String removalProperty(String kid) {
        PortalMyCarlosDeviceApprovalCodec.exact32(kid);
        return "mycarlos.delivery.removed." + kid;
    }

    private static Row row(ResultSet r) throws SQLException {
        return new Row(r.getLong("id"), r.getLong("original_id"), r.getString("clinic_id"), r.getString("delivery_id"),
                r.getString("device_key_id"), r.getInt("demographic_no"), r.getLong("accepted_device_id"),
                r.getLong("acceptance_version"), hex(r.getBytes("config_sha256")), new Bytes(r.getBytes("encrypted_snapshot")),
                hex(r.getBytes("snapshot_sha256")), r.getString("state"), r.getString("lease_id"),
                r.getObject("lease_until", Long.class), r.getLong("row_version"), r.getLong("retry_after"),
                r.getBytes("encrypted_receipt") == null ? null : new Bytes(r.getBytes("encrypted_receipt")),
                r.getBytes("receipt_sha256") == null ? null : hex(r.getBytes("receipt_sha256")), r.getString("receipt_jti"));
    }
    private static void nextVersion(Row row) { if (row.version() < 0 || row.version() == Long.MAX_VALUE) throw invalid(); }
    private static long generated(java.sql.PreparedStatement q) throws SQLException {
        try (var rows = q.getGeneratedKeys()) {
            if (!rows.next()) throw invalid();
            long id = rows.getLong(1);
            if (id <= 0 || rows.next()) throw invalid();
            return id;
        }
    }
    private static byte[] raw(String hex) { PortalMyCarlosDeliveryCodec.hash(hex); return java.util.HexFormat.of().parseHex(hex); }
    private static String hex(byte[] bytes) { if (bytes == null || bytes.length != 32) throw invalid(); return java.util.HexFormat.of().formatHex(bytes); }
    private static void changed(int count) { if (count != 1) throw new IllegalStateException("myCarlos delivery state changed"); }
}
