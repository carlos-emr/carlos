/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryCodec.invalid;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Jdbc;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Statement;
import java.util.Objects;
import org.hibernate.jdbc.ReturningWork;

/** Exact encrypted pages and the durable contiguous clinic checkpoint share one owning transaction. */
final class PortalMyCarlosDeliveryEventDao {
    private final EntityManagerFactory factory;
    PortalMyCarlosDeliveryEventDao(EntityManagerFactory factory) { this.factory = Objects.requireNonNull(factory); }
    private <T> T owned(ReturningWork<T> work) { return Jdbc.owned(factory, work); }
    record Checkpoint(long through, long acknowledged, String writerId, Long writerUntil) {}

    Checkpoint checkpoint(String clinic) {
        return owned(c -> {
            try (var create = c.prepareStatement("INSERT INTO mycarlos_delivery_checkpoint (clinic_id) VALUES (?) ON DUPLICATE KEY UPDATE clinic_id=VALUES(clinic_id)")) {
                create.setString(1, clinic); create.executeUpdate();
            }
            try (var q = c.prepareStatement("SELECT committed_through,acknowledged_through,writer_id,writer_until FROM mycarlos_delivery_checkpoint WHERE clinic_id=? FOR UPDATE")) {
                q.setString(1, clinic);
                try (var r = q.executeQuery()) {
                    if (!r.next()) throw invalid();
                    return new Checkpoint(r.getLong(1), r.getLong(2), r.getString(3), r.getObject(4, Long.class));
                }
            }
        });
    }

    boolean hasCollectedData(String clinic) {
        return owned(c -> {
            try (var q = c.prepareStatement("SELECT 1 FROM mycarlos_delivery_event_page WHERE clinic_id=? LIMIT 1")) {
                q.setString(1, clinic); try (var r = q.executeQuery()) { return r.next(); }
            }
        });
    }

    void writer(String clinic, String token, long until) {
        owned(c -> {
            try (var q = c.prepareStatement("UPDATE mycarlos_delivery_checkpoint SET writer_id=?,writer_until=? WHERE clinic_id=?")) {
                q.setString(1, token); q.setLong(2, until); q.setString(3, clinic); changed(q.executeUpdate());
            }
            return null;
        });
    }

    long keepPage(String clinic, long after, String hash, byte[] encrypted, long now, String failure) {
        if (failure != null && !java.util.Set.of("invalid_page", "unknown_binding", "invalid_proof", "event_gap").contains(failure)) throw invalid();
        return owned(c -> {
            try (var lookup = c.prepareStatement("SELECT id FROM mycarlos_delivery_event_page WHERE clinic_id=? AND page_sha256=? FOR UPDATE")) {
                lookup.setString(1, clinic); lookup.setBytes(2, raw(hash));
                try (var r = lookup.executeQuery()) {
                    if (r.next()) {
                        // Exact malformed bodies can recur at a later request cursor. Retain
                        // their first observation once; stored after_sequence is not cursor authority.
                        return r.getLong(1);
                    }
                }
            }
            try (var q = c.prepareStatement("INSERT INTO mycarlos_delivery_event_page (clinic_id,after_sequence,page_sha256,encrypted_page,collected_at,failure_code) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                q.setString(1, clinic); q.setLong(2, after); q.setBytes(3, raw(hash)); q.setBytes(4, encrypted);
                q.setLong(5, now); q.setString(6, failure); changed(q.executeUpdate());
                try (var r = q.getGeneratedKeys()) { if (!r.next()) throw invalid(); return r.getLong(1); }
            }
        });
    }

    void quarantinePage(long id, String reason) {
        if (!java.util.Set.of("unknown_binding", "invalid_proof", "event_gap").contains(reason)) throw invalid();
        owned(c -> {
            try (var q = c.prepareStatement("UPDATE mycarlos_delivery_event_page SET failure_code=? WHERE id=?")) {
                q.setString(1, reason); q.setLong(2, id);
                int changed = q.executeUpdate();
                if (changed == 0) {
                    try (var check = c.prepareStatement("SELECT failure_code FROM mycarlos_delivery_event_page WHERE id=? FOR UPDATE")) {
                        check.setLong(1, id); try (var r = check.executeQuery()) {
                            if (!r.next() || !reason.equals(r.getString(1))) throw invalid();
                        }
                    }
                } else changed(changed);
            }
            return null;
        });
    }

    void event(String clinic, PortalMyCarlosDeliveryClient.Event event, long pageId, boolean proofVerified) {
        owned(c -> {
            try (var q = c.prepareStatement("INSERT INTO mycarlos_delivery_event (clinic_id,sequence,page_id,page_ordinal,kind,verified) VALUES (?,?,?,?,?,?)")) {
                q.setString(1, clinic); q.setLong(2, event.sequence()); q.setLong(3, pageId);
                q.setInt(4, event.ordinal()); q.setString(5, event.kind()); q.setBoolean(6, proofVerified); changed(q.executeUpdate());
            }
            return null;
        });
    }

    void commitThrough(String clinic, String writer, long prior, long through) {
        if (through < prior) throw invalid();
        if (through == prior) return; // No-op prefix must also work with affected-row JDBC semantics.
        owned(c -> {
            try (var q = c.prepareStatement("UPDATE mycarlos_delivery_checkpoint SET committed_through=? WHERE clinic_id=? AND writer_id=? AND committed_through=?")) {
                q.setLong(1, through); q.setString(2, clinic); q.setString(3, writer); q.setLong(4, prior);
                changed(q.executeUpdate());
            }
            return null;
        });
    }

    void acknowledged(String clinic, String writer, long through) {
        owned(c -> {
            try (var q = c.prepareStatement("UPDATE mycarlos_delivery_checkpoint SET acknowledged_through=?,writer_id=NULL,writer_until=NULL WHERE clinic_id=? AND writer_id=? AND committed_through=? AND acknowledged_through<=?")) {
                q.setLong(1, through); q.setString(2, clinic); q.setString(3, writer);
                q.setLong(4, through); q.setLong(5, through); changed(q.executeUpdate());
            }
            return null;
        });
    }
    void releaseWriter(String clinic, String writer) {
        owned(c -> {
            try (var q = c.prepareStatement("UPDATE mycarlos_delivery_checkpoint SET writer_id=NULL,writer_until=NULL WHERE clinic_id=? AND writer_id=?")) {
                q.setString(1, clinic); q.setString(2, writer); changed(q.executeUpdate());
            }
            return null;
        });
    }

    private static byte[] raw(String hash) { PortalMyCarlosDeliveryCodec.hash(hash); return java.util.HexFormat.of().parseHex(hash); }
    private static void changed(int count) { if (count != 1) throw new IllegalStateException("myCarlos delivery checkpoint changed"); }
}
