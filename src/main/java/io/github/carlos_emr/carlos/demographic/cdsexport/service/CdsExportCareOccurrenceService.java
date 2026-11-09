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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCareElementDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Original care parents and all child observations share one private source assembly; unknown/changed native care requires review. */
@Service
public class CdsExportCareOccurrenceService implements CdsExportCategoryOccurrenceAssembler {
    private static final Set<String> TABLES =
            Set.of(
                    "measurements",
                    "measurementsExt",
                    "measurementsDeleted",
                    "measurementType",
                    "demographic");
    private static final Pattern CHILD =
            Pattern.compile("^([A-Za-z]+)\\[([1-9][0-9]{0,17})\\](?=/|$)");
    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportCapturedTextRowsReader rows;

    public CdsExportCareOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures) {
        this.values = Objects.requireNonNull(values);
        this.verification = Objects.requireNonNull(verification);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.captures = Objects.requireNonNull(captures);
        rows = new CdsExportCapturedTextRowsReader(captures);
    }

    @Override
    public CdsCategory category() {
        return CdsCategory.CARE_ELEMENTS;
    }

    @Override
    public CdsExportClinicalOccurrenceService.Result append(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        var result = new CdsExportClinicalOccurrenceService.Result[1];
        xml.source(
                emitter ->
                        result[0] =
                                assemble(
                                        lease,
                                        patient,
                                        patientKey,
                                        requiredTables,
                                        source,
                                        payloadManifest,
                                        bounds,
                                        control,
                                        emitter,
                                        output));
        return Objects.requireNonNull(result[0]);
    }

    private CdsExportClinicalOccurrenceService.Result assemble(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        Objects.requireNonNull(output);
        patient.require(lease);
        control.authorizeCategory(patient.patient(), category(), false);
        var tables = Set.copyOf(requiredTables);
        if (patientKey == null
                || !patientKey.matches("[0-9a-f]{64}")
                || !lease.document().command().categories().contains(category())
                || !tables.containsAll(TABLES)
                || !tables.equals(source.capture().tables().keySet())) invalid();
        if (source.capture().tables().get("measurementsDeleted").rows() != 0) review();
        new CdsExportMergeScopeValidator(captures)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var current = new TreeMap<String, CdsExportCapturedTextRowDto>();
        var exact = new HashMap<String, CdsExportNativeRowReceiptDto>();
        long[] memory = {0};
        var scanned =
                rows.forEachTable(
                        lease,
                        patient,
                        tables,
                        TABLES,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        67_108_864,
                        control,
                        row -> {
                            String id = id(row);
                            if (!row.fields().keySet().equals(columns(row.table()))
                                    || !row.exactNumbers().isEmpty()) invalid();
                            memory[0] =
                                    Math.addExact(memory[0], 1024L + row.fields().size() * 256L);
                            for (String value : row.fields().values())
                                if (value != null)
                                    memory[0] =
                                            Math.addExact(
                                                    memory[0],
                                                    value.getBytes(StandardCharsets.UTF_8).length
                                                            + 64L);
                            if (current.size() >= bounds.nativeRows()
                                    || memory[0] > bounds.indexBytes()) limit();
                            if (current.putIfAbsent(key(row.table(), id), row) != null) invalid();
                        });
        if (!scanned.equals(source.capture())) invalid();
        var proof =
                witnesses.scan(
                        lease,
                        patient,
                        tables,
                        source,
                        bounds.fields(),
                        bounds.bytes(),
                        control,
                        row -> {
                            if (!TABLES.contains(row.table())) return;
                            if (exact.size() >= bounds.nativeRows()) limit();
                            if (exact.putIfAbsent(key(row.table(), row.id()), row) != null)
                                invalid();
                        });
        if (!proof.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || !current.keySet().equals(exact.keySet())) invalid();
        for (var entry : current.entrySet()) verify(entry.getValue(), exact.get(entry.getKey()));
        for (String table : TABLES)
            if (current.values().stream().filter(row -> row.table().equals(table)).count()
                    != source.capture().tables().get(table).rows()) invalid();
        var demographic = current.get(key("demographic", Integer.toString(patient.patient())));
        if (demographic == null || source.capture().tables().get("demographic").rows() != 1)
            invalid();
        for (var row : current.values()) {
            if (row.table().equals("measurements")
                    && !Integer.toString(patient.patient())
                            .equals(row.fields().get("demographicNo"))) invalid();
            if (row.table().equals("measurementsExt")
                    && !current.containsKey(
                            key("measurements", row.fields().get("measurement_id")))) invalid();
        }
        long[] totals = {0, 0};
        values.withValues(
                lease,
                patient,
                tables,
                source.manifest(),
                payloadManifest,
                bounds.fields(),
                bounds.bytes(),
                bounds.indexRows(),
                bounds.indexBytes(),
                control,
                (index, retained, streams) -> {
                    if (!index.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
                        invalid();
                    var checked =
                            verification.verify(
                                    lease,
                                    patient,
                                    tables,
                                    source,
                                    index,
                                    bounds.fields(),
                                    bounds.bytes(),
                                    1_000_000,
                                    control);
                    var units = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
                    var parents = new TreeMap<String, CdsExportCapturedTextRowDto>();
                    for (var row :
                            index.tables().getOrDefault("cds_import_unit", Map.of()).values()) {
                        String address =
                                row.fields().get("run_id") + ":" + row.fields().get("unit_key");
                        if (!index.appliedUnits().contains(address)) continue;
                        if (units.putIfAbsent(address, row) != null) invalid();
                        if (category().wireName().equals(row.fields().get("category")))
                            parents.put(address, row);
                    }
                    if (parents.size() > bounds.nativeRows()) limit();
                    var children = new HashMap<String, List<String>>();
                    for (var edge :
                            index.tables()
                                    .getOrDefault("cds_import_dependency", Map.of())
                                    .values()) {
                        control.checkpoint();
                        String run = edge.fields().get("run_id"),
                                child = run + ":" + edge.fields().get("unit_key"),
                                parent = run + ":" + edge.fields().get("depends_on_unit_key");
                        if (!parents.containsKey(parent)
                                || !units.containsKey(child)
                                || !"CareElements_CARE"
                                        .equals(units.get(child).fields().get("category")))
                            continue;
                        String expected =
                                CdsImportKeys.hash(run, child.substring(37), parent.substring(37));
                        if (!expected.equals(edge.recordKey())
                                || !expected.equals(edge.fields().get("id"))) invalid();
                        var linked = children.computeIfAbsent(child, ignored -> new ArrayList<>());
                        linked.add(parent);
                        if (linked.size() != 1) review();
                    }
                    var expectedChildren = new HashSet<String>();
                    for (var entry : units.entrySet())
                        if ("CareElements_CARE".equals(entry.getValue().fields().get("category")))
                            expectedChildren.add(entry.getKey());
                    if (!children.keySet().equals(expectedChildren)) review();
                    var owned = new HashSet<String>();
                    var consumed = new HashSet<String>();
                    var childDone = new HashSet<String>();
                    var dictionary = new HashSet<String>();
                    for (var parent : parents.entrySet()) {
                        control.checkpoint();
                        String address = parent.getKey();
                        var metadata = parent.getValue().fields();
                        var parentRefs = index.references().get(address);
                        var parentProof = checked.units().get(address);
                        if (parentRefs == null
                                || parentRefs.size() != 1
                                || !"demographic".equals(parentRefs.getFirst().table())
                                || !Integer.toString(patient.patient())
                                        .equals(parentRefs.getFirst().id())
                                || parentProof == null
                                || parentProof.size() != 1
                                || !parentRefs.equals(
                                        parentProof.stream()
                                                .map(
                                                        CdsExportRetainedReceiptVerificationDto
                                                                        .CheckedReference
                                                                ::expected)
                                                .toList())
                                || !exact.get(
                                                key(
                                                        "demographic",
                                                        Integer.toString(patient.patient())))
                                        .digest()
                                        .equals(parentProof.getFirst().currentDigest())) invalid();
                        var fields =
                                new CdsExportRetainedOccurrenceFields()
                                        .fieldsFor(
                                                patient.patient(),
                                                category(),
                                                address,
                                                index,
                                                checked,
                                                retained,
                                                control::checkpoint);
                        String
                                root =
                                        CdsExportRetainedOccurrencePath.root(
                                                fields.getFirst(), category()),
                                lineage = metadata.get("lineage_key");
                        if (!address.substring(37)
                                .equals(CdsImportKeys.hash(lineage, root, "NATIVE", "0")))
                            invalid();
                        var roots = new HashSet<String>();
                        for (var field : fields) {
                            if (!root.equals(
                                    CdsExportRetainedOccurrencePath.root(field, category())))
                                invalid();
                            String path =
                                    field.sourcePath().length() == root.length()
                                            ? ""
                                            : field.sourcePath().substring(root.length() + 1);
                            if (path.isEmpty()) continue;
                            var match = CHILD.matcher(path);
                            if (!match.find()
                                    || !CdsCareScalarContract.ELEMENTS.contains(match.group(1)))
                                invalid();
                            roots.add(match.group());
                        }
                        var prepared = new TreeMap<String, CdsCareElementDto>();
                        var assessments =
                                new LinkedHashMap<
                                        CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
                        var actualRows = new ArrayList<CdsExportCapturedTextRowDto>();
                        for (var child : children.entrySet())
                            if (child.getValue().getFirst().equals(address)) {
                                control.checkpoint();
                                if (!childDone.add(child.getKey())
                                        || !checked.unchangedUnits().contains(child.getKey()))
                                    review();
                                var unit = units.get(child.getKey());
                                var info = unit.fields();
                                if (!Objects.equals(
                                                metadata.get("patient_key"),
                                                info.get("patient_key"))
                                        || !Objects.equals(lineage, info.get("lineage_key")))
                                    invalid();
                                String ordinal = info.get("ordinal");
                                try {
                                    long n = Long.parseLong(ordinal);
                                    if (n < 1 || !Long.toString(n).equals(ordinal)) invalid();
                                } catch (NumberFormatException malformed) {
                                    invalid();
                                }
                                var candidate =
                                        new CdsExportRetainedPreparationReader()
                                                .care(index, child.getKey())
                                                .value();
                                var matching = new ArrayList<String>();
                                for (String childRoot : roots)
                                    if (childRoot.startsWith(candidate.element() + "[")
                                            && child.getKey()
                                                    .substring(37)
                                                    .equals(
                                                            CdsImportKeys.hash(
                                                                    lineage,
                                                                    root + "/" + childRoot,
                                                                    "_CARE",
                                                                    ordinal)))
                                        matching.add(childRoot);
                                if (matching.size() != 1
                                        || prepared.putIfAbsent(matching.getFirst(), candidate)
                                                != null) review();
                                String childRoot = matching.getFirst();
                                var refs = index.references().get(child.getKey());
                                var compared = checked.units().get(child.getKey());
                                if (refs == null
                                        || compared == null
                                        || !refs.equals(
                                                compared.stream()
                                                        .map(
                                                                CdsExportRetainedReceiptVerificationDto
                                                                                .CheckedReference
                                                                        ::expected)
                                                        .toList())) invalid();
                                var primary =
                                        refs.stream()
                                                .filter(ref -> ref.table().equals("measurements"))
                                                .toList();
                                if (primary.size() != 1) review();
                                String nativeId = primary.getFirst().id();
                                var observation = current.get(key("measurements", nativeId));
                                if (observation == null) review();
                                var extensions = new ArrayList<CdsExportCapturedTextRowDto>();
                                for (var ref : refs) {
                                    String nativeKey = key(ref.table(), ref.id());
                                    if (!Set.of("measurements", "measurementsExt")
                                                    .contains(ref.table())
                                            || !owned.add(nativeKey)
                                            || !current.containsKey(nativeKey)
                                            || !ref.digest().equals(exact.get(nativeKey).digest()))
                                        review();
                                    if (ref.table().equals("measurementsExt"))
                                        extensions.add(current.get(nativeKey));
                                }
                                bind(
                                        patient.patient(),
                                        observation,
                                        extensions,
                                        candidate,
                                        root + "/" + childRoot);
                                assess(observation, extensions, candidate.element(), assessments);
                                actualRows.add(observation);
                                actualRows.addAll(extensions);
                                for (var definition : current.values())
                                    if (definition.table().equals("measurementType")
                                            && Objects.equals(
                                                    definition.fields().get("type"),
                                                    observation.fields().get("type"))
                                            && Objects.equals(
                                                    definition.fields().get("measuringInstruction"),
                                                    observation
                                                            .fields()
                                                            .get("measuringInstruction"))
                                            && dictionary.add(
                                                    key(definition.table(), id(definition)))) {
                                        var scoped =
                                                new CdsExportNativeFieldAssessments(
                                                        definition, assessments);
                                        for (String column : definition.fields().keySet())
                                            scoped.operational(
                                                    column,
                                                    "LOCAL_VOCABULARY_WITH_FROZEN_PATIENT_CODE_VALUE_AND_INSTRUCTIONS");
                                        actualRows.add(definition);
                                    }
                            }
                        if (!prepared.keySet().equals(roots)) review();
                        var options =
                                new CdsExportRetainedImportOptionsReader()
                                        .fromCaptured(index, address.substring(0, 36));
                        var sourceValues =
                                new CdsExportCareRetainedSource()
                                        .readVerified(
                                                fields,
                                                options,
                                                prepared,
                                                streams,
                                                control::checkpoint);
                        var content =
                                new CdsExportRetainedSectionWriter()
                                        .prepareChanges(
                                                fields,
                                                streams,
                                                sourceValues.changes(),
                                                control::checkpoint);
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        actualRows, assessments, 1_000_000, control::checkpoint);
                        for (String field : sourceValues.sourceFields())
                            if (!consumed.add(field)) invalid();
                        var identity =
                                new CdsExportIdentityDto(
                                        patientKey,
                                        category(),
                                        CdsImportKeys.hash(
                                                "carlos-retained-occurrence-v1", address));
                        streams.requireActive();
                        control.authorizeCategory(patient.patient(), category(), false);
                        xml.emit(new CdsExportXmlComposer.Section(identity, content.content()));
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        identity,
                                        index.sourceManifestSha256(),
                                        "cds_import_unit",
                                        address.substring(37),
                                        address,
                                        assessments,
                                        content.retainedFields(),
                                        content.replacedFields()));
                        control.authorizeCategory(patient.patient(), category(), false);
                        totals[0]++;
                        totals[1] = Math.addExact(totals[1], sourceValues.sourceFields().size());
                    }
                    var expectedNative = new HashSet<String>();
                    current.forEach(
                            (key, row) -> {
                                if (Set.of("measurements", "measurementsExt").contains(row.table()))
                                    expectedNative.add(key);
                            });
                    var expectedDictionary = new HashSet<String>();
                    current.forEach(
                            (key, row) -> {
                                if (row.table().equals("measurementType"))
                                    expectedDictionary.add(key);
                            });
                    var expectedSource = new HashSet<String>();
                    for (var field : retained)
                        if (field.category() == category()) expectedSource.add(field.id());
                    if (!childDone.equals(expectedChildren)
                            || !owned.equals(expectedNative)
                            || !dictionary.equals(expectedDictionary)) review();
                    if (!consumed.equals(expectedSource)) invalid();
                    streams.requireActive();
                    captures.authorizeSources(lease, patient, tables, control);
                    patient.require(lease);
                    control.authorizeCategory(patient.patient(), category(), false);
                    control.checkpoint();
                });
        return new CdsExportClinicalOccurrenceService.Result(
                source.manifest().plaintextSha256(),
                category(),
                totals[0],
                totals[0],
                0,
                totals[1]);
    }

    static void bind(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> extensions,
            CdsCareElementDto prepared,
            String path)
            throws IOException {
        String nativeId = id(row);
        var value = row.fields();
        String instruction =
                switch (prepared.measurementType()) {
                    case "CDS_GOALS" -> "Source documented goals";
                    case "CDS_EXAM" -> "Source exam performed; result not supplied";
                    case "CDS_CARE_VALUE" -> "Uninterpreted source care value";
                    default ->
                            prepared.unit() == null ? "Source assertion" : "in " + prepared.unit();
                };
        String comments =
                prepared.nativeOverflow()
                        ? "CDS_COMPLETE_VALUE_IN_SOURCE"
                        : prepared.uninterpreted() ? "CDS_UNINTERPRETED_SOURCE_VALUE" : "";
        if (!Integer.toString(patient).equals(value.get("demographicNo"))
                || !prepared.measurementType().equals(value.get("type"))
                || !prepared.value().equals(value.get("dataField"))
                || !instruction.equals(value.get("measuringInstruction"))
                || !comments.equals(value.get("comments"))
                || value.get("dateObserved") == null) invalid();
        if (value.get("appointmentNo") != null && !value.get("appointmentNo").equals("0")) review();
        var expected = new HashMap<String, String>();
        expected.put("cdsCareElement", prepared.element());
        expected.put("date_precision", "FULL_DATE");
        expected.put("cdsSourceDate", prepared.date().original());
        expected.put("cdsSourcePath", path);
        if (prepared.unit() != null) expected.put("unit", prepared.unit());
        if (prepared.code() != null) expected.put("loinc", prepared.code());
        if (prepared.nativeOverflow()) expected.put("cdsNativeOverflow", "true");
        if (prepared.uninterpreted()) expected.put("cdsUninterpreted", "true");
        if (prepared.sourceValueDigest() != null)
            expected.put("cdsValueDigest", prepared.sourceValueDigest());
        if (prepared.date().appliedShiftDays() != 0)
            expected.put("cdsShiftDays", Integer.toString(prepared.date().appliedShiftDays()));
        var actual = new HashMap<String, String>();
        for (var extension : extensions) {
            if (!nativeId.equals(extension.fields().get("measurement_id"))
                    || actual.containsKey(extension.fields().get("keyval"))) invalid();
            actual.put(extension.fields().get("keyval"), extension.fields().get("val"));
        }
        if (!expected.equals(actual)) review();
    }

    private static void assess(
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> extensions,
            String element,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> result) {
        var main = new CdsExportNativeFieldAssessments(row, result);
        for (String column : row.fields().keySet()) {
            if (Set.of("type", "dataField", "measuringInstruction", "dateObserved", "comments")
                    .contains(column))
                main.set(
                        column,
                        row.fields().get(column) == null || row.fields().get(column).isEmpty()
                                ? Outcome.ABSENT
                                : Outcome.MAPPED,
                        "CareElements/" + element,
                        "UNCHANGED_SEALED_CARE_PROJECTION_WITH_COMPLETE_TYPED_SOURCE");
            else
                main.operational(
                        column,
                        "LOCAL_IDENTITY_ENTRY_PROVENANCE_AND_EMPTY_APPOINTMENT_CONTEXT_NEVER_SOURCE_AUTHOR");
        }
        for (var extension : extensions) {
            var fields = new CdsExportNativeFieldAssessments(extension, result);
            fields.operational("id", "LOCAL_EXTENSION_IDENTITY");
            fields.operational("measurement_id", "EXACT_PATIENT_OBSERVATION_OWNER");
            fields.operational("keyval", "SEALED_CARE_METADATA_ROLE");
            fields.set(
                    "val",
                    Outcome.MAPPED,
                    "CareElements/" + element,
                    "UNCHANGED_SOURCE_TEMPORAL_UNIT_CODE_AND_COMPLETE_VALUE_BINDING");
        }
    }

    private static Set<String> columns(String table) throws IOException {
        if (table.equals("demographic")) {
            var columns = new HashSet<>(CdsExportDemographicRowDto.COLUMNS);
            columns.add("demographic_no");
            return Set.copyOf(columns);
        }
        return Set.copyOf(
                switch (table) {
                    case "measurements" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_CURRENT.columns();
                    case "measurementsExt" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_EXT.columns();
                    case "measurementType" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENT_TYPES.columns();
                    case "measurementsDeleted" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_DELETED.columns();
                    default -> throw new IOException("CDS_EXPORT_CARE_OCCURRENCE_INVALID");
                });
    }

    private static String id(CdsExportCapturedTextRowDto row) throws IOException {
        String value =
                row.fields().get(row.table().equals("demographic") ? "demographic_no" : "id");
        try {
            long id = Long.parseLong(value);
            if (id < 1
                    || !Long.toString(id).equals(value)
                    || !(row.table().equals("demographic")
                                    ? String.format(Locale.ROOT, "%010d", id)
                                    : CdsExportPatientTableSourceReader.key(id))
                            .equals(row.recordKey())) invalid();
        } catch (NumberFormatException malformed) {
            invalid();
        }
        return value;
    }

    private static void verify(
            CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness)
            throws IOException {
        if (witness == null
                || witness.state() == null
                || witness.partial()
                || !Objects.equals(witness.digest(), witness.state().digest())
                || !row.table().equals(witness.table())
                || !id(row).equals(witness.id())
                || !row.recordKey().equals(witness.recordKey())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        witness.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) invalid();
        for (int i = 0; i < witness.state().columns().size(); i++) {
            var column = witness.state().columns().get(i);
            var cell = witness.state().cells().get(i);
            String value = row.fields().get(column.name());
            if (cell.exactNumeric() != null || CdsExactNumeric.codec(column.type()) != null)
                invalid();
            if (value == null) {
                if (!cell.sqlNull()) invalid();
            } else {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                if (cell.sqlNull()
                        || bytes.length != cell.bytes()
                        || !HexFormat.of()
                                .formatHex(CdsImportKeys.digest().digest(bytes))
                                .equals(cell.sha256())) invalid();
            }
        }
    }

    private static String key(String table, String id) {
        return table + ":" + id;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CARE_OCCURRENCE_INVALID");
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }
}
