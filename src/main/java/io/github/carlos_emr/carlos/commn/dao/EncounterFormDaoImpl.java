/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.util.NativeQueryValues;
import org.springframework.stereotype.Repository;

@Repository
public class EncounterFormDaoImpl extends AbstractDaoImpl<EncounterForm> implements EncounterFormDao {

    public EncounterFormDaoImpl() {
        super(EncounterForm.class);
    }

    private String requireTable(String table) {
        if (table == null || !table.matches("[a-zA-Z][a-zA-Z0-9_]*")
                || (!Set.of("form", "formGrowth0_36").contains(table) && findByFormTable(table).isEmpty())) {
            throw new IllegalArgumentException("Unknown encounter form table");
        }
        return table;
    }

    @Override
    public List<HistoryRow> findPatientHistory(String table, String demographicNo, boolean grouped) {
        String trusted = requireTable(table);
        boolean xml = "form".equals(trusted);
        String sql;
        if (xml) {
            sql = "SELECT form_no, demographic_no, CAST(form_date AS DATE), CAST(form_date AS DATE) FROM form WHERE demographic_no=?1 ORDER BY form_no DESC";
        } else if (grouped) {
            // Retain the original per-day grouping and independent ID/timestamp maxima.
            sql = "SELECT max(ID), demographic_no, CAST(formCreated AS DATE), max(formEdited) FROM " + trusted
                    + " WHERE demographic_no=?1 GROUP BY date(formEdited)";
        } else {
            sql = "SELECT ID, demographic_no, CAST(formCreated AS DATE), formEdited FROM " + trusted
                    + " WHERE demographic_no=?1 ORDER BY ID DESC";
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql, Object[].class)
                .setParameter(1, demographicNo).getResultList();
        return rows.stream().map(row -> new HistoryRow(((Number) row[0]).intValue(),
                ((Number) row[1]).intValue(), NativeQueryValues.asDate(row[2]), NativeQueryValues.asDate(row[3]))).toList();
    }

    @Override
    // S2077: requireTable admits only registry-approved ASCII identifiers; patient/name are bound.
    @SuppressWarnings("java:S2077")
    public String findLatestPatientFormId(String table, String demographicNo, String legacyFormName, boolean byEdited) {
        String trusted = requireTable(table);
        boolean xml = "form".equals(trusted);
        String sql = "SELECT " + (xml ? "form_no" : "ID") + " FROM " + trusted + " WHERE demographic_no=?1"
                + (xml ? " AND form_name=?2 ORDER BY form_no DESC" : byEdited ? " ORDER BY formEdited DESC, ID DESC" : " ORDER BY ID DESC");
        Query query = entityManager.createNativeQuery(sql).setParameter(1, demographicNo).setMaxResults(1);
        if (xml) query.setParameter(2, legacyFormName);
        List<?> rows = query.getResultList();
        return rows.isEmpty() ? "0" : rows.get(0).toString();
    }

    @Override
    // S2077: requireTable validates the only variable identifier; the patient value is bound.
    @SuppressWarnings("java:S2077")
    public HistoryRow findNewestPatientHistory(String table, String demographicNo) {
        String trusted = requireTable(table);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("SELECT ID,demographic_no,CAST(formCreated AS DATE),formEdited FROM "
                + trusted + " WHERE demographic_no=?1 ORDER BY ID DESC", Object[].class)
                .setParameter(1, demographicNo).setMaxResults(1).getResultList();
        if (rows.isEmpty()) return null;
        Object[] row = rows.get(0);
        return new HistoryRow(((Number) row[0]).intValue(), ((Number) row[1]).intValue(),
                NativeQueryValues.asDate(row[2]), NativeQueryValues.asDate(row[3]));
    }

    @Override
    public Map<String, String> findPatientRecord(String table, int id, int demographicNo) {
        String trusted = requireTable(table);
        return entityManager.unwrap(org.hibernate.Session.class).doReturningWork(connection -> {
            try (var statement = preparePatientRecordSelect(connection, trusted)) {
                statement.setInt(1, id);
                statement.setInt(2, demographicNo);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) return null;
                    Map<String, String> result = new LinkedHashMap<>();
                    var metadata = rows.getMetaData();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) result.put(metadata.getColumnName(i), rows.getString(i));
                    return result;
                }
            }
        });
    }

    @Override
    // S2077: requireTable validates the only variable identifier; patient/timestamp are bound.
    @SuppressWarnings("java:S2077")
    public boolean importedRowExists(String table, String demographicNo, String edited) {
        String trusted = requireTable(table);
        return !entityManager.createNativeQuery("SELECT ID FROM " + trusted
                        + " WHERE demographic_no=?1 AND formEdited=?2")
                .setParameter(1, demographicNo).setParameter(2, edited).setMaxResults(1).getResultList().isEmpty();
    }

    @Override
    public long insertImportedRow(String table, String demographicNo, String edited, Map<String, String> values) {
        String trusted = requireTable(table);
        // Read the real schema: form tables have different optional columns and defaults.
        // Values stay strings so MariaDB, as with updateString(), owns date/number conversion.
        List<String> columns = entityManager.unwrap(org.hibernate.Session.class).doReturningWork(connection -> {
            try (var statement = prepareFormSchemaSelect(connection, trusted);
                 var rows = statement.executeQuery()) {
                List<String> result = new ArrayList<>();
                var metadata = rows.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) result.add(metadata.getColumnName(i));
                return result;
            }
        });
        Map<String, String> names = new LinkedHashMap<>();
        columns.forEach(column -> names.put(column.toLowerCase(Locale.ROOT), column));
        Map<String, String> supplied = new LinkedHashMap<>();
        for (var entry : values.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (Set.of("id", "demographic_no", "formedited").contains(key)) continue;
            if (!names.containsKey(key)) throw new IllegalArgumentException("Unknown form import column");
            supplied.put(key, entry.getValue());
        }
        supplied.put("demographic_no", demographicNo);
        supplied.put("formedited", edited);
        if (!names.keySet().containsAll(supplied.keySet())) throw new IllegalArgumentException("Invalid form import schema");
        List<String> keys = new ArrayList<>(supplied.keySet());
        // Every identifier comes from the physical metadata of the allowlisted table.
        String fields = String.join(",", keys.stream().map(key -> "`" + names.get(key).replace("`", "``") + "`").toList());
        String binds = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
        return entityManager.unwrap(org.hibernate.Session.class).doReturningWork(connection -> {
            try (var insert = prepareImportedInsert(connection, trusted, fields, binds)) {
                for (int i = 0; i < keys.size(); i++) insert.setString(i + 1, supplied.get(keys.get(i)));
                insert.executeUpdate();
                try (var generated = insert.getGeneratedKeys()) {
                    if (!generated.next()) throw new java.sql.SQLException("Form import returned no generated ID");
                    return generated.getLong(1);
                }
            }
        });
    }

    // FindSecBugs SQL_INJECTION_JDBC / SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING:
    // requireTable restricts the table to ASCII identifiers approved by the form registry;
    // the patient and record identifiers remain JDBC parameters.
    @SuppressFBWarnings(value = {"SQL_INJECTION_JDBC", "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"},
            justification = "Private form SELECT uses requireTable's registry-approved ASCII identifier and binds patient/record values")
    @SuppressWarnings("java:S2077") // Same audited identifier and binding boundary as the FindSecBugs exception.
    private static java.sql.PreparedStatement preparePatientRecordSelect(java.sql.Connection connection,
            String trustedTable) throws java.sql.SQLException {
        return connection.prepareStatement("SELECT * FROM " + trustedTable + " WHERE ID=? AND demographic_no=?");
    }

    // FindSecBugs SQL_INJECTION_JDBC / SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING:
    // requireTable validates the sole variable identifier before this zero-row metadata read.
    @SuppressFBWarnings(value = {"SQL_INJECTION_JDBC", "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"},
            justification = "Private zero-row schema read uses only requireTable's registry-approved ASCII table identifier")
    @SuppressWarnings("java:S2077") // Same audited identifier boundary as the FindSecBugs exception.
    private static java.sql.PreparedStatement prepareFormSchemaSelect(java.sql.Connection connection,
            String trustedTable) throws java.sql.SQLException {
        return connection.prepareStatement("SELECT * FROM " + trustedTable + " WHERE 1=0");
    }

    // FindSecBugs SQL_INJECTION_JDBC / SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING:
    // requireTable validates the table; fields are escaped physical metadata column names,
    // binds contains only generated question marks, and every imported value is bound.
    @SuppressFBWarnings(value = {"SQL_INJECTION_JDBC", "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"},
            justification = "Private insert uses a registry-approved ASCII table, quoted physical metadata columns and generated placeholders; values are bound separately")
    @SuppressWarnings("java:S2077") // Same audited metadata identifiers and bound values as the FindSecBugs exception.
    private static java.sql.PreparedStatement prepareImportedInsert(java.sql.Connection connection,
            String trustedTable, String fields, String binds) throws java.sql.SQLException {
        return connection.prepareStatement("INSERT INTO " + trustedTable + " (" + fields + ") VALUES (" + binds + ")",
                java.sql.Statement.RETURN_GENERATED_KEYS);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<EncounterForm> findAll() {
        Query query = entityManager.createQuery("SELECT x FROM " + modelClass.getSimpleName() + " x");
        List<EncounterForm> results = query.getResultList();
        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<EncounterForm> findAllNotHidden() {
        Query query = entityManager
                .createQuery("SELECT x FROM " + modelClass.getSimpleName() + " x where x.displayOrder!=0");
        List<EncounterForm> results = query.getResultList();
        return results;
    }

    @Override
    public List<EncounterForm> findByFormName(String formName) {
        Query query = entityManager
                .createQuery("select x from " + modelClass.getSimpleName() + " x where x.formName=?1");
        query.setParameter(1, formName);

        @SuppressWarnings("unchecked")
        List<EncounterForm> results = query.getResultList();

        return (results);
    }

    @Override
    public List<EncounterForm> findByFormTable(String formTable) {
        Query query = entityManager
                .createQuery("select x from " + modelClass.getSimpleName() + " x where x.formTable=?1");
        query.setParameter(1, formTable);

        @SuppressWarnings("unchecked")
        List<EncounterForm> results = query.getResultList();

        return (results);
    }

}
