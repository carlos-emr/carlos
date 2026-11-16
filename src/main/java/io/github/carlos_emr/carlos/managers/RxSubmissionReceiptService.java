/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable evidence of exactly which rendered draft versions a clinical transaction consumed. */
@Service
public class RxSubmissionReceiptService {
    private final JdbcTemplate jdbc;
    public RxSubmissionReceiptService(DataSource source) { jdbc = new JdbcTemplate(source); }

    public record Receipt(Integer scriptId, String outcome) { }

    /** Pending attempts survive a process crash; they never authorize an automatic second prescription. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Receipt find(String provider, int patient, String token) {
        return lookup(provider, patient, token);
    }

    /**
     * The receipt query itself. It runs in the transaction of the public method that calls it:
     * a call from one transactional method of this class to another does not pass through the
     * transaction proxy, so the shared read is kept out of any annotated method.
     */
    private Receipt lookup(String provider, int patient, String token) {
        if (token == null) return null;
        var ids = jdbc.query("SELECT prescription_id,outcome FROM rx_submission_receipt "
                + "WHERE provider_no=? AND demographic_no=? AND submission_hash=? AND outcome <> 'FAILED'",
                (rs, row) -> new Receipt((Integer) rs.getObject(1), rs.getString(2)), provider, patient, token);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /** A durable intent precedes clinical work, including the first attempt after a restart. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean start(String provider, int patient, String token, String attempt, String ip) {
        validate(provider, patient, token);
        try {
            jdbc.update("INSERT INTO rx_submission_receipt(submission_hash,provider_no,demographic_no,outcome) "
                    + "VALUES (?,?,?,'FAILED')", token, provider, patient);
        } catch (org.springframework.dao.DuplicateKeyException existing) {
            // Only the exact primary-key duplicate is expected; the subsequent lock reads its state.
        }
        // The row lock also serializes identical submissions from separate application instances.
        String outcome = jdbc.queryForObject("SELECT outcome FROM rx_submission_receipt "
                + "WHERE submission_hash=? AND provider_no=? AND demographic_no=? FOR UPDATE",
                String.class, token, provider, patient);
        if (!"FAILED".equals(outcome)) return false;
        jdbc.update("UPDATE rx_submission_receipt SET outcome='PENDING',attempt_id=?,lastUpdateUser=?,lastUpdateDate=? WHERE submission_hash=? "
                + "AND provider_no=? AND demographic_no=?", attempt, provider, new java.sql.Timestamp(System.currentTimeMillis()), token, provider, patient);
        io.github.carlos_emr.carlos.log.LogAction.addLogStrict(provider, io.github.carlos_emr.carlos.log.LogConst.SAVE_ATTEMPT,
                io.github.carlos_emr.carlos.log.LogConst.CON_PRESCRIPTION, token, ip, Integer.toString(patient), "");
        return true;
    }

    /** Must join the prescription transaction: SAVED cannot outlive rolled-back clinical rows or audits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String provider, int patient, String token, int script) {
        validate(provider, patient, token);
        if (script <= 0) throw new IllegalArgumentException("Invalid prescription receipt");
        int rows = jdbc.update("UPDATE rx_submission_receipt SET prescription_id=?,outcome='SAVED',lastUpdateUser=?,lastUpdateDate=? "
                + "WHERE submission_hash=? AND provider_no=? AND demographic_no=? AND outcome='PENDING'",
                script, provider, new java.sql.Timestamp(System.currentTimeMillis()), token, provider, patient);
        if (rows != 1) throw new IllegalStateException("Prescription receipt was not pending");
    }

    /**
     * Records what the saved request carried, as a second row naming the same script. The receipt
     * identifies a rendered draft by its revisions alone, and a card edited after a lost reply
     * still carries those revisions; only this evidence tells that edit from an exact replay.
     * Must join the prescription transaction, like {@link #record}: evidence of stored content
     * cannot outlive the rows it describes.
     *
     * @param provider     the authenticated prescriber
     * @param patient      the authorized patient
     * @param contentToken the request's {@link #contentFingerprint}
     * @param script       the prescription this transaction wrote
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordContent(String provider, int patient, String contentToken, int script) {
        validate(provider, patient, contentToken);
        if (script <= 0) throw new IllegalArgumentException("Invalid prescription receipt");
        var now = new java.sql.Timestamp(System.currentTimeMillis());
        // The draft's own row is locked by this transaction, so no other save of the same draft
        // can be writing this one; the UPDATE only covers a row left by an older build.
        int rows = jdbc.update("UPDATE rx_submission_receipt SET prescription_id=?,outcome='SAVED',attempt_id=NULL,lastUpdateUser=?,lastUpdateDate=? "
                + "WHERE submission_hash=? AND provider_no=? AND demographic_no=?", script, provider, now, contentToken, provider, patient);
        if (rows == 0) {
            jdbc.update("INSERT INTO rx_submission_receipt(submission_hash,provider_no,demographic_no,prescription_id,outcome,lastUpdateUser,lastUpdateDate) "
                    + "VALUES (?,?,?,?,'SAVED',?,?)", contentToken, provider, patient, script, provider, now);
        }
    }

    /**
     * Whether the saved draft stored exactly the content this token names.
     *
     * @param saved        the draft's own receipt, already found {@code SAVED}
     * @param contentToken the replaying request's {@link #contentFingerprint}; {@code null} never matches
     * @return {@code false} when the content differs or no evidence of it was recorded
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean storedContent(String provider, int patient, Receipt saved, String contentToken) {
        Receipt content = lookup(provider, patient, contentToken);
        return content != null && saved != null && "SAVED".equals(saved.outcome()) && "SAVED".equals(content.outcome())
                && saved.scriptId() != null && saved.scriptId().equals(content.scriptId());
    }

    /** Only a callback failure followed by a confirmed rollback permits the exact draft to retry. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(String provider, int patient, String token, String attempt) {
        jdbc.update("UPDATE rx_submission_receipt SET outcome='FAILED',lastUpdateUser=?,lastUpdateDate=? WHERE submission_hash=? "
                + "AND provider_no=? AND demographic_no=? AND outcome='PENDING' AND attempt_id=?", provider, new java.sql.Timestamp(System.currentTimeMillis()), token, provider, patient, attempt);
    }

    private static void validate(String provider, int patient, String token) {
        if (provider == null || provider.isBlank() || provider.length() > 6 || patient <= 0
                || token == null || !token.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid prescription receipt");
        }
    }

    /**
     * Opaque identity of the fields one request asked to store, bound to the draft it was rendered
     * from. Each name and value is length-framed, so text cannot move between fields unnoticed.
     * Only the digest is kept: it holds no readable medication text, and because the draft token
     * comes from random revisions it cannot be matched against guessed prescriptions.
     *
     * @param draftToken the draft's {@link #fingerprint}
     * @param fields     every submitted field that is not transport plumbing, with all its values
     * @return a 64-character token, or {@code null} when there is no valid draft token
     */
    public static String contentFingerprint(String draftToken, Map<String, String[]> fields) {
        if (draftToken == null || !draftToken.matches("[a-f0-9]{64}") || fields == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(("rx-content\n" + draftToken + '\n').getBytes(StandardCharsets.US_ASCII));
            for (var entry : new TreeMap<>(fields).entrySet()) {
                String[] values = entry.getValue() == null ? new String[0] : entry.getValue();
                frame(digest, entry.getKey());
                digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(values.length).array());
                for (String value : values) frame(digest, value);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void frame(MessageDigest digest, String text) {
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    /** Canonical, bounded, opaque identity; no medication fields or patient identifiers are stored in the digest input log. */
    public static String fingerprint(Map<String, String> revisions) {
        if (revisions == null || revisions.isEmpty() || revisions.size() > 1000) return null;
        StringBuilder canonical = new StringBuilder();
        for (var entry : new TreeMap<>(revisions).entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[0-9]{1,10}")
                    || entry.getValue() == null
                    || !entry.getValue().matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) return null;
            canonical.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
