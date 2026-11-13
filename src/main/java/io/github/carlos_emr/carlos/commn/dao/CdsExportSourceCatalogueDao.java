/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * <p>This software is published under the GPL GNU General Public License. This program is free
 * software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * <p>CARLOS EMR Project https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dto.CdsExportLocalSourceDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportUnlinkedSourceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.*;

import jakarta.persistence.*;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Metadata-only coverage read model; unclassified source presence never becomes an empty successful
 * chart.
 */
@Repository
@Transactional(readOnly = true, propagation = Propagation.MANDATORY)
public class CdsExportSourceCatalogueDao {
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger(CdsExportSourceCatalogueDao.class);
    @PersistenceContext private EntityManager entityManager;

    // Identifiers come only from the immutable pinned catalogue, validated as ASCII SQL
    // identifiers.
    @SuppressWarnings("java:S2077")
    public CdsExportTableSchemaDto readSourceSchema() {
        snapshot();
        visibility();
        var catalogue = CdsExportSourceCatalogue.current();
        int caseMode = tableNameCaseMode();
        var engines = engines(caseMode);
        var profile = profile(catalogue, engines.keySet(), caseMode).profile();
        var columns = new HashMap<String, List<CdsExportTableSchemaDto.Column>>();
        for (var row :
                rows(
                        "select"
                            + " TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,CHARACTER_SET_NAME,COLLATION_NAME,NULLIF(GENERATION_EXPRESSION,'')"
                            + " from information_schema.COLUMNS where TABLE_SCHEMA=DATABASE()",
                        65537)) {
            String table = tableKey((String) row[0], caseMode);
            if (!engines.containsKey(table)) continue;
            columns.computeIfAbsent(table, ignored -> new ArrayList<>())
                    .add(
                            new CdsExportTableSchemaDto.Column(
                                    (String) row[1],
                                    (String) row[2],
                                    (String) row[3],
                                    (String) row[4],
                                    (String) row[5],
                                    (String) row[6]));
        }
        var primary = new HashMap<String, List<String>>();
        for (var row :
                rows(
                        "select TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION from"
                            + " information_schema.KEY_COLUMN_USAGE where TABLE_SCHEMA=DATABASE()"
                            + " and CONSTRAINT_NAME='PRIMARY' order by TABLE_NAME,ORDINAL_POSITION",
                        16385)) {
            String table = tableKey((String) row[0], caseMode);
            if (engines.containsKey(table))
                primary.computeIfAbsent(table, ignored -> new ArrayList<>()).add((String) row[1]);
        }
        for (var entry : profile.tables().entrySet()) {
            String table = entry.getKey();
            String key = tableKey(table, caseMode);
            if (!entry.getValue().engine().equals("InnoDB"))
                CdsExportSourceReadLock.requirePinned();
            // Lock the physical table metadata and prove whole-table visibility without returning
            // any values, including account secrets.
            entityManager
                    .createNativeQuery("select d.* from `" + table + "` d limit 0")
                    .setHint("org.hibernate.timeout", 120)
                    .getResultList();
            var fields = columns.get(key);
            if (fields == null) review();
            fields.sort(Comparator.comparing(CdsExportTableSchemaDto.Column::name));
            if (!entry.getValue()
                    .equals(
                            new CdsExportTableSchemaDto(
                                    table,
                                    engines.get(key),
                                    fields,
                                    primary.getOrDefault(key, List.of())))) review();
        }
        return catalogue.scope();
    }

    // Identifiers come only from the immutable pinned catalogue, validated as ASCII SQL
    // identifiers.
    @SuppressWarnings("java:S2077")
    public void requireUnclassifiedSourcesEmpty(int patient, Runnable checkpoint) {
        snapshot();
        if (patient < 1) throw new IllegalArgumentException("CDS_EXPORT_PATIENT_SCOPE_INVALID");
        Objects.requireNonNull(checkpoint).run();
        var catalogue = CdsExportSourceCatalogue.current();
        int caseMode = tableNameCaseMode();
        var profile = profile(catalogue, engines(caseMode).keySet(), caseMode).profile();
        for (String table : new TreeSet<>(profile.tables().keySet())) {
            checkpoint.run();
            if (!profile.tables().get(table).engine().equals("InnoDB"))
                CdsExportSourceReadLock.requirePinned();
            var disposition = catalogue.tables().get(table);
            // DOCUMENT rows are captured and exported as data summaries when Reports is selected
            // (CdsExportDataSummaryReadDao); LISTED rows are only counted for the ReadMe. Neither
            // holds the patient here, exactly like a mapped table of an unselected category.
            if (disposition == CdsExportSourceCatalogue.Disposition.REQUIRE_EMPTY
                    || disposition == CdsExportSourceCatalogue.Disposition.ACCESS_CONTROL) {
                String statement = presenceQuery(table, profile, catalogue);
                // The private finite recipe uses only ASCII identifiers from the immutable,
                // schema-qualified catalogue and compiled parent scopes. Patient data is bound
                // below.
                // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
                var query = entityManager.createNativeQuery(statement);
                if (catalogue.patientScopes().containsKey(table)
                        || CdsExportIndirectPatientScopeReadSupport.supports(table))
                    query.setParameter("patient", patient);
                if (!query.setMaxResults(1)
                        .setHint("org.hibernate.timeout", 120)
                        .getResultList()
                        .isEmpty()) {
                    // A row in a patient-linked table holds only this patient; a populated
                    // table with no patient link blocks every patient and stops the job.
                    boolean patientLinked =
                            catalogue.patientScopes().containsKey(table)
                                    || CdsExportIndirectPatientScopeReadSupport.supports(table);
                    sourceNotice(
                            patientLinked
                                    ? CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED
                                    : CdsExportReviewException.Reason.CLINIC_SOURCE_UNREVIEWED,
                            List.of(table));
                    if (patientLinked)
                        throw new CdsExportReviewException(
                                CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED);
                    review();
                }
            } else if (disposition == CdsExportSourceCatalogue.Disposition.MAPPED_SOURCE
                    && CdsExportIndirectPatientScopeReadSupport.supports(table)) {
                // A mapped inbox table is captured through the chart its rows are linked to, and
                // its unlinked inbox rows belong to no chart (counted by unlinkedSourceRows). A row
                // that is neither may belong to any chart, so it still holds this one.
                String statement =
                        "select 1,1 from `"
                                + table
                                + "` d where "
                                + CdsExportIndirectPatientScopeReadSupport.unattributable(
                                        table, profile);
                // Identifiers only from the pinned catalogue and the compiled parent scopes.
                // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
                if (!entityManager
                        .createNativeQuery(statement)
                        .setMaxResults(1)
                        .setHint("org.hibernate.timeout", 120)
                        .getResultList()
                        .isEmpty()) {
                    sourceNotice(
                            CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED,
                            List.of(table));
                    throw new CdsExportReviewException(
                            CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED);
                }
            }
        }
        checkpoint.run();
    }

    /**
     * Rows of the reviewed inbox tables that are linked to no patient: a lab or HRM report the
     * inbox has not matched to a chart yet, or the routing and reviewers of an incoming document
     * not yet filed. They are part of no chart, so no patient is held for them; the job counts
     * them per table so their exclusion is visible to the operator instead of silent. Counts
     * only, never identifiers or values.
     */
    // Identifiers come only from the immutable pinned catalogue and the compiled parent scopes.
    @SuppressWarnings("java:S2077")
    public List<CdsExportUnlinkedSourceDto> unlinkedSourceRows() {
        snapshot();
        var catalogue = CdsExportSourceCatalogue.current();
        int caseMode = tableNameCaseMode();
        var profile = profile(catalogue, engines(caseMode).keySet(), caseMode).profile();
        var result = new ArrayList<CdsExportUnlinkedSourceDto>();
        for (String table : CdsExportIndirectPatientScopeReadSupport.unlinkedTables()) {
            if (!profile.tables().containsKey(table)) continue;
            if (!profile.tables().get(table).engine().equals("InnoDB")) review();
            // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
            Object value =
                    entityManager
                            .createNativeQuery(
                                    "select count(*) from `"
                                            + table
                                            + "` d where "
                                            + CdsExportIndirectPatientScopeReadSupport.unlinked(
                                                    table, profile))
                            .setHint("org.hibernate.timeout", 120)
                            .getSingleResult();
            if (!(value instanceof Number number)) {
                review();
                continue;
            }
            long rows = number.longValue();
            if (rows < 0) review();
            if (rows > 0) result.add(new CdsExportUnlinkedSourceDto(table, rows));
        }
        return List.copyOf(result);
    }

    /** Patient ownership is a reviewed read scope, never approval to omit a selected clinical row. */
    private static String presenceQuery(
            String table,
            CdsExportSourceCatalogue.Profile profile,
            CdsExportSourceCatalogue.Snapshot catalogue) {
        String query = "select 1,1 from `" + table + "` d";
        var owners = catalogue.patientScopes().get(table);
        if (owners != null) {
            var clauses = new ArrayList<String>();
            for (String owner : owners) {
                var column =
                        profile.tables().get(table).columns().stream()
                                .filter(c -> c.name().equals(owner))
                                .findFirst()
                                .orElseThrow();
                String value = "d.`" + owner + "`";
                // Numeric text aliases cannot disappear behind an indexed canonical-string
                // comparison.
                if (column.type().startsWith("varchar("))
                    value = "CAST(" + value + " AS DECIMAL(65,0))";
                clauses.add(value + "=:patient");
            }
            query += " where (" + String.join(" or ", clauses) + ")";
        } else if (CdsExportIndirectPatientScopeReadSupport.supports(table)) {
            query += " where " + CdsExportIndirectPatientScopeReadSupport.predicate(table, profile);
        }
        var remainder = catalogue.clinicalRemainders().get(table);
        if (remainder != null) {
            var clauses = new ArrayList<String>();
            for (String column : remainder)
                clauses.add(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue
                                .remainderPresent(table, column));
            query += " and (" + String.join(" or ", clauses) + ")";
        }
        return query;
    }

    /** A filtered metadata catalogue cannot establish that no unreadable clinical table exists. */
    private void visibility() {
        String grantee =
                "concat('''',substring_index(current_user(),'@',1),'''@''',substring_index(current_user(),'@',-1),'''')";
        var schema =
                rows(
                        "select 1,1 from information_schema.SCHEMA_PRIVILEGES where"
                            + " TABLE_SCHEMA=DATABASE() and PRIVILEGE_TYPE='SELECT' and GRANTEE="
                                + grantee,
                        2);
        if (schema.isEmpty()
                && rows(
                                "select 1,1 from information_schema.USER_PRIVILEGES where"
                                        + " PRIVILEGE_TYPE='SELECT' and GRANTEE="
                                        + grantee,
                                2)
                        .isEmpty()) review();
    }

    private int tableNameCaseMode() {
        Object value =
                entityManager
                        .createNativeQuery("select @@lower_case_table_names")
                        .getSingleResult();
        if (!(value instanceof Number)) review();
        int mode = ((Number) value).intValue();
        if (mode < 0 || mode > 2) review();
        return mode;
    }

    // MariaDB modes 1 and 2 compare table identifiers without case. Column spellings and all
    // structural metadata remain exact. SQL identifiers still come only from the pinned profile.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification =
                    "ASCII SQL identifiers folded only according to MariaDB's configured table-name"
                            + " case mode")
    private static String tableKey(String table, int caseMode) {
        if (table == null || !table.matches("[A-Za-z_][A-Za-z0-9_]*")) review();
        return caseMode == 0 ? table : table.toLowerCase(Locale.ROOT);
    }

    private Map<String, String> engines(int caseMode) {
        var result = new HashMap<String, String>();
        for (var row :
                rows(
                        "select TABLE_NAME,ENGINE from information_schema.TABLES where"
                                + " TABLE_SCHEMA=DATABASE() and TABLE_TYPE='BASE TABLE'",
                        1026)) {
            String table = tableKey((String) row[0], caseMode);
            if (table.equals("flyway_schema_history")) continue;
            if (result.putIfAbsent(table, (String) row[1]) != null) review();
        }
        return result;
    }

    /**
     * Base tables outside the matched pinned physical profile and outside the reviewed list of
     * package-operational local tables, with MariaDB's row estimate. They have no reviewed
     * structure or patient ownership, so they are never exported; callers list them in the
     * protected diagnostics and the encrypted ReadMe instead of letting them pass silently.
     */
    // Identifiers come from information_schema and are validated as ASCII SQL identifiers.
    @SuppressWarnings("java:S2077")
    public List<CdsExportLocalSourceDto> unreviewedLocalSources() {
        snapshot();
        var catalogue = CdsExportSourceCatalogue.current();
        int caseMode = tableNameCaseMode();
        var extras = profile(catalogue, engines(caseMode).keySet(), caseMode).extras();
        var estimates = new HashMap<String, Long>();
        for (var row :
                rows(
                        "select TABLE_NAME,TABLE_ROWS from information_schema.TABLES where"
                                + " TABLE_SCHEMA=DATABASE() and TABLE_TYPE='BASE TABLE'",
                        1026)) {
            String table = tableKey((String) row[0], caseMode);
            if (extras.contains(table))
                estimates.put((String) row[0], row[1] == null ? 0L : ((Number) row[1]).longValue());
        }
        var result = new ArrayList<CdsExportLocalSourceDto>();
        for (var entry : new TreeMap<>(estimates).entrySet()) {
            String table = entry.getKey();
            tableKey(table, caseMode);
            // The name comes from information_schema and matched the ASCII identifier pattern above.
            // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
            boolean populated =
                    !entityManager
                            .createNativeQuery("select 1 from `" + table + "` limit 1")
                            .setHint("org.hibernate.timeout", 120)
                            .getResultList()
                            .isEmpty();
            if (populated)
                result.add(new CdsExportLocalSourceDto(table, Math.max(1, entry.getValue())));
        }
        return List.copyOf(result);
    }

    private record Match(CdsExportSourceCatalogue.Profile profile, Set<String> extras) {}

    /**
     * Every table of exactly one pinned profile must exist with its reviewed structure. Additional
     * local tables do not choose or block a profile; they are reported by {@link
     * #unreviewedLocalSources()}. A missing profile table, or two profiles fitting equally, still
     * requires review because the reviewed physical contract no longer describes the database.
     */
    private static Match profile(
            CdsExportSourceCatalogue.Snapshot catalogue, Set<String> actual, int caseMode) {
        var operational = new HashSet<String>();
        for (String name : catalogue.localOperationalTables().keySet())
            operational.add(tableKey(name, caseMode));
        Match best = null;
        boolean tied = false;
        var closestMissing = new TreeSet<String>();
        String closestName = null;
        for (var profile : catalogue.profiles()) {
            var keys = new HashSet<String>();
            for (String name : profile.tables().keySet()) {
                if (!keys.add(tableKey(name, caseMode))) review();
            }
            var missing = new TreeSet<>(keys);
            missing.removeAll(actual);
            if (!missing.isEmpty()) {
                if (closestName == null || missing.size() < closestMissing.size()) {
                    closestName = profile.name();
                    closestMissing = missing;
                }
                continue;
            }
            var extras = new HashSet<>(actual);
            extras.removeAll(keys);
            extras.removeAll(operational);
            if (best == null || extras.size() < best.extras().size()) {
                best = new Match(profile, Set.copyOf(extras));
                tied = false;
            } else if (extras.size() == best.extras().size()) tied = true;
        }
        if (best == null) {
            // Names from the pinned physical contract only; no values.
            LOG.warn(
                    "CDS_EXPORT_SOURCE_PROFILE_TABLES_MISSING profile={} tables={}",
                    closestName,
                    closestMissing.size() > 20
                            ? List.copyOf(closestMissing).subList(0, 20) + " +" + (closestMissing.size() - 20)
                            : closestMissing);
            sourceNotice(
                    CdsExportReviewException.Reason.CLINIC_SOURCE_UNREVIEWED,
                    List.copyOf(closestMissing));
            review();
        }
        if (tied) {
            LOG.warn("CDS_EXPORT_SOURCE_PROFILE_AMBIGUOUS");
            review();
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String statement, int limit) {
        var values =
                (List<Object[]>)
                        entityManager
                                .createNativeQuery(statement)
                                .setMaxResults(limit)
                                .setHint("org.hibernate.timeout", 120)
                                .getResultList();
        if (values.size() >= limit) review();
        return values;
    }

    private static void snapshot() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("CDS_EXPORT_READ_CAPTURE_TRANSACTION_REQUIRED");
    }

    /** Schema-level problems concern every patient, so they stop the job rather than hold one. */
    private static void review() {
        throw new CdsExportReviewException(
                CdsExportReviewException.Reason.CLINIC_SOURCE_UNREVIEWED);
    }

    /**
     * Names the blocking tables on the safe technical channel, which the CLI prints to stderr
     * and the web application writes to its log (census D5: nothing named the table). Only table
     * names from the pinned catalogue or information_schema are written; never values or rows.
     */
    private static void sourceNotice(CdsExportReviewException.Reason reason, List<String> tables) {
        var names = new ArrayList<String>();
        for (String table : tables) {
            if (names.size() == 20) break;
            if (table != null && table.matches("[A-Za-z0-9_$]{1,64}")) names.add('"' + table + '"');
        }
        // The named safe technical channel, as CdsExportExecutionService writes it.
        org.apache.logging.log4j.LogManager.getLogger("carlos.cds.safeTechnical")
                .error(
                        "CDS_EXPORT_SOURCE: {\"code\":\"{}\",\"tables\":[{}],\"more\":{}}",
                        reason.code(),
                        String.join(",", names),
                        Math.max(0, tables.size() - names.size()));
    }
}
