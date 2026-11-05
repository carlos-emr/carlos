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
            try (var statement = connection.prepareStatement("SELECT * FROM " + trusted + " WHERE ID=? AND demographic_no=?")) {
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
        @SuppressWarnings("unchecked")
        List<String> columns = entityManager.createNativeQuery("SELECT COLUMN_NAME FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA=SCHEMA() AND TABLE_NAME=?1 ORDER BY ORDINAL_POSITION", String.class)
                .setParameter(1, trusted).getResultList();
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
        List<String> binds = new ArrayList<>();
        for (int i = 1; i <= keys.size(); i++) binds.add("?" + i);
        Query insert = entityManager.createNativeQuery("INSERT INTO " + trusted + " (" + fields + ") VALUES (" + String.join(",", binds) + ")");
        for (int i = 0; i < keys.size(); i++) insert.setParameter(i + 1, supplied.get(keys.get(i)));
        insert.executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
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
