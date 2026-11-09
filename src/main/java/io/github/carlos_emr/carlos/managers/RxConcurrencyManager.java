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
public class RxConcurrencyManager {
    private final JdbcTemplate jdbc;
    public RxConcurrencyManager(DataSource source) { jdbc = new JdbcTemplate(source); }

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

    public long updateForm(String provider, int patient, int drug, Long original, String form) {
        var versions = jdbc.queryForList("SELECT row_version FROM drugs WHERE drugid=? "
                + "AND demographic_no=? FOR UPDATE", Long.class, drug, patient);
        if (versions.size() != 1 || original != null && !original.equals(versions.getFirst())) throw new Conflict();
        long version = versions.getFirst();
        if (jdbc.update("UPDATE drugs SET drug_form=?,row_version=row_version+1,lastUpdateDate=CURRENT_TIMESTAMP "
                + "WHERE drugid=? AND demographic_no=? AND row_version=?", form, drug, patient, version) != 1) throw new Conflict();
        LogAction.addLogStrict(provider, "update", LogConst.CON_MEDICATION, "drugid=" + drug, "", Integer.toString(patient), "drug_form");
        return Math.addExact(version, 1);
    }

    /** Exact no-op retries are idempotent; changed notes require the original displayed version. */
    public long updateComment(String provider, int patient, int script, Long original, String originalComment, boolean hasOriginalComment, String comment) {
        var rows = jdbc.query("SELECT row_version,rx_comments FROM prescription WHERE script_no=? "
                + "AND demographic_no=? FOR UPDATE", (rs, row) -> new Object[]{rs.getLong(1), rs.getString(2)}, script, patient);
        if (rows.size() != 1) throw new Conflict();
        long version = (Long) rows.getFirst()[0];
        if (Objects.equals(comment, rows.getFirst()[1])) return version;
        if (original != null && original.longValue() != version
                || hasOriginalComment && !Objects.equals(originalComment, rows.getFirst()[1])) throw new Conflict();
        if (jdbc.update("UPDATE prescription SET rx_comments=?,row_version=row_version+1,lastUpdateDate=CURRENT_TIMESTAMP "
                + "WHERE script_no=? AND demographic_no=? AND row_version=?", comment, script, patient, version) != 1) throw new Conflict();
        LogAction.addLogStrict(provider, "update", LogConst.CON_PRESCRIPTION, Integer.toString(script), "", Integer.toString(patient), "comments");
        return Math.addExact(version, 1);
    }

    public static Long originalVersion(String value) {
        if (value == null) return null; // Compatibility: older clients have no original-version contract.
        if (!value.matches("[0-9]{1,18}")) throw new IllegalArgumentException("Invalid original version");
        return Long.valueOf(value);
    }
}
