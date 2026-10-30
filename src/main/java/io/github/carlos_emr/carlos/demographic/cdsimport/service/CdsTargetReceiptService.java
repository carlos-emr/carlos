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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
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
            Map.entry("casemgmt_note_ext", "id"),
            Map.entry("measurements", "id"), Map.entry("measurementsExt", "id"),
            Map.entry("hl7TextMessage", "lab_id"), Map.entry("hl7TextInfo", "id"),
            Map.entry("patientLabRouting", "id"), Map.entry("providerLabRouting", "id"),
            Map.entry("DemographicContact", "id"),
            Map.entry("Contact", "id"), Map.entry("admission", "am_id"),
            Map.entry("demographicExt", "id"), Map.entry("demographiccust", "demographic_no"),
            Map.entry("pharmacyInfo", "recordID"), Map.entry("demographicPharmacy", "id"),
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
            ReceiptReader reader = new ReceiptReader(connection);
            for (var entry : targets.entrySet()) {
                String table = entry.getKey();
                // Several source fields may reference the same physical table in one bounded unit.
                int separator = table.indexOf('#');
                if (separator >= 0) table = table.substring(0, separator);
                add(connection, found, table, entry.getValue(), reader);
                if (table.equals("preventions")) {
                    preventionExtensions(connection, found, entry.getValue(), reader);
                    dates(connection, found, PartialDate.PREVENTION, entry.getValue(), reader);
                } else if (table.equals("drugs")) dates(connection, found, PartialDate.DRUGS, entry.getValue(), reader);
                else if (table.equals("allergies")) dates(connection, found, PartialDate.ALLERGIES, entry.getValue(), reader);
                else if (table.equals("casemgmt_note")) noteLinks(connection, found, entry.getValue(), reader);
            }
            // Publication ownership and unit references are part of completeness, not best-effort cleanup metadata.
            if (run != null && unit != null) fileDependencies(connection, found, run, unit, reader);
            return found;
        });
        try {
            String encoded = JSON.writeValueAsString(Map.of("protocol", 2, "refs", references));
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
            if (document.path("protocol").asInt() != 2 || !references.isArray()
                    || references.isEmpty() || references.size() > MAXIMUM_REFERENCES) return false;
            return entityManager.unwrap(Session.class).doReturningWork(connection -> {
                ReceiptReader reader = new ReceiptReader(connection);
                for (JsonNode reference : references) {
                    String actual = reader.digest(reference.path("table").asText(), reference.path("id").asText());
                    if (actual == null || !actual.equals(reference.path("digest").asText())) return false;
                }
                return true;
            });
        } catch (java.io.IOException | IllegalArgumentException invalid) { return false; }
    }

    private static void dates(Connection connection, List<Reference> references, int table, String id, ReceiptReader reader)
            throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from partial_date where table_name=? and table_id=? order by id")) {
            statement.setInt(1, table); statement.setString(2, id); statement.setMaxRows(65);
            try (ResultSet rows = statement.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    if (++count > 64) throw new CdsImportException("TARGET_DEPENDENCY_COUNT_REQUIRES_REVIEW");
                    add(connection, references, "partial_date", rows.getString(1), reader);
                }
            }
        }
    }

    private static void fileDependencies(Connection connection, List<Reference> references, String run, String unit, ReceiptReader reader)
            throws java.sql.SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "select id,file_id from cds_import_file_reference where run_id=? and unit_key=? order by id")) {
            query.setString(1, run); query.setString(2, unit); query.setMaxRows(65);
            try (ResultSet rows = query.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    if (++count > 64) throw new CdsImportException("TARGET_DEPENDENCY_COUNT_REQUIRES_REVIEW");
                    add(connection, references, "cds_import_file_reference", rows.getString(1), reader);
                    add(connection, references, "cds_import_file", rows.getString(2), reader);
                }
            }
        }
    }

    private static void noteLinks(Connection connection, List<Reference> references, String note, ReceiptReader reader) throws java.sql.SQLException {
        try (PreparedStatement query = connection.prepareStatement("select id from casemgmt_issue_notes where note_id=? order by id")) {
            query.setString(1, note); query.setMaxRows(65);
            try (ResultSet rows = query.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    if (++count > 64) throw new CdsImportException("TARGET_DEPENDENCY_COUNT_REQUIRES_REVIEW");
                    add(connection, references, "casemgmt_issue_notes", note + "_" + rows.getString(1), reader);
                }
            }
        }
    }

    private static void preventionExtensions(Connection connection, List<Reference> references, String id, ReceiptReader reader)
            throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from preventionsExt where prevention_id=? order by id")) {
            statement.setString(1, id); statement.setMaxRows(65);
            try (ResultSet rows = statement.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    if (++count > 64) throw new CdsImportException("TARGET_DEPENDENCY_COUNT_REQUIRES_REVIEW");
                    add(connection, references, "preventionsExt", rows.getString(1), reader);
                }
            }
        }
    }

    private static void add(Connection connection, List<Reference> references, String table, String id, ReceiptReader reader)
            throws java.sql.SQLException {
        if (references.size() >= MAXIMUM_REFERENCES) throw new CdsImportException("TARGET_RECEIPT_TOO_LARGE");
        String hash = reader.digest(table, id);
        if (hash == null) throw new CdsImportException("TARGET_ROW_MISSING");
        references.add(new Reference(table, id, hash));
    }

    /** Actual-driver regression entry point; every value returned by the server has a fixed bound. */
    static String rowDigest(Connection connection, String table, String id) throws java.sql.SQLException {
        return new ReceiptReader(connection).digest(table, id);
    }

    private record Column(String name, String type, int precision, int scale) { }

    /** One bounded schema cache per capture/verification, discarded with its physical connection callback. */
    private static final class ReceiptReader {
        private final Connection connection;
        private final Map<String, List<Column>> columns = new HashMap<>();
        private ReceiptReader(Connection connection) { this.connection = connection; }

        // Only native receipt-catalogue names reach this identifier-only metadata query.
        @SuppressWarnings("java:S2077")
        private List<Column> columns(String table) throws java.sql.SQLException {
            if (table == null || !PRIMARY_KEYS.containsKey(table)
                    && !"ctl_document".equals(table) && !"casemgmt_issue_notes".equals(table)) {
                throw new CdsImportException("TARGET_REFERENCE_INVALID");
            }
            List<Column> existing = columns.get(table);
            if (existing != null) return existing;
            List<Column> found = new ArrayList<>();
            try (var statement = connection.prepareStatement("SELECT * FROM `" + table + "` WHERE 1=0")) {
                statement.setQueryTimeout(30);
                try (var result = statement.executeQuery()) {
                    var metadata = result.getMetaData();
                    if (metadata.getColumnCount() > 512) throw new CdsImportException("TARGET_COLUMN_COUNT_REQUIRES_REVIEW");
                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        String name = metadata.getColumnName(i);
                        if (table.equals("cds_import_file") && name.equalsIgnoreCase("verified_epoch")) continue;
                        found.add(new Column(name, metadata.getColumnTypeName(i), metadata.getPrecision(i), metadata.getScale(i)));
                    }
                }
            }
            if (found.isEmpty()) throw new CdsImportException("TARGET_COLUMNS_MISSING");
            columns.put(table, List.copyOf(found)); return columns.get(table);
        }

        private String digest(String table, String id) throws java.sql.SQLException {
            String key = PRIMARY_KEYS.get(table);
            boolean documentLink = "ctl_document".equals(table), noteLink = "casemgmt_issue_notes".equals(table);
            boolean composite = documentLink || noteLink;
            if ((!composite && key == null) || id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) throw new CdsImportException("TARGET_REFERENCE_INVALID");
            String[] pair = composite ? id.split("_", -1) : null;
            if (composite && (pair.length != 2 || !pair[0].matches("[0-9]{1,19}") || !pair[1].matches("[0-9]{1,19}"))) throw new CdsImportException("TARGET_REFERENCE_INVALID");
            String where = documentLink ? "module='demographic' and module_id=? and document_no=?"
                    : noteLink ? "note_id=? and id=?" : "`" + key + "`=?";
            List<Column> selected = columns(table);
            StringBuilder sql = new StringBuilder("SELECT ");
            for (Column column : selected) {
                if (sql.length() > 7) sql.append(',');
                String name = "`" + column.name().replace("`", "``") + "`";
                sql.append(name).append(" IS NULL,OCTET_LENGTH(CAST(").append(name)
                        .append(" AS BINARY)),SHA2(CAST(").append(name).append(" AS BINARY),256)");
            }
            sql.append(" FROM `").append(table).append("` WHERE ").append(where);
            if (sql.length() > 65536) throw new CdsImportException("TARGET_DIGEST_QUERY_CAPACITY");
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                statement.setString(1, composite ? pair[0] : id);
                if (composite) statement.setString(2, pair[1]);
                statement.setMaxRows(2); statement.setQueryTimeout(30);
                // Table/key names are allowlisted; metadata names escape backticks; row IDs remain bound.
                // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) return null;
                    MessageDigest hash = CdsImportKeys.digest();
                    CdsImportKeys.add(hash, "carlos-native-row-v2");
                    int index = 1;
                    for (Column column : selected) {
                        CdsImportKeys.add(hash, column.name()); CdsImportKeys.add(hash, column.type());
                        CdsImportKeys.add(hash, Integer.toString(column.precision())); CdsImportKeys.add(hash, Integer.toString(column.scale()));
                        if (row.getBoolean(index)) hash.update((byte) 0);
                        else {
                            hash.update((byte) 1);
                            long length = row.getLong(index + 1); String value = row.getString(index + 2);
                            if (length < 0 || value == null || !value.matches("[0-9a-fA-F]{64}")) throw new CdsImportException("TARGET_ROW_HASH_UNAVAILABLE");
                            CdsImportKeys.add(hash, Long.toString(length)); CdsImportKeys.add(hash, value.toLowerCase(java.util.Locale.ROOT));
                        }
                        index += 3;
                    }
                    if (row.next()) throw new CdsImportException("TARGET_REFERENCE_AMBIGUOUS");
                    return HexFormat.of().formatHex(hash.digest());
                }
            }
        }
    }

    private static String digest(String value) {
        return HexFormat.of().formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
