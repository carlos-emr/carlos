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

import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
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
        var profile = profile(catalogue, engines.keySet(), caseMode);
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
        var profile = profile(catalogue, engines(caseMode).keySet(), caseMode);
        for (String table : new TreeSet<>(profile.tables().keySet())) {
            checkpoint.run();
            if (!profile.tables().get(table).engine().equals("InnoDB"))
                CdsExportSourceReadLock.requirePinned();
            var disposition = catalogue.tables().get(table);
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
                    // A name from the pinned physical catalogue, with no clinical values or row
                    // identifiers.
                    LOG.warn("CDS_EXPORT_SOURCE_CATALOGUE_REQUIRES_REVIEW table={}", table);
                    review();
                }
            }
        }
        checkpoint.run();
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
                clauses.add("COALESCE(OCTET_LENGTH(d.`" + column + "`),0)>0");
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

    private static CdsExportSourceCatalogue.Profile profile(
            CdsExportSourceCatalogue.Snapshot catalogue, Set<String> actual, int caseMode) {
        var matches =
                catalogue.profiles().stream()
                        .filter(profile -> matches(profile.tables().keySet(), actual, caseMode))
                        .toList();
        if (matches.size() != 1) review();
        return matches.getFirst();
    }

    private static boolean matches(Set<String> expected, Set<String> actual, int caseMode) {
        var keys = new HashSet<String>();
        for (String name : expected) {
            if (!keys.add(tableKey(name, caseMode))) review();
        }
        return keys.equals(actual);
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

    private static void review() {
        throw new CdsExportReviewException(
                CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED);
    }
}
