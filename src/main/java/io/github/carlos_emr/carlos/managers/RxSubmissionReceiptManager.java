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
public class RxSubmissionReceiptManager {
    private final JdbcTemplate jdbc;
    public RxSubmissionReceiptManager(DataSource source) { jdbc = new JdbcTemplate(source); }

    public record Receipt(Integer scriptId, String outcome) { }

    /** Pending attempts survive a process crash; they never authorize an automatic second prescription. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Receipt find(String provider, int patient, String token) {
        if (token == null) return null;
        var ids = jdbc.query("SELECT prescription_id,outcome FROM rx_submission_receipt "
                + "WHERE provider_no=? AND demographic_no=? AND submission_hash=? AND outcome <> 'FAILED'",
                (rs, row) -> new Receipt((Integer) rs.getObject(1), rs.getString(2)), provider, patient, token);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /** A durable intent precedes clinical work, including the first attempt after a restart. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean start(String provider, int patient, String token) {
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
        jdbc.update("UPDATE rx_submission_receipt SET outcome='PENDING' WHERE submission_hash=? "
                + "AND provider_no=? AND demographic_no=?", token, provider, patient);
        io.github.carlos_emr.carlos.log.LogAction.addLogStrict(provider, "SAVE_ATTEMPT",
                io.github.carlos_emr.carlos.log.LogConst.CON_PRESCRIPTION, token, "", Integer.toString(patient), "");
        return true;
    }

    /** Must join the prescription transaction: SAVED cannot outlive rolled-back clinical rows or audits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String provider, int patient, String token, int script) {
        validate(provider, patient, token);
        if (script <= 0) throw new IllegalArgumentException("Invalid prescription receipt");
        int rows = jdbc.update("UPDATE rx_submission_receipt SET prescription_id=?,outcome='SAVED' "
                + "WHERE submission_hash=? AND provider_no=? AND demographic_no=? AND outcome='PENDING'",
                script, token, provider, patient);
        if (rows != 1) throw new IllegalStateException("Prescription receipt was not pending");
    }

    /** Only a callback failure followed by a confirmed rollback permits the exact draft to retry. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(String provider, int patient, String token) {
        jdbc.update("UPDATE rx_submission_receipt SET outcome='FAILED' WHERE submission_hash=? "
                + "AND provider_no=? AND demographic_no=? AND outcome='PENDING'", token, provider, patient);
    }

    private static void validate(String provider, int patient, String token) {
        if (provider == null || provider.isBlank() || provider.length() > 6 || patient <= 0
                || token == null || !token.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid prescription receipt");
        }
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
