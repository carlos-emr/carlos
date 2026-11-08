/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.*;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Metadata-only coverage read model; unclassified source presence never becomes an empty successful chart. */
@Repository
@Transactional(readOnly = true, propagation = Propagation.MANDATORY)
public class CdsExportSourceCatalogueDao {
    @PersistenceContext private EntityManager entityManager;
    public CdsExportTableSchemaDto readSourceSchema() {
        snapshot(); visibility(); var catalogue = CdsExportSourceCatalogue.current(); var engines = engines(); var profile = profile(catalogue, engines.keySet());
        var columns = new HashMap<String, List<CdsExportTableSchemaDto.Column>>();
        for (var row : rows("select TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,CHARACTER_SET_NAME,COLLATION_NAME,NULLIF(GENERATION_EXPRESSION,'') "
                + "from information_schema.COLUMNS where TABLE_SCHEMA=DATABASE()", 65537)) {
            String table = (String) row[0]; if (!engines.containsKey(table)) continue;
            columns.computeIfAbsent(table, ignored -> new ArrayList<>()).add(new CdsExportTableSchemaDto.Column((String) row[1], (String) row[2],
                    (String) row[3], (String) row[4], (String) row[5], (String) row[6]));
        }
        var primary = new HashMap<String, List<String>>();
        for (var row : rows("select TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION from information_schema.KEY_COLUMN_USAGE "
                + "where TABLE_SCHEMA=DATABASE() and CONSTRAINT_NAME='PRIMARY' order by TABLE_NAME,ORDINAL_POSITION", 16385)) {
            String table = (String) row[0]; if (engines.containsKey(table)) primary.computeIfAbsent(table, ignored -> new ArrayList<>()).add((String) row[1]);
        }
        for (var entry : profile.tables().entrySet()) {
            String table = entry.getKey();
            if (!entry.getValue().engine().equals("InnoDB")) CdsExportSourceReadLock.requirePinned();
            // Lock the physical table metadata and prove whole-table visibility without returning any values, including account secrets.
            entityManager.createNativeQuery("select d.* from `" + table + "` d limit 0").setHint("org.hibernate.timeout", 120).getResultList();
            var fields = columns.get(table); if (fields == null) review(); fields.sort(Comparator.comparing(CdsExportTableSchemaDto.Column::name));
            if (!entry.getValue().equals(new CdsExportTableSchemaDto(table, engines.get(table), fields, primary.getOrDefault(table, List.of())))) review();
        }
        return catalogue.scope();
    }
    public void requireUnclassifiedSourcesEmpty(int patient, Runnable checkpoint) {
        snapshot(); if (patient < 1) throw new IllegalArgumentException("CDS_EXPORT_PATIENT_SCOPE_INVALID"); Objects.requireNonNull(checkpoint).run();
        var catalogue = CdsExportSourceCatalogue.current(); var profile = profile(catalogue, engines().keySet());
        for (String table : new TreeSet<>(profile.tables().keySet())) {
            checkpoint.run();
            if (!profile.tables().get(table).engine().equals("InnoDB")) CdsExportSourceReadLock.requirePinned();
            if (catalogue.tables().get(table) == CdsExportSourceCatalogue.Disposition.REQUIRE_EMPTY
                    && !entityManager.createNativeQuery("select 1,1 from `" + table + "`")
                            .setMaxResults(1).setHint("org.hibernate.timeout", 120).getResultList().isEmpty()) review();
        }
        checkpoint.run();
    }
    /** A filtered metadata catalogue cannot establish that no unreadable clinical table exists. */
    private void visibility() {
        String grantee = "concat('''',substring_index(current_user(),'@',1),'''@''',substring_index(current_user(),'@',-1),'''')";
        var schema = rows("select 1,1 from information_schema.SCHEMA_PRIVILEGES where TABLE_SCHEMA=DATABASE() and PRIVILEGE_TYPE='SELECT' and GRANTEE=" + grantee, 2);
        if (schema.isEmpty() && rows("select 1,1 from information_schema.USER_PRIVILEGES where PRIVILEGE_TYPE='SELECT' and GRANTEE=" + grantee, 2).isEmpty()) review();
    }
    private Map<String, String> engines() {
        var result = new HashMap<String, String>();
        for (var row : rows("select TABLE_NAME,ENGINE from information_schema.TABLES where TABLE_SCHEMA=DATABASE() and TABLE_TYPE='BASE TABLE'", 1026)) {
            String table = (String) row[0]; if (table.equals("flyway_schema_history")) continue;
            if (result.putIfAbsent(table, (String) row[1]) != null) review();
        }
        return result;
    }
    private static CdsExportSourceCatalogue.Profile profile(CdsExportSourceCatalogue.Snapshot catalogue, Set<String> actual) {
        var matches = catalogue.profiles().stream().filter(profile -> profile.tables().keySet().equals(actual)).toList(); if (matches.size() != 1) review(); return matches.getFirst();
    }
    @SuppressWarnings("unchecked") private List<Object[]> rows(String statement, int limit) {
        var values = (List<Object[]>) entityManager.createNativeQuery(statement).setMaxResults(limit).setHint("org.hibernate.timeout", 120).getResultList();
        if (values.size() >= limit) review(); return values;
    }
    private static void snapshot() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("CDS_EXPORT_READ_CAPTURE_TRANSACTION_REQUIRED");
    }
    private static void review() { throw new CdsExportReviewException(CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED); }
}
