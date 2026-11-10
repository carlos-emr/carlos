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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Coordinates imported laboratory projections and ordinary native HL7 observations.
 * Changed imported mirrors require review; native results have separate structural membership and complete supporting context.
 * This bounded occurrence component alone does not qualify the complete laboratory source catalogue.
 */
@Service
public class CdsExportLaboratoryOccurrenceService implements CdsExportCategoryOccurrenceAssembler {
    @Override
    public CdsCategory category() {
        return CdsCategory.LABORATORY_RESULTS;
    }

    private static final CdsCategory CATEGORY = CdsCategory.LABORATORY_RESULTS;
    private static final Set<String> TABLES =
            Set.of("cds_laboratory_result", "hl7TextMessage", "hl7TextInfo", "patientLabRouting");
    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportCapturedTextRowsReader rows;

    public CdsExportLaboratoryOccurrenceService(
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
        patient.require(lease);
        Objects.requireNonNull(output);
        control.authorizeCategory(patient.patient(), CATEGORY, true);
        var tables = Set.copyOf(requiredTables);
        if (!lease.document().command().categories().contains(CATEGORY)
                || patientKey == null
                || !patientKey.matches("[0-9a-f]{64}")
                || !tables.containsAll(TABLES)
                || !tables.equals(source.capture().tables().keySet())) invalid();
        new CdsExportMergeScopeValidator(captures)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var captured = readCurrent(lease, patient, tables, source, bounds, control);
        var current = captured.rows();
        var exact = captured.witnesses();
        long[] counts = {0, 0, 0};
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
                    var owned = new HashSet<String>();
                    var consumed = new HashSet<String>();
                    for (var unit :
                            index.tables().getOrDefault("cds_import_unit", Map.of()).values()) {
                        control.checkpoint();
                        if (!CATEGORY.wireName().equals(unit.fields().get("category"))) continue;
                        String address =
                                unit.fields().get("run_id") + ":" + unit.fields().get("unit_key");
                        if (!index.appliedUnits().contains(address)) continue;
                        var references = index.references().get(address);
                        var comparisons = checked.units().get(address);
                        if (references == null
                                || comparisons == null
                                || !references.equals(
                                        comparisons.stream()
                                                .map(
                                                        CdsExportRetainedReceiptVerificationDto
                                                                        .CheckedReference
                                                                ::expected)
                                                .toList())) invalid();
                        if (!checked.unchangedUnits().contains(address)) review();
                        var resultRefs =
                                references.stream()
                                        .filter(ref -> ref.table().equals("cds_laboratory_result"))
                                        .toList();
                        if (resultRefs.size() != 1) review();
                        var row =
                                current.get(
                                        key("cds_laboratory_result", resultRefs.getFirst().id()));
                        if (row == null) review();
                        String lab = row.fields().get("nativeLabNo");
                        var expected = new HashSet<String>();
                        expected.add(key(row.table(), nativeId(row)));
                        if (lab != null) {
                            expected.add(key("hl7TextMessage", lab));
                            for (var candidate : current.values())
                                if (candidate.table().equals("hl7TextInfo")
                                                && lab.equals(candidate.fields().get("lab_no"))
                                        || candidate.table().equals("patientLabRouting")
                                                && lab.equals(candidate.fields().get("lab_no")))
                                    expected.add(key(candidate.table(), nativeId(candidate)));
                        }
                        var actual = new HashSet<String>();
                        for (var ref : references) {
                            String k = key(ref.table(), ref.id());
                            if (!TABLES.contains(ref.table())
                                    || !actual.add(k)
                                    || !current.containsKey(k)
                                    || !ref.digest().equals(exact.get(k).digest())
                                    || !owned.add(k)) review();
                        }
                        if (!expected.equals(actual)
                                || lab != null && actual.size() != 4
                                || lab == null && actual.size() != 1) review();
                        var prepared =
                                new CdsExportRetainedPreparationReader()
                                        .laboratory(index, address)
                                        .value();
                        if (!address.substring(37).equals(row.fields().get("id"))
                                || !address.substring(0, 36).equals(row.fields().get("runId")))
                            invalid();
                        var sourceValue =
                                new CdsExportLaboratoryRetainedSource()
                                        .read(
                                                patient.patient(),
                                                address,
                                                index,
                                                checked,
                                                retained,
                                                streams,
                                                control);
                        var fields =
                                new CdsExportRetainedOccurrenceFields()
                                        .fieldsFor(
                                                patient.patient(),
                                                CATEGORY,
                                                address,
                                                index,
                                                checked,
                                                retained,
                                                control::checkpoint);
                        String root =
                                CdsExportRetainedOccurrencePath.root(fields.getFirst(), CATEGORY);
                        if (!root.equals(row.fields().get("sourcePath"))) invalid();
                        for (var scalar :
                                CdsExportLaboratoryRetainedSource.NATIVE_SCALARS.entrySet())
                            if (!Objects.equals(
                                    prepared.values().get(scalar.getValue()),
                                    row.fields().get(scalar.getKey()))) invalid();
                        String flag =
                                prepared.values()
                                        .get(
                                                "ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsEnum");
                        if (flag == null)
                            flag =
                                    prepared.values()
                                            .get(
                                                    "ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsPlainText");
                        if (!Objects.equals(flag, row.fields().get("abnormalFlag"))
                                || !nativeDate(
                                        prepared.collection(),
                                        row.fields().get("collectionDate"),
                                        row.fields().get("collectionPrecision"))
                                || !nativeDate(
                                        prepared.requisition(),
                                        row.fields().get("requisitionDate"),
                                        row.fields().get("requisitionPrecision"))) invalid();
                        var assessments = assess(current, actual);
                        var assessedRows = actual.stream().map(current::get).toList();
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        assessedRows, assessments, 1_000_000, control::checkpoint);
                        var content =
                                new CdsExportRetainedSectionWriter()
                                        .prepareChanges(
                                                fields,
                                                streams,
                                                sourceValue.changes(),
                                                control::checkpoint);
                        for (String field : sourceValue.sourceFields())
                            if (!consumed.add(field)) invalid();
                        var identity =
                                new CdsExportIdentityDto(
                                        patientKey,
                                        CATEGORY,
                                        CdsImportKeys.hash(
                                                "carlos-native-occurrence-v1",
                                                row.table(),
                                                nativeId(row)));
                        control.authorizeCategory(patient.patient(), CATEGORY, true);
                        streams.requireActive();
                        xml.emit(new CdsExportXmlComposer.Section(identity, content.content()));
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        identity,
                                        index.sourceManifestSha256(),
                                        row.table(),
                                        nativeId(row),
                                        address,
                                        assessments,
                                        content.retainedFields(),
                                        content.replacedFields()));
                        control.authorizeCategory(patient.patient(), CATEGORY, true);
                        counts[0]++;
                        counts[1] = Math.addExact(counts[1], sourceValue.sourceFields().size());
                    }
                    var nativeSources =
                            nativeSources(patient.patient(), current, index, control::checkpoint);
                    if (!nativeSources.isEmpty()
                            && !lease.document()
                                    .command()
                                    .categories()
                                    .contains(CdsCategory.NEW_CATEGORY)) review();
                    var zone =
                            nativeSources.isEmpty()
                                    ? null
                                    : new CdsExportClinicalZoneService(captures)
                                            .read(
                                                    lease,
                                                    patient,
                                                    tables,
                                                    source,
                                                    bounds.fields(),
                                                    bounds.bytes(),
                                                    control)
                                            .zone();
                    for (var nativeSource : nativeSources) {
                        var mapper = new CdsExportNativeLaboratoryMapper();
                        var assessments = CdsExportNativeLaboratoryMapper.assessments(nativeSource);
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        nativeSource.rows(),
                                        assessments,
                                        1_000_000,
                                        control::checkpoint);
                        for (var row : nativeSource.rows())
                            if (!owned.add(key(row.table(), nativeId(row)))) invalid();
                        var mapped = mapper.map(nativeSource, zone);
                        if (mapped.size() != nativeSource.parsed().observations().size()) invalid();
                        for (var value : mapped) {
                            String id = nativeSource.id() + ":" + value.ordinal();
                            var identity =
                                    new CdsExportIdentityDto(
                                            patientKey,
                                            CATEGORY,
                                            CdsImportKeys.hash(
                                                    "carlos-native-occurrence-v1",
                                                    "hl7TextMessage",
                                                    id));
                            streams.requireActive();
                            control.authorizeCategory(patient.patient(), CATEGORY, true);
                            xml.emit(
                                    new CdsExportXmlComposer.Section(
                                            identity, body -> mapper.write(value, body)));
                            output.accept(
                                    new CdsExportClinicalOccurrenceService.Receipt(
                                            identity,
                                            index.sourceManifestSha256(),
                                            "hl7TextMessage",
                                            id,
                                            null,
                                            assessments,
                                            Set.of(),
                                            Set.of()));
                            control.authorizeCategory(patient.patient(), CATEGORY, true);
                            counts[0]++;
                            counts[2]++;
                        }
                    }
                    var expectedFields = new HashSet<String>();
                    for (var field : retained)
                        if (field.category() == CATEGORY) expectedFields.add(field.id());
                    if (!consumed.equals(expectedFields)) invalid();
                    if (!owned.equals(current.keySet())) review();
                    streams.requireActive();
                    captures.authorizeSources(lease, patient, tables, control);
                    patient.require(lease);
                    control.authorizeCategory(patient.patient(), CATEGORY, true);
                    control.checkpoint();
                });
        return new CdsExportClinicalOccurrenceService.Result(
                source.manifest().plaintextSha256(),
                CATEGORY,
                counts[0],
                counts[0] - counts[2],
                counts[2],
                counts[1]);
    }

    record NativeSource(
            String id,
            CdsExportCapturedTextRowDto message,
            CdsExportCapturedTextRowDto information,
            CdsExportCapturedTextRowDto routing,
            CdsExportNativeLaboratoryParser.Parsed parsed) {
        List<CdsExportCapturedTextRowDto> rows() {
            return List.of(message, information, routing);
        }

        @Override
        public String toString() {
            return "NativeSource[protected]";
        }
    }

    List<NativeSource> nativeSources(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportRetainedIndexDto index,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (!lease.document().command().categories().contains(CATEGORY)) return List.of();
        patient.require(lease);
        control.authorizeCategory(patient.patient(), CATEGORY, true);
        if (!tables.containsAll(TABLES)
                || !tables.equals(source.capture().tables().keySet())
                || !source.manifest().plaintextSha256().equals(index.sourceManifestSha256()))
            invalid();
        new CdsExportMergeScopeValidator(captures)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var result =
                nativeSources(
                        patient.patient(),
                        readCurrent(lease, patient, tables, source, bounds, control).rows(),
                        index,
                        control::checkpoint);
        control.authorizeCategory(patient.patient(), CATEGORY, true);
        return result;
    }

    static List<NativeSource> nativeSources(
            int patient,
            Map<String, CdsExportCapturedTextRowDto> current,
            CdsExportRetainedIndexDto index,
            Runnable checkpoint)
            throws IOException {
        requireOwnership(patient, current);
        var imported = new HashSet<String>();
        for (var address : index.appliedUnits())
            for (var ref : index.references().getOrDefault(address, List.of()))
                if (ref.table().equals("hl7TextMessage")) imported.add(ref.id());
        var mirrors = new HashSet<String>();
        var information = new HashMap<String, CdsExportCapturedTextRowDto>();
        var routing = new HashMap<String, CdsExportCapturedTextRowDto>();
        for (var row : current.values()) {
            if (row.table().equals("cds_laboratory_result")
                    && row.fields().get("nativeLabNo") != null)
                mirrors.add(row.fields().get("nativeLabNo"));
            if (row.table().equals("hl7TextInfo")) information.put(row.fields().get("lab_no"), row);
            if (row.table().equals("patientLabRouting"))
                routing.put(row.fields().get("lab_no"), row);
        }
        var sources = new ArrayList<NativeSource>();
        for (var row : current.values()) {
            checkpoint.run();
            if (!row.table().equals("hl7TextMessage")) continue;
            String id = nativeId(row);
            if (mirrors.contains(id)) {
                if (!imported.contains(id)) review();
                else continue;
            }
            if (imported.contains(id)) review();
            var parsed =
                    new CdsExportNativeLaboratoryParser()
                            .parse(row.fields().get("message"), checkpoint);
            sources.add(new NativeSource(id, row, information.get(id), routing.get(id), parsed));
        }
        sources.sort(Comparator.comparing(NativeSource::id));
        return List.copyOf(sources);
    }

    private record Captured(
            Map<String, CdsExportCapturedTextRowDto> rows,
            Map<String, CdsExportNativeRowReceiptDto> witnesses) {}

    private Captured readCurrent(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        var current = new TreeMap<String, CdsExportCapturedTextRowDto>();
        var exact = new HashMap<String, CdsExportNativeRowReceiptDto>();
        long[] memory = {0};
        var read =
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
                            control.checkpoint();
                            String id = nativeId(row);
                            var plan = plan(row.table());
                            if (!row.fields().keySet().equals(Set.copyOf(plan.columns()))
                                    || !row.exactNumbers().isEmpty()) invalid();
                            if (current.size() >= bounds.nativeRows()) limit();
                            for (var value : row.fields().values())
                                if (value != null)
                                    memory[0] =
                                            Math.addExact(
                                                    memory[0],
                                                    value.getBytes(StandardCharsets.UTF_8).length
                                                            + 64L);
                            memory[0] =
                                    Math.addExact(memory[0], 1024L + row.fields().size() * 256L);
                            if (memory[0] > bounds.indexBytes()) limit();
                            if (current.putIfAbsent(key(row.table(), id), row) != null) invalid();
                        });
        if (!read.equals(source.capture())) invalid();
        var scanned =
                witnesses.scan(
                        lease,
                        patient,
                        tables,
                        source,
                        bounds.fields(),
                        bounds.bytes(),
                        control,
                        witness -> {
                            if (!TABLES.contains(witness.table())) return;
                            if (exact.size() >= bounds.nativeRows()) limit();
                            if (exact.putIfAbsent(key(witness.table(), witness.id()), witness)
                                    != null) invalid();
                        });
        if (!scanned.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || !current.keySet().equals(exact.keySet())) invalid();
        for (var entry : current.entrySet()) verify(entry.getValue(), exact.get(entry.getKey()));
        for (String table : TABLES)
            if (current.values().stream().filter(row -> row.table().equals(table)).count()
                    != source.capture().tables().get(table).rows()) invalid();
        requireOwnership(patient.patient(), current);
        return new Captured(Map.copyOf(current), Map.copyOf(exact));
    }

    private static CdsExportPatientTableReadSupport.Plan plan(String table) throws IOException {
        return switch (table) {
            case "cds_laboratory_result" ->
                    CdsExportPatientTableReadSupport.Plan.LABORATORY_RESULTS;
            case "hl7TextMessage" -> CdsExportPatientTableReadSupport.Plan.LABORATORY_MESSAGES;
            case "hl7TextInfo" -> CdsExportPatientTableReadSupport.Plan.LABORATORY_INFORMATION;
            case "patientLabRouting" -> CdsExportPatientTableReadSupport.Plan.LABORATORY_ROUTING;
            default -> throw new IOException("CDS_EXPORT_LABORATORY_OCCURRENCE_INVALID");
        };
    }

    private static String nativeId(CdsExportCapturedTextRowDto row) throws IOException {
        String id = row.fields().get(row.table().equals("hl7TextMessage") ? "lab_id" : "id");
        try {
            if (row.table().equals("cds_laboratory_result")) {
                if (id == null || !id.matches("[0-9a-f]{64}") || !id.equals(row.recordKey()))
                    invalid();
            } else {
                long number = Long.parseLong(id);
                if (number < 1
                        || !Long.toString(number).equals(id)
                        || !CdsExportPatientTableSourceReader.key(number).equals(row.recordKey()))
                    invalid();
            }
        } catch (NumberFormatException malformed) {
            invalid();
        }
        return id;
    }

    private static void verify(
            CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness)
            throws IOException {
        if (witness == null
                || witness.state() == null
                || witness.partial()
                || !Objects.equals(witness.digest(), witness.state().digest())
                || !row.table().equals(witness.table())
                || !nativeId(row).equals(witness.id())
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

    static void requireOwnership(int patient, Map<String, CdsExportCapturedTextRowDto> current)
            throws IOException {
        var routes = new HashMap<String, String>();
        var information = new HashMap<String, String>();
        var results = new HashMap<String, String>();
        var messages = new HashSet<String>();
        for (var row : current.values()) {
            String id = nativeId(row);
            switch (row.table()) {
                case "hl7TextMessage" -> {
                    if (!messages.add(id)) invalid();
                }
                case "patientLabRouting" -> {
                    if (!Integer.toString(patient).equals(row.fields().get("demographic_no"))
                            || !"HL7".equals(row.fields().get("lab_type"))
                            || routes.putIfAbsent(row.fields().get("lab_no"), id) != null) review();
                }
                case "hl7TextInfo" -> {
                    if (information.putIfAbsent(row.fields().get("lab_no"), id) != null) review();
                }
                case "cds_laboratory_result" -> {
                    if (!Integer.toString(patient).equals(row.fields().get("demographicNo")))
                        invalid();
                    String lab = row.fields().get("nativeLabNo");
                    if (lab == null
                            ? !"Y".equals(row.fields().get("sourceBlocked"))
                            : "Y".equals(row.fields().get("sourceBlocked"))
                                    || results.putIfAbsent(lab, id) != null) review();
                }
                default -> invalid();
            }
        }
        if (!messages.equals(routes.keySet())
                || !messages.equals(information.keySet())
                || !messages.containsAll(results.keySet())) review();
    }

    private static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assess(
            Map<String, CdsExportCapturedTextRowDto> current, Set<String> selected) {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (String key : selected) {
            var row = current.get(key);
            var assessment = new CdsExportNativeFieldAssessments(row, result);
            for (String column : row.fields().keySet()) {
                if (!row.table().equals("cds_laboratory_result")) {
                    assessment.operational(
                            column,
                            "UNCHANGED_GENERATED_LAB_MIRROR_WITH_COMPLETE_STRUCTURED_SOURCE");
                    continue;
                }
                String path = CdsExportLaboratoryRetainedSource.NATIVE_SCALARS.get(column);
                if (path == null)
                    path =
                            switch (column) {
                                case "abnormalFlag" -> "ResultNormalAbnormalFlag";
                                case "collectionDate", "collectionPrecision" ->
                                        "CollectionDateTime";
                                case "requisitionDate", "requisitionPrecision" ->
                                        "LabRequisitionDateTime";
                                default -> null;
                            };
                if (path == null)
                    assessment.operational(column, "LOCAL_IDENTITY_PROVENANCE_AND_NATIVE_LAB_LINK");
                else {
                    String value = row.fields().get(column);
                    assessment.set(
                            column,
                            value == null || value.isEmpty() ? Outcome.ABSENT : Outcome.MAPPED,
                            path,
                            "VERIFIED_UNCHANGED_PREPARED_LABORATORY_VALUE");
                }
            }
        }
        return Map.copyOf(result);
    }

    static boolean nativeDate(CdsTemporalDto prepared, String value, String precision)
            throws IOException {
        if (prepared == null || !prepared.precision().name().equals(precision)) return false;
        new CdsExportPreparedTemporalMapper().map(prepared, true);
        if (prepared.precision() == CdsTemporalDto.Precision.UNKNOWN) return value == null;
        if (prepared.appliedShiftDays() == 0) return Objects.equals(prepared.original(), value);
        if (value == null) return false;
        try {
            boolean clock =
                    prepared.precision() == CdsTemporalDto.Precision.LOCAL_DATE_TIME
                            || prepared.precision() == CdsTemporalDto.Precision.OFFSET_DATE_TIME;
            if (!clock && prepared.precision() != CdsTemporalDto.Precision.FULL_DATE) return false;
            var parsed =
                    CdsTemporal.parse(
                            null,
                            null,
                            clock ? null : value,
                            clock ? value : null,
                            "LaboratoryDate");
            return parsed.precision() == prepared.precision()
                    && Objects.equals(parsed.earliest(), prepared.earliest())
                    && Objects.equals(parsed.latest(), prepared.latest())
                    && Objects.equals(parsed.localDateTime(), prepared.localDateTime())
                    && Objects.equals(parsed.offsetDateTime(), prepared.offsetDateTime());
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static String key(String table, String id) {
        return table + ":" + id;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_LABORATORY_OCCURRENCE_INVALID");
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }
}
