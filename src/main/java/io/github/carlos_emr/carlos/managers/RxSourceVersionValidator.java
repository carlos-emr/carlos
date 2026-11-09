/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;

/** Locks the physical source rows, then compares the original versions carried by an editor. */
@Service
@Transactional
public class RxSourceVersionValidator {
    private final JdbcTemplate jdbc;
    public RxSourceVersionValidator(DataSource source) { jdbc = new JdbcTemplate(source); }

    public static final class Conflict extends RuntimeException {
        public Conflict() { super("The prescription changed. Reload and review the current record before saving."); }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void checkSources(int patient, Map<Integer, Long> versions) {
        for (var entry : new TreeMap<>(versions).entrySet()) {
            var stored = jdbc.queryForList("SELECT row_version FROM drugs WHERE drugid=? "
                    + "AND demographic_no=? FOR UPDATE", Long.class, entry.getKey(), patient);
            if (stored.size() != 1 || !Objects.equals(stored.getFirst(), entry.getValue())) throw new Conflict();
        }
    }

    public record CurrentForm(String form, long version) { }

    @Transactional(readOnly = true)
    public CurrentForm currentForm(int patient, int drug) {
        var rows = jdbc.query("SELECT drug_form,row_version FROM drugs WHERE drugid=? AND demographic_no=?",
                (rs, row) -> new CurrentForm(rs.getString(1), rs.getLong(2)), drug, patient);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public long updateForm(String provider, int patient, int drug, Long original, String form) {
        var versions = jdbc.queryForList("SELECT row_version FROM drugs WHERE drugid=? "
                + "AND demographic_no=? FOR UPDATE", Long.class, drug, patient);
        if (versions.size() != 1 || original != null && !original.equals(versions.getFirst())) throw new Conflict();
        long version = versions.getFirst();
        if (jdbc.update("UPDATE drugs SET drug_form=?,row_version=row_version+1,lastUpdateDate=? "
                + "WHERE drugid=? AND demographic_no=? AND row_version=?", form, new java.sql.Timestamp(System.currentTimeMillis()), drug, patient, version) != 1) throw new Conflict();
        LogAction.addLogStrict(provider, "update", LogConst.CON_MEDICATION, "drugid=" + drug, "", Integer.toString(patient), "drug_form");
        return Math.addExact(version, 1);
    }

    /** Exact no-op retries are idempotent; changed notes require the original displayed version. */
    public long updateComment(String provider, int patient, int script, Long original, String originalComment, boolean hasOriginalComment, String comment) {
        return updateCommentLocked(provider, patient, script, original, originalComment, hasOriginalComment, null, comment);
    }

    public long updateCommentDigest(String provider, int patient, int script, Long original, String digest, String comment) {
        if (digest == null || !digest.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid note digest");
        return updateCommentLocked(provider, patient, script, original, null, false, digest, comment);
    }

    private long updateCommentLocked(String provider, int patient, int script, Long original, String originalComment,
            boolean hasOriginalComment, String digest, String comment) {
        var rows = jdbc.query("SELECT row_version,rx_comments FROM prescription WHERE script_no=? "
                + "AND demographic_no=? FOR UPDATE", (rs, row) -> new Object[]{rs.getLong(1), rs.getString(2)}, script, patient);
        if (rows.size() != 1) throw new Conflict();
        long version = (Long) rows.getFirst()[0];
        // The editor renders a SQL NULL as an empty textarea. Compare the original display,
        // otherwise the first notes edit on a newly saved prescription always conflicts.
        String displayedComment = rows.getFirst()[1] == null ? "" : (String) rows.getFirst()[1];
        if (Objects.equals(comment, displayedComment)) return version;
        if (original != null && original.longValue() != version
                || hasOriginalComment && !Objects.equals(originalComment, displayedComment)
                || digest != null && !digest.equals(commentDigest(displayedComment))) throw new Conflict();
        if (jdbc.update("UPDATE prescription SET rx_comments=?,row_version=row_version+1,lastUpdateDate=? "
                + "WHERE script_no=? AND demographic_no=? AND row_version=?", comment, new java.sql.Timestamp(System.currentTimeMillis()), script, patient, version) != 1) throw new Conflict();
        LogAction.addLogStrict(provider, "update", LogConst.CON_PRESCRIPTION, Integer.toString(script), "", Integer.toString(patient), "comments");
        return Math.addExact(version, 1);
    }

    public static String commentDigest(String comment) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest((comment == null ? "" : comment).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static Long originalVersion(String value) {
        if (value == null) return null; // Compatibility: older clients have no original-version contract.
        if (!value.matches("[0-9]{1,18}")) throw new IllegalArgumentException("Invalid original version");
        return Long.valueOf(value);
    }
}
