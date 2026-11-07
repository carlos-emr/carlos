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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsPreparationSeal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsTargetReceiptService;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * One bounded pass indexes the original run/patient/unit/field/dependency/preparation graph.
 * Applied work is distinguished from proposals/refusals. Envelope seals and relationships
 * are checked, but a historical row digest is not proof that its current projection is
 * unchanged. Clinical reconciliation and actual row receipts remain separate gates.
 */
@Service
public class CdsExportRetainedIndexService {
    private static final Map<String, Plan> PLANS = Map.of("cds_import_run", Plan.IMPORT_RUNS, "cds_import_patient", Plan.IMPORT_PATIENTS,
            "cds_import_unit", Plan.IMPORT_UNITS, "cds_import_field", Plan.IMPORT_FIELDS,
            "cds_import_dependency", Plan.IMPORT_DEPENDENCIES, "cds_import_preparation", Plan.IMPORT_PREPARATIONS);
    private static final Set<String> PUBLISHED = Set.of("COMPLETE", "COMPLETE_WITH_NONLOSSY_WARNINGS", "ALREADY_COMPLETE");
    private static final Set<String> UNWRITTEN = Set.of("PLANNED", "REFUSED", "FAILED_BEFORE_WRITE", "CANCELLED_BEFORE_WRITE", "NOT_STARTED");
    private static final Set<String> APPLIED = Set.of("COMMITTED", "ALREADY_COMPLETE");
    private static final JsonMapper JSON = JsonMapper.builder(com.fasterxml.jackson.core.JsonFactory.builder()
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(65536).maxNumberLength(20).build()).build())
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportNativeCaptureService captures;
    @Autowired public CdsExportRetainedIndexService(CdsExportNativeCaptureService captures) { this(new CdsExportCapturedTextRowsReader(captures), captures); }
    CdsExportRetainedIndexService(CdsExportCapturedTextRowsReader rows, CdsExportNativeCaptureService captures) {
        this.rows = Objects.requireNonNull(rows); this.captures = Objects.requireNonNull(captures);
    }

    public CdsExportRetainedIndexDto index(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> requiredTables,
            CdsExportStagedFileDto manifest, long maximumFields, long maximumBytes, int maximumRows, long maximumIndexBytes,
            CdsExportRunControl control) throws IOException {
        if (!requiredTables.containsAll(PLANS.keySet()) || maximumRows < 1 || maximumRows > 1_000_000
                || maximumIndexBytes < 1 || maximumIndexBytes > 64L << 20) throw new IllegalArgumentException("CDS_EXPORT_RETAINED_INDEX_SCOPE_INVALID");
        patient.require(lease); var tables = new TreeMap<String, Map<String, CdsExportCapturedTextRowDto>>();
        PLANS.keySet().forEach(table -> tables.put(table, new TreeMap<>())); long[] count = new long[2];
        rows.forEachTable(lease, patient, requiredTables, PLANS.keySet(), manifest, maximumFields, maximumBytes, (int) Math.min(maximumIndexBytes, 2_097_152L + io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec.MAX_BYTES), control, row -> {
            Plan plan = PLANS.get(row.table());
            if (plan == null || !plan.validRetainedKey(row.recordKey()) || !row.fields().keySet().equals(Set.copyOf(plan.columns()))
                    || !row.recordKey().equals(row.fields().get("id"))) invalid();
            count[0] = Math.addExact(count[0], 1);
            for (var entry : row.fields().entrySet()) {
                count[1] = Math.addExact(count[1], entry.getKey().getBytes(StandardCharsets.UTF_8).length + 16L);
                if (entry.getValue() != null) count[1] = Math.addExact(count[1], entry.getValue().getBytes(StandardCharsets.UTF_8).length);
            }
            if (count[0] > maximumRows || count[1] > maximumIndexBytes) throw new IOException("CDS_EXPORT_RETAINED_INDEX_RESOURCE_LIMIT");
            if (tables.get(row.table()).putIfAbsent(row.recordKey(), row) != null) invalid();
        });
        patient.require(lease); control.checkpoint();
        try {
            var result = link(lease, patient.patient(), manifest.plaintextSha256(), tables, count[0], count[1], control);
            captures.authorizeSources(lease, patient, requiredTables, control); patient.require(lease); control.checkpoint(); return result;
        }
        catch (IllegalArgumentException | NullPointerException malformed) { throw new IOException("CDS_EXPORT_RETAINED_INDEX_REQUIRES_REVIEW"); }
    }
    private CdsExportRetainedIndexDto link(CdsExportLeaseDto lease, int demographic, String source,
            Map<String, Map<String, CdsExportCapturedTextRowDto>> tables, long rows, long bytes, CdsExportRunControl control) throws IOException {
        var runs = tables.get("cds_import_run"); var patients = new HashMap<String, CdsExportCapturedTextRowDto>();
        for (var row : runs.values()) {
            control.checkpoint();
            uuid(row.recordKey()); digest(value(row, "options_digest")); digest(value(row, "ruleset_digest"));
            CdsLogicalProfile.fromIdentifier(value(row, "profile"));
        }
        for (var row : tables.get("cds_import_patient").values()) {
            control.checkpoint();
            String run = value(row, "run_id"), key = value(row, "patient_key"); uuid(run); digest(key);
            if (!runs.containsKey(run) || !Integer.toString(demographic).equals(value(row, "target_demographic_no"))
                    || patients.putIfAbsent(address(run, key), row) != null) invalid();
            if (!"0".equals(value(row, "held"))) throw new IOException("CDS_EXPORT_CAPTURED_IMPORT_HOLD_REQUIRES_REVIEW");
            if (!PUBLISHED.contains(value(row, "state")) && !UNWRITTEN.contains(value(row, "state"))) invalid();
        }
        var units = new HashMap<String, CdsExportCapturedTextRowDto>(); var applied = new HashSet<String>();
        var references = new HashMap<String, List<CdsExportRetainedIndexDto.Reference>>();
        for (var unit : tables.get("cds_import_unit").values()) {
            control.checkpoint();
            String run = value(unit, "run_id"), key = value(unit, "unit_key"), parent = value(unit, "patient_key"); digest(key); digest(parent);
            var patient = patients.get(address(run, parent)); String unitAddress = address(run, key);
            if (patient == null || units.putIfAbsent(unitAddress, unit) != null) invalid();
            String category = value(unit, "category"), base = base(category);
            if (lease.document().command().categories().stream().noneMatch(selected -> selected.wireName().equals(base))) invalid();
            if (APPLIED.contains(value(unit, "state"))) {
                if (!PUBLISHED.contains(value(patient, "state"))) invalid();
                digest(value(unit, "source_digest"));
                if (!hex(value(unit, "target_references")).equals(value(unit, "target_digest"))) invalid();
                references.put(unitAddress, references(value(unit, "target_references"), demographic));
                if (unit.fields().get("native_baseline") != null) io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec.decode(
                        unit.fields().get("native_baseline"), value(unit, "target_references"), value(unit, "target_digest"));
                applied.add(unitAddress);
            } else if (!"EXPECTED".equals(value(unit, "state")) || PUBLISHED.contains(value(patient, "state"))) invalid();
        }
        var dependencies = new HashMap<String, List<String>>();
        for (var edge : tables.get("cds_import_dependency").values()) {
            control.checkpoint();
            String run = value(edge, "run_id"), child = address(run, value(edge, "unit_key")), parent = address(run, value(edge, "depends_on_unit_key"));
            var c = units.get(child); var p = units.get(parent);
            if (c == null || p == null || !value(c, "patient_key").equals(value(p, "patient_key")) || applied.contains(child) && !applied.contains(parent)) invalid();
            dependencies.computeIfAbsent(child, ignored -> new ArrayList<>()).add(parent);
        }
        acyclic(applied, dependencies, control);
        var nativeParents = new HashMap<String, String>();
        var mapper = new CdsExportRetainedFieldMapper();
        for (var row : tables.get("cds_import_field").values()) {
            control.checkpoint();
            var field = mapper.map(demographic, lease.document().command().categories(), row); String key = address(field.runId(), field.fieldUnitKey());
            var owner = units.get(key);
            if (owner == null || !applied.contains(key) || !value(owner, "patient_key").equals(field.sourcePatientKey())
                    || !value(owner, "category").equals(field.category().wireName() + "_FIELDS") || number(value(owner, "ordinal")) != field.occurrence()
                    || references.get(key).stream().noneMatch(ref -> ref.table().equals("cds_import_field") && ref.id().equals(field.id()))) invalid();
            var candidates = new HashSet<String>();
            for (String parent : dependencies.getOrDefault(key, List.of())) {
                var unit = units.get(parent);
                if (value(unit, "category").equals(field.category().wireName()) && number(value(unit, "ordinal")) == field.occurrence()) candidates.add(parent);
            }
            if (candidates.size() != 1) invalid(); nativeParents.put(field.id(), candidates.iterator().next());
        }
        for (var entry : references.entrySet()) for (var reference : entry.getValue()) {
            if (reference.table().equals("cds_import_field") && !tables.get("cds_import_field").containsKey(reference.id())) invalid();
        }
        for (var prepared : tables.get("cds_import_preparation").values()) {
            control.checkpoint();
            String run = value(prepared, "run_id"), key = address(run, value(prepared, "unit_key")); var unit = units.get(key);
            if (unit == null) invalid(); if (!applied.contains(key)) continue; var origin = runs.get(run);
            String payload = value(prepared, "payload");
            if (payload.getBytes(StandardCharsets.UTF_8).length > 1_048_576 || !value(prepared, "source_digest").equals(value(unit, "source_digest"))
                    || !value(prepared, "options_digest").equals(value(origin, "options_digest")) || !value(prepared, "ruleset_digest").equals(value(origin, "ruleset_digest"))
                    || !CdsPreparationSeal.digest(value(prepared, "payload_type"), value(prepared, "source_digest"), value(prepared, "options_digest"),
                            value(prepared, "ruleset_digest"), payload).equals(value(prepared, "digest"))) invalid();
        }
        return new CdsExportRetainedIndexDto(source, rows, bytes, tables, applied, references, nativeParents);
    }
    private static List<CdsExportRetainedIndexDto.Reference> references(String encoded, int patient) throws IOException {
        if (encoded.length() > 65536) invalid(); JsonNode root = JSON.readTree(encoded); exact(root, Set.of("protocol", "refs"));
        if (!root.get("protocol").isIntegralNumber() || !root.get("protocol").canConvertToInt() || root.get("protocol").intValue() != 2 || !root.get("refs").isArray()
                || root.get("refs").isEmpty() || root.get("refs").size() > 192) invalid();
        var result = new ArrayList<CdsExportRetainedIndexDto.Reference>();
        for (var ref : root.get("refs")) {
            exact(ref, Set.of("table", "id", "digest")); for (String field : List.of("table", "id", "digest")) if (!ref.get(field).isTextual()) invalid();
            String table = ref.get("table").textValue(), id = ref.get("id").textValue(), digest = ref.get("digest").textValue(); digest(digest);
            if (!CdsTargetReceiptService.targetTables().contains(table) || !id.matches("[A-Za-z0-9_-]{1,64}")
                    || table.equals("demographic") && !id.equals(Integer.toString(patient))) invalid();
            result.add(new CdsExportRetainedIndexDto.Reference(table, id, digest));
        }
        return List.copyOf(result); // Repeated receipt references are legal; never turn them into additional clinical occurrences.
    }
    private static void acyclic(Set<String> applied, Map<String, List<String>> parents, CdsExportRunControl control) throws IOException {
        var degrees = new HashMap<String, Integer>(); var children = new HashMap<String, List<String>>(); var ready = new ArrayDeque<String>();
        for (String unit : applied) {
            control.checkpoint();
            var inputs = parents.getOrDefault(unit, List.of()); degrees.put(unit, inputs.size()); if (inputs.isEmpty()) ready.add(unit);
            for (String parent : inputs) children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(unit);
        }
        int seen = 0;
        while (!ready.isEmpty()) { control.checkpoint(); String parent = ready.removeFirst(); seen++; for (String child : children.getOrDefault(parent, List.of())) {
            int left = degrees.compute(child, (key, count) -> count - 1); if (left == 0) ready.add(child);
        } }
        if (seen != applied.size()) invalid();
    }
    private static String value(CdsExportCapturedTextRowDto row, String column) throws IOException { String value = row.fields().get(column); if (value == null) invalid(); return value; }
    private static String address(String run, String key) { return run + ":" + key; }
    private static String base(String category) { for (String suffix : List.of("_FIELDS", "_CONTACT", "_CARE")) if (category.endsWith(suffix)) return category.substring(0, category.length() - suffix.length()); return category; }
    private static String hex(String value) { return HexFormat.of().formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8))); }
    private static void digest(String value) throws IOException { if (value == null || !value.matches("[0-9a-f]{64}")) invalid(); }
    private static void uuid(String value) throws IOException { if (value == null || !UUID.fromString(value).toString().equals(value)) invalid(); }
    private static long number(String value) throws IOException { long number = Long.parseLong(value); if (number < 0 || !Long.toString(number).equals(value)) invalid(); return number; }
    private static void exact(JsonNode value, Set<String> keys) throws IOException { if (value == null || !value.isObject() || value.size() != keys.size()) invalid(); for (String key : keys) if (!value.has(key)) invalid(); }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_RETAINED_INDEX_REQUIRES_REVIEW"); }
}
