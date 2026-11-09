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
    public enum Disposition { MAPPED_SOURCE, OPERATIONAL, REFERENCE, REQUIRE_EMPTY }
    public record Profile(String name, Map<String, CdsExportTableSchemaDto> tables) {
        public Profile { tables = Map.copyOf(tables); }
    }
    public record Snapshot(Map<String, Disposition> tables, List<Profile> profiles,
            Map<String, Set<String>> requiredScopes, CdsExportTableSchemaDto scope) {
        public Snapshot {
            tables = Map.copyOf(tables); profiles = List.copyOf(profiles);
            var copy = new HashMap<String, Set<String>>(); requiredScopes.forEach((name, values) -> copy.put(name, Set.copyOf(values)));
            requiredScopes = Map.copyOf(copy);
        }
        public Set<String> required(Set<CdsCategory> selected) {
            var result = new HashSet<String>();
            for (var category : selected) result.addAll(requiredScopes.get(category.wireName()));
            return Set.copyOf(result);
        }
    }
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(1_048_576).build()).build());
    // Snapshot defensively copies its collections; volatile safely publishes that immutable value.
    @SuppressWarnings("java:S3077")
    private static volatile Snapshot cached;
    private CdsExportSourceCatalogue() { }
    public static Snapshot current() {
        var result = cached; if (result != null) return result;
        synchronized (CdsExportSourceCatalogue.class) {
            if (cached == null) try { cached = load(); } catch (IOException | RuntimeException failed) {
                throw new IllegalStateException("CDS_EXPORT_SOURCE_CATALOGUE_INVALID");
            }
            return cached;
        }
    }
    private static Snapshot load() throws IOException {
        byte[] bytes = resource("source-catalogue.json"); var root = JSON.readTree(bytes);
        if (root.path("format").asInt() != 1 || !root.path("tables").isObject() || root.path("tables").size() > 1024
                || root.path("tables").isEmpty() || !root.path("profiles").isObject() || root.path("profiles").size() != 2) invalid();
        var rulings = new HashMap<String, Disposition>();
        for (var field : root.path("tables").properties()) {
            identifier(field.getKey()); rulings.put(field.getKey(), Disposition.valueOf(field.getValue().asText()));
        }
        var profiles = new ArrayList<Profile>(); var union = new HashSet<String>();
        for (String province : List.of("on", "bc")) {
            byte[] physical = resource("physical-schema/" + province + ".json");
            if (!sha(physical).equals(root.path("profiles").path(province).path("physicalContractSha256").asText())) invalid();
            var document = JSON.readTree(physical);
            if (document.path("format").asInt() != 2 || !province.equals(document.path("province").asText())) invalid();
            var schema = document.path("schema"); var columns = new HashMap<String, List<CdsExportTableSchemaDto.Column>>();
            if (!schema.path("columns").isArray() || schema.path("columns").size() > 65536 || !schema.path("tables").isArray()
                    || schema.path("tables").isEmpty() || schema.path("tables").size() > 1024 || !schema.path("keys").isArray()
                    || schema.path("keys").size() > 32768) invalid();
            for (var row : schema.path("columns")) {
                if (!row.isArray() || row.size() != 10) invalid(); String table = row.get(0).asText(); identifier(table);
                columns.computeIfAbsent(table, ignored -> new ArrayList<>()).add(new CdsExportTableSchemaDto.Column(row.get(1).asText(),
                        row.get(2).asText(), row.get(3).asText(), optional(row.get(6)), optional(row.get(7)), optional(row.get(8))));
            }
            var keys = new HashMap<String, TreeMap<Integer, String>>();
            for (var row : schema.path("keys")) if (row.get(1).asText().equals("PRIMARY")) {
                int ordinal = row.get(2).asInt(); if (ordinal < 1 || ordinal > 16
                        || keys.computeIfAbsent(row.get(0).asText(), ignored -> new TreeMap<>()).putIfAbsent(ordinal, row.get(3).asText()) != null) invalid();
            }
            var tables = new HashMap<String, CdsExportTableSchemaDto>();
            for (var row : schema.path("tables")) {
                String table = row.get(0).asText(); identifier(table);
                if (!"InnoDB".equals(row.get(1).asText()) && !(table.equals("formRourke2009")
                        && Set.of("Aria", "MyISAM").contains(row.get(1).asText()) && rulings.get(table) == Disposition.REQUIRE_EMPTY)) invalid();
                var fields = columns.get(table);
                if (fields == null || fields.isEmpty() || fields.size() > 4096) invalid();
                fields.sort(Comparator.comparing(CdsExportTableSchemaDto.Column::name));
                if (fields.stream().map(CdsExportTableSchemaDto.Column::name).distinct().count() != fields.size()
                        || tables.putIfAbsent(table, new CdsExportTableSchemaDto(table, row.get(1).asText(), fields,
                                List.copyOf(keys.getOrDefault(table, new TreeMap<>()).values()))) != null) invalid();
            }
            if (!columns.keySet().equals(tables.keySet())) invalid(); union.addAll(tables.keySet()); profiles.add(new Profile(province, tables));
        }
        if (!union.equals(rulings.keySet())) invalid();
        var required = new HashMap<String, Set<String>>();
        for (var field : root.path("requiredScopes").properties()) {
            if (!CdsCategory.wireNames().contains(field.getKey()) || !field.getValue().isArray() || field.getValue().size() > 256) invalid();
            var scopes = new HashSet<String>(); for (var scope : field.getValue()) { identifier(scope.asText()); if (!scopes.add(scope.asText())) invalid(); }
            required.put(field.getKey(), Set.copyOf(scopes));
        }
        if (!required.keySet().equals(CdsCategory.wireNames())) invalid();
        var registeredPhysical = new HashSet<String>(); var declaredScopes = new HashSet<String>(); required.values().forEach(declaredScopes::addAll);
        for (String name : declaredScopes) {
            var descriptor = JSON.readValue(resource("source-schema/" + name + ".json"), CdsExportTableSchemaDto.class);
            if (descriptor.engine().equals("InnoDB")) registeredPhysical.add(descriptor.table());
        }
        for (var entry : rulings.entrySet()) if (entry.getValue() == Disposition.MAPPED_SOURCE && !registeredPhysical.contains(entry.getKey())) invalid();
        var scope = JSON.readValue(resource("source-schema/clinical_source_catalogue.json"), CdsExportTableSchemaDto.class);
        if (scope.columns().size() != 1 || !sha(bytes).equals(scope.columns().getFirst().expression())) invalid();
        return new Snapshot(rulings, profiles, required, scope);
    }
    private static String optional(JsonNode value) { return value.isNull() || value.asText().isEmpty() ? null : value.asText(); }
    private static void identifier(String name) throws IOException { if (!name.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) invalid(); }
    private static byte[] resource(String name) throws IOException {
        try (var input = CdsExportSourceCatalogue.class.getResourceAsStream("/cds-export/" + name)) {
            if (input == null) invalid(); byte[] bytes = input.readNBytes(16_777_217); if (bytes.length > 16_777_216) invalid(); return bytes;
        }
    }
    private static String sha(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_SOURCE_CATALOGUE_INVALID"); }
}
