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
package io.github.carlos_emr.carlos.demographic.cdsexport.support;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.*;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.io.*;
import java.security.*;
import java.util.*;

/** Immutable application-owned coverage rulings, bound to the exact shipped physical schema contracts. */
public final class CdsExportSourceCatalogue {
    public enum Disposition {
        MAPPED_SOURCE,
        OPERATIONAL,
        REFERENCE,
        ACCESS_CONTROL,
        REQUIRE_EMPTY,
        /**
         * Patient records with no faithful CDS element of their own (encounter forms, eForms,
         * consultations, ticklers, messages, consents and similar). Every physical column is
         * captured when Reports is selected and exported as a CARLOS data summary Report
         * (maintainer decision 6). The table keeps a reviewed patient scope, direct or through a
         * parent, and must be named in the Reports capture scope.
         */
        DOCUMENT,
        /**
         * Records of a removed CARLOS feature. Populated patient rows are listed, by table name
         * and row count, in the encrypted package ReadMe; they are never exported and never hold
         * the patient (maintainer decision 20).
         */
        LISTED
    }

    /**
     * The spelling a clinical remainder column holds when nobody recorded anything. The
     * {@code admission.radioDischargeReason} column defaults to {@code '0'}, and no CARLOS screen
     * writes a discharge reason (discharge reasons are lookup codes, such as {@code 50} for an
     * automatic discharge), so {@code '0'} is no reason.
     */
    private static final Map<String, Map<String, String>> NO_REMAINDER =
            Map.of("admission", Map.of("radioDischargeReason", "0"));

    /**
     * SQL test, on alias {@code d}, that a clinical remainder column of {@code table} holds a
     * recorded value: neither NULL nor empty nor the column's spelling of "none". The column
     * names come from the reviewed catalogue only.
     */
    public static String remainderPresent(String table, String column) {
        if (table == null || column == null || !column.matches("[A-Za-z0-9_]{1,64}"))
            throw new IllegalArgumentException("CDS_EXPORT_REMAINDER_COLUMN_INVALID");
        String value = "d.`" + column + "`";
        String present = "COALESCE(OCTET_LENGTH(" + value + "),0)>0";
        String none = NO_REMAINDER.getOrDefault(table, Map.of()).get(column);
        return none == null ? present : "(" + present + " and " + value + "<>'" + none + "')";
    }

    public record Profile(String name, Map<String, CdsExportTableSchemaDto> tables) {
        public Profile {
            tables = Map.copyOf(tables);
        }
    }

    public record Snapshot(
            Map<String, Disposition> tables,
            List<Profile> profiles,
            Map<String, Set<String>> requiredScopes,
            Map<String, List<String>> patientScopes,
            Map<String, List<String>> clinicalRemainders,
            CdsExportTableSchemaDto scope,
            Map<String, String> localOperationalTables) {
        public Snapshot {
            tables = Map.copyOf(tables);
            localOperationalTables = Map.copyOf(localOperationalTables);
            profiles = List.copyOf(profiles);
            var copy = new HashMap<String, Set<String>>();
            requiredScopes.forEach((name, values) -> copy.put(name, Set.copyOf(values)));
            requiredScopes = Map.copyOf(copy);
            patientScopes = copyColumns(patientScopes);
            clinicalRemainders = copyColumns(clinicalRemainders);
        }

        private static Map<String, List<String>> copyColumns(Map<String, List<String>> source) {
            var result = new HashMap<String, List<String>>();
            source.forEach((table, columns) -> result.put(table, List.copyOf(columns)));
            return Map.copyOf(result);
        }

        /** The reviewed tables of one disposition, in a stable order. */
        public SortedSet<String> tables(Disposition disposition) {
            var result = new TreeSet<String>();
            tables.forEach(
                    (table, ruling) -> {
                        if (ruling == disposition) result.add(table);
                    });
            return Collections.unmodifiableSortedSet(result);
        }

        /** The pinned physical structure of a table in the first profile that has it, if any. */
        public Optional<CdsExportTableSchemaDto> physical(String table) {
            for (var profile : profiles) {
                var schema = profile.tables().get(table);
                if (schema != null) return Optional.of(schema);
            }
            return Optional.empty();
        }

        public Set<String> required(Set<CdsCategory> selected) {
            var result = new HashSet<String>();
            for (var category : selected) result.addAll(requiredScopes.get(category.wireName()));
            return Set.copyOf(result);
        }
    }

    private static final ObjectMapper JSON =
            new ObjectMapper(
                    JsonFactory.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .streamReadConstraints(
                                    StreamReadConstraints.builder()
                                            .maxNestingDepth(32)
                                            .maxStringLength(1_048_576)
                                            .build())
                            .build());

    // Snapshot defensively copies its collections; volatile safely publishes that immutable value.
    @SuppressWarnings("java:S3077")
    private static volatile Snapshot cached;

    private CdsExportSourceCatalogue() {}

    public static Snapshot current() {
        var result = cached;
        if (result != null) return result;
        synchronized (CdsExportSourceCatalogue.class) {
            if (cached == null)
                try {
                    cached = load();
                } catch (IOException | RuntimeException failed) {
                    throw new IllegalStateException("CDS_EXPORT_SOURCE_CATALOGUE_INVALID");
                }
            return cached;
        }
    }

    private static Snapshot load() throws IOException {
        byte[] bytes = resource("source-catalogue.json");
        var root = JSON.readTree(bytes);
        if (root.path("format").asInt() != 1
                || !root.path("tables").isObject()
                || root.path("tables").size() > 1024
                || root.path("tables").isEmpty()
                || !root.path("profiles").isObject()
                || root.path("profiles").size() != 2) invalid();
        var rulings = new HashMap<String, Disposition>();
        for (var field : root.path("tables").properties()) {
            identifier(field.getKey());
            rulings.put(field.getKey(), Disposition.valueOf(field.getValue().asText()));
        }
        var profiles = new ArrayList<Profile>();
        var union = new HashSet<String>();
        for (String province : List.of("on", "bc")) {
            byte[] physical = resource("physical-schema/" + province + ".json");
            if (!sha(physical)
                    .equals(
                            root.path("profiles")
                                    .path(province)
                                    .path("physicalContractSha256")
                                    .asText())) invalid();
            var document = JSON.readTree(physical);
            if (document.path("format").asInt() != 2
                    || !province.equals(document.path("province").asText())) invalid();
            var schema = document.path("schema");
            var columns = new HashMap<String, List<CdsExportTableSchemaDto.Column>>();
            if (!schema.path("columns").isArray()
                    || schema.path("columns").size() > 65536
                    || !schema.path("tables").isArray()
                    || schema.path("tables").isEmpty()
                    || schema.path("tables").size() > 1024
                    || !schema.path("keys").isArray()
                    || schema.path("keys").size() > 32768) invalid();
            for (var row : schema.path("columns")) {
                if (!row.isArray() || row.size() != 10) invalid();
                String table = row.get(0).asText();
                identifier(table);
                columns.computeIfAbsent(table, ignored -> new ArrayList<>())
                        .add(
                                new CdsExportTableSchemaDto.Column(
                                        row.get(1).asText(),
                                        row.get(2).asText(),
                                        row.get(3).asText(),
                                        optional(row.get(6)),
                                        optional(row.get(7)),
                                        optional(row.get(8))));
            }
            var keys = new HashMap<String, TreeMap<Integer, String>>();
            for (var row : schema.path("keys"))
                if (row.get(1).asText().equals("PRIMARY")) {
                    int ordinal = row.get(2).asInt();
                    if (ordinal < 1
                            || ordinal > 16
                            || keys.computeIfAbsent(row.get(0).asText(), ignored -> new TreeMap<>())
                                            .putIfAbsent(ordinal, row.get(3).asText())
                                    != null) invalid();
                }
            var tables = new HashMap<String, CdsExportTableSchemaDto>();
            for (var row : schema.path("tables")) {
                String table = row.get(0).asText();
                identifier(table);
                // formRourke2009 alone may be a non-transactional legacy table; it is read only
                // under the pinned source read lock (CdsExportSourceReadLock).
                if (!"InnoDB".equals(row.get(1).asText())
                        && !(table.equals("formRourke2009")
                                && Set.of("Aria", "MyISAM").contains(row.get(1).asText())
                                && Set.of(Disposition.REQUIRE_EMPTY, Disposition.DOCUMENT)
                                        .contains(rulings.get(table)))) invalid();
                var fields = columns.get(table);
                if (fields == null || fields.isEmpty() || fields.size() > 4096) invalid();
                fields.sort(Comparator.comparing(CdsExportTableSchemaDto.Column::name));
                if (fields.stream().map(CdsExportTableSchemaDto.Column::name).distinct().count()
                                != fields.size()
                        || tables.putIfAbsent(
                                        table,
                                        new CdsExportTableSchemaDto(
                                                table,
                                                row.get(1).asText(),
                                                fields,
                                                List.copyOf(
                                                        keys.getOrDefault(table, new TreeMap<>())
                                                                .values())))
                                != null) invalid();
            }
            if (!columns.keySet().equals(tables.keySet())) invalid();
            union.addAll(tables.keySet());
            profiles.add(new Profile(province, tables));
        }
        if (!union.equals(rulings.keySet())) invalid();
        var patientScopes = scopedColumns(root.path("patientScopes"), profiles, rulings, true);
        var clinicalRemainders =
                scopedColumns(root.path("clinicalRemainders"), profiles, rulings, false);
        for (var entry : rulings.entrySet())
            if (entry.getValue() == Disposition.ACCESS_CONTROL
                    && (!patientScopes.containsKey(entry.getKey())
                            || !clinicalRemainders.containsKey(entry.getKey()))) invalid();
        var required = new HashMap<String, Set<String>>();
        for (var field : root.path("requiredScopes").properties()) {
            if (!CdsCategory.wireNames().contains(field.getKey())
                    || !field.getValue().isArray()
                    || field.getValue().size() > 256) invalid();
            var scopes = new HashSet<String>();
            for (var scope : field.getValue()) {
                identifier(scope.asText());
                if (!scopes.add(scope.asText())) invalid();
            }
            required.put(field.getKey(), Set.copyOf(scopes));
        }
        if (!required.keySet().equals(CdsCategory.wireNames())) invalid();
        var registeredPhysical = new HashSet<String>();
        var declaredScopes = new HashSet<String>();
        required.values().forEach(declaredScopes::addAll);
        // A data-summary table is read through the pinned physical contract itself, so it has no
        // separate source-schema descriptor; it must be captured with Reports and only there.
        var reports = required.get(CdsCategory.REPORTS.wireName());
        for (var entry : rulings.entrySet())
            if (entry.getValue() == Disposition.DOCUMENT && !reports.contains(entry.getKey()))
                invalid();
        for (var entry : required.entrySet())
            for (String name : entry.getValue())
                if (rulings.get(name) == Disposition.DOCUMENT
                        && !entry.getKey().equals(CdsCategory.REPORTS.wireName())) invalid();
        for (String name : declaredScopes) {
            if (rulings.get(name) == Disposition.DOCUMENT) continue;
            var descriptor =
                    JSON.readValue(
                            resource("source-schema/" + name + ".json"),
                            CdsExportTableSchemaDto.class);
            if (descriptor.engine().equals("InnoDB")) registeredPhysical.add(descriptor.table());
        }
        for (var entry : rulings.entrySet())
            if (entry.getValue() == Disposition.MAPPED_SOURCE
                    && !registeredPhysical.contains(entry.getKey())) invalid();
        var scope =
                JSON.readValue(
                        resource("source-schema/clinical_source_catalogue.json"),
                        CdsExportTableSchemaDto.class);
        if (scope.columns().size() != 1
                || !sha(bytes).equals(scope.columns().getFirst().expression())) invalid();
        var operational = new HashMap<String, String>();
        var local = root.path("localOperationalTables");
        if (!local.isMissingNode()) {
            if (!local.isObject() || local.size() > 32) invalid();
            for (var field : local.properties()) {
                // Package-created local tables are outside the Flyway physical contract by design;
                // a name inside the contract must keep its reviewed ruling instead.
                if (!field.getKey().matches("[A-Za-z_][A-Za-z0-9_]{0,63}")
                        || rulings.containsKey(field.getKey())
                        || !field.getValue().isTextual()
                        || field.getValue().asText().isBlank()
                        || field.getValue().asText().length() > 512) invalid();
                operational.put(field.getKey(), field.getValue().asText());
            }
        }
        return new Snapshot(
                rulings, profiles, required, patientScopes, clinicalRemainders, scope, operational);
    }

    private static Map<String, List<String>> scopedColumns(
            JsonNode document,
            List<Profile> profiles,
            Map<String, Disposition> rulings,
            boolean owner)
            throws IOException {
        if (!document.isObject() || document.size() > 1024) invalid();
        var result = new HashMap<String, List<String>>();
        for (var field : document.properties()) {
            String table = field.getKey();
            identifier(table);
            var ruling = rulings.get(table);
            // Clinical remainders: the columns whose stored value makes a row of an otherwise
            // non-clinical table clinical. On an ACCESS_CONTROL table such a row holds the
            // patient; on a DOCUMENT table only such rows are records (data summaries).
            if (ruling == null
                    || (owner
                            ? !Set.of(
                                            Disposition.REQUIRE_EMPTY,
                                            Disposition.ACCESS_CONTROL,
                                            Disposition.DOCUMENT,
                                            Disposition.LISTED)
                                    .contains(ruling)
                            : ruling != Disposition.ACCESS_CONTROL
                                    && ruling != Disposition.DOCUMENT)) invalid();
            if (!field.getValue().isArray()
                    || field.getValue().isEmpty()
                    || field.getValue().size() > 4) invalid();
            var columns = new ArrayList<String>();
            for (var value : field.getValue()) {
                if (!value.isTextual()) invalid();
                String column = value.asText();
                identifier(column);
                if (columns.contains(column)) invalid();
                for (var profile : profiles) {
                    var schema = profile.tables().get(table);
                    if (schema == null) continue;
                    var match =
                            schema.columns().stream().filter(c -> c.name().equals(column)).toList();
                    if (match.size() != 1
                            || owner
                                    && !match.getFirst()
                                            .type()
                                            .matches(
                                                    "(?:tinyint|smallint|mediumint|int|bigint)\\([0-9]{1,3}\\)(?:"
                                                        + " unsigned)?|varchar\\([0-9]{1,3}\\)"))
                        invalid();
                }
                columns.add(column);
            }
            result.put(table, List.copyOf(columns));
        }
        return Map.copyOf(result);
    }

    private static String optional(JsonNode value) {
        return value.isNull() || value.asText().isEmpty() ? null : value.asText();
    }

    private static void identifier(String name) throws IOException {
        if (!name.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) invalid();
    }

    private static byte[] resource(String name) throws IOException {
        try (var input =
                CdsExportSourceCatalogue.class.getResourceAsStream("/cds-export/" + name)) {
            if (input == null) invalid();
            byte[] bytes = input.readNBytes(16_777_217);
            if (bytes.length > 16_777_216) invalid();
            return bytes;
        }
    }

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_SOURCE_CATALOGUE_INVALID");
    }
}
