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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReceiptDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Exact bounded native-row receipts, read through the owning transaction's physical connection. */
@Service
public class CdsTargetReceiptService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAXIMUM_REFERENCES = 192;
    private static final Map<String, String> PRIMARY_KEYS = Map.ofEntries(
            Map.entry("demographic", "demographic_no"), Map.entry("drugs", "drugid"),
            Map.entry("allergies", "allergyid"), Map.entry("preventions", "id"),
            Map.entry("preventionsExt", "id"), Map.entry("partial_date", "id"),
            Map.entry("appointment", "appointment_no"), Map.entry("document", "document_no"),
            Map.entry("casemgmt_note", "note_id"), Map.entry("casemgmt_issue", "id"),
            Map.entry("measurements", "id"), Map.entry("measurementsExt", "id"),
            Map.entry("hl7TextMessage", "lab_id"), Map.entry("hl7TextInfo", "id"),
            Map.entry("patientLabRouting", "id"), Map.entry("providerLabRouting", "id"),
            Map.entry("DemographicContact", "id"),
            Map.entry("Contact", "id"), Map.entry("admission", "id"),
            Map.entry("demographicExt", "id"),
            Map.entry("cds_laboratory_result", "id"), Map.entry("cds_import_file", "id"),
            Map.entry("cds_import_file_reference", "id"),
            Map.entry("cds_import_field", "id"));
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    public record Reference(String table, String id, String digest) { }

    /** Actual native and evidence tables, including association tables with composite keys. */
    public static java.util.Set<String> targetTables() {
        java.util.Set<String> tables = new java.util.HashSet<>(PRIMARY_KEYS.keySet());
        tables.add("ctl_document"); tables.add("casemgmt_issue_notes");
        return java.util.Set.copyOf(tables);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CdsImportReceiptDto capture(Map<String, String> targets) {
        return capture(targets, null, null);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CdsImportReceiptDto capture(Map<String, String> targets, String run, String unit) {
        if (targets.isEmpty() || targets.size() > MAXIMUM_REFERENCES) throw new CdsImportException("TARGET_REFERENCE_COUNT_INVALID");
        entityManager.flush();
        List<Reference> references = entityManager.unwrap(Session.class).doReturningWork(connection -> {
            List<Reference> found = new ArrayList<>();
            for (var entry : targets.entrySet()) {
                String table = entry.getKey();
                // Several source fields may reference the same physical table in one bounded unit.
                int separator = table.indexOf('#');
                if (separator >= 0) table = table.substring(0, separator);
                add(connection, found, table, entry.getValue());
                if (table.equals("preventions")) {
                    children(connection, found, "preventionsExt", "prevention_id", entry.getValue());
                    dates(connection, found, PartialDate.PREVENTION, entry.getValue());
                } else if (table.equals("drugs")) dates(connection, found, PartialDate.DRUGS, entry.getValue());
                else if (table.equals("allergies")) dates(connection, found, PartialDate.ALLERGIES, entry.getValue());
                else if (table.equals("casemgmt_note")) noteLinks(connection, found, entry.getValue());
            }
            // Publication ownership and unit references are part of completeness, not best-effort cleanup metadata.
            if (run != null && unit != null) fileDependencies(connection, found, run, unit);
            return found;
        });
        try {
            String encoded = JSON.writeValueAsString(Map.of("protocol", 1, "refs", references));
            if (encoded.length() > 65536) throw new CdsImportException("TARGET_RECEIPT_TOO_LARGE");
            return new CdsImportReceiptDto(encoded, digest(encoded));
        } catch (java.io.IOException failure) { throw new CdsImportException("TARGET_RECEIPT_ENCODING_FAILED"); }
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public boolean verify(CdsImportReceiptDto receipt) {
        if (receipt == null || receipt.targetReferences() == null || receipt.targetReferences().length() > 65536
                || !digest(receipt.targetReferences()).equals(receipt.targetDigest())) return false;
        try {
            JsonNode document = JSON.readTree(receipt.targetReferences());
            JsonNode references = document.path("refs");
            if (document.path("protocol").asInt() != 1 || !references.isArray()
                    || references.isEmpty() || references.size() > MAXIMUM_REFERENCES) return false;
            return entityManager.unwrap(Session.class).doReturningWork(connection -> {
                for (JsonNode reference : references) {
                    String actual = rowDigest(connection, reference.path("table").asText(), reference.path("id").asText());
                    if (actual == null || !actual.equals(reference.path("digest").asText())) return false;
                }
                return true;
            });
        } catch (java.io.IOException | IllegalArgumentException invalid) { return false; }
    }

    private static void dates(Connection connection, List<Reference> references, int table, String id)
            throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from partial_date where table_name=? and table_id=? order by id")) {
            statement.setInt(1, table); statement.setString(2, id); statement.setMaxRows(65);
            try (ResultSet rows = statement.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    if (++count > 64) throw new CdsImportException("TARGET_DEPENDENCY_COUNT_REQUIRES_REVIEW");
                    add(connection, references, "partial_date", rows.getString(1));
                }
            }
        }
    }

    private static void fileDependencies(Connection connection, List<Reference> references, String run, String unit)
            throws java.sql.SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "select id,file_id from cds_import_file_reference where run_id=? and unit_key=? order by id")) {
            query.setString(1, run); query.setString(2, unit); query.setMaxRows(65);
            try (ResultSet rows = query.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    if (++count > 64) throw new CdsImportException("TARGET_DEPENDENCY_COUNT_REQUIRES_REVIEW");
                    add(connection, references, "cds_import_file_reference", rows.getString(1));
                    add(connection, references, "cds_import_file", rows.getString(2));
                }
            }
        }
    }

    private static void noteLinks(Connection connection, List<Reference> references, String note) throws java.sql.SQLException {
        try (PreparedStatement query = connection.prepareStatement("select id from casemgmt_issue_notes where note_id=? order by id")) {
            query.setString(1, note); query.setMaxRows(65);
            try (ResultSet rows = query.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    if (++count > 64) throw new CdsImportException("TARGET_DEPENDENCY_COUNT_REQUIRES_REVIEW");
                    add(connection, references, "casemgmt_issue_notes", note + "_" + rows.getString(1));
                }
            }
        }
    }

    private static void children(Connection connection, List<Reference> references, String table, String foreignKey, String id)
            throws java.sql.SQLException {
        // Table and foreign-key arguments originate only from the fixed call sites above.
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from `" + table + "` where `" + foreignKey + "`=? order by id")) {
            statement.setString(1, id); statement.setMaxRows(65);
            try (ResultSet rows = statement.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    if (++count > 64) throw new CdsImportException("TARGET_DEPENDENCY_COUNT_REQUIRES_REVIEW");
                    add(connection, references, table, rows.getString(1));
                }
            }
        }
    }

    private static void add(Connection connection, List<Reference> references, String table, String id)
            throws java.sql.SQLException {
        if (references.size() >= MAXIMUM_REFERENCES) throw new CdsImportException("TARGET_RECEIPT_TOO_LARGE");
        String hash = rowDigest(connection, table, id);
        if (hash == null) throw new CdsImportException("TARGET_ROW_MISSING");
        references.add(new Reference(table, id, hash));
    }

    private static String rowDigest(Connection connection, String table, String id) throws java.sql.SQLException {
        String key = PRIMARY_KEYS.get(table);
        boolean documentLink = "ctl_document".equals(table);
        boolean noteLink = "casemgmt_issue_notes".equals(table);
        boolean composite = documentLink || noteLink;
        if ((!composite && key == null) || id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new CdsImportException("TARGET_REFERENCE_INVALID");
        }
        String[] pair = composite ? id.split("_", -1) : null;
        if (composite && (pair.length != 2 || !pair[0].matches("[0-9]{1,19}") || !pair[1].matches("[0-9]{1,19}"))) {
            throw new CdsImportException("TARGET_REFERENCE_INVALID");
        }
        String where = documentLink ? "module='demographic' and module_id=? and document_no=?"
                : noteLink ? "note_id=? and id=?" : "`" + key + "`=?";
        try (PreparedStatement statement = connection.prepareStatement("select * from `" + table + "` where " + where)) {
            statement.setString(1, composite ? pair[0] : id);
            if (composite) statement.setString(2, pair[1]);
            statement.setMaxRows(2); statement.setQueryTimeout(30);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                MessageDigest hash = CdsImportKeys.digest();
                var metadata = row.getMetaData();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    // Operational verification may advance after a resume; immutable file identity
                    // (digest/length/location/generation/state) remains part of the clinical receipt.
                    if (table.equals("cds_import_file") && metadata.getColumnName(index).equalsIgnoreCase("verified_epoch")) continue;
                    CdsImportKeys.add(hash, metadata.getColumnName(index));
                    try (Reader value = row.getCharacterStream(index)) {
                        if (value == null) { hash.update((byte) 0); continue; }
                        hash.update((byte) 1);
                        char[] buffer = new char[4096];
                        int count;
                        // Hash fixed-width UTF-16 units so arbitrary JDBC reader chunk boundaries
                        // cannot split a Unicode surrogate pair and change the receipt.
                        while ((count = value.read(buffer)) != -1) {
                            for (int n = 0; n < count; n++) {
                                hash.update((byte) (buffer[n] >>> 8)); hash.update((byte) buffer[n]);
                            }
                        }
                    } catch (java.io.IOException failed) { throw new CdsImportException("TARGET_ROW_READ_FAILED"); }
                    hash.update((byte) 0xff);
                }
                if (row.next()) throw new CdsImportException("TARGET_REFERENCE_AMBIGUOUS");
                return HexFormat.of().formatHex(hash.digest());
            }
        }
    }

    private static String digest(String value) {
        return HexFormat.of().formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
