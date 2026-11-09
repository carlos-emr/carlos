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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Synchronous assembly of supported occurrences from one authenticated patient generation.
 * Private XML and receipts remain unpublished until this entire scan and later independent
 * document/coverage/delivery checks pass. This service does not qualify an export profile.
 */
@Service
public class CdsExportClinicalOccurrenceService {
    public record Bounds(
            long fields,
            long bytes,
            int indexRows,
            long indexBytes,
            int nativeRows,
            int lookupRows) {
        public Bounds {
            if (fields < 1
                    || fields > 100_000_000
                    || bytes < 1
                    || indexRows < 1
                    || indexRows > 1_000_000
                    || indexBytes < 1
                    || indexBytes > 64L << 20
                    || nativeRows < 1
                    || nativeRows > 8192
                    || lookupRows < 1
                    || lookupRows > 131_072)
                throw new IllegalArgumentException("CDS_EXPORT_OCCURRENCE_BOUNDS_INVALID");
        }
    }

    public record Receipt(
            CdsExportIdentityDto identity,
            String sourceManifestSha256,
            String table,
            String nativeId,
            String importedUnit,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Set<String> retainedFields,
            Set<String> replacedFields) {
        public Receipt {
            fields = Map.copyOf(fields);
            retainedFields = Set.copyOf(retainedFields);
            replacedFields = Set.copyOf(replacedFields);
        }

        @Override
        public String toString() {
            return "CdsExportClinicalOccurrenceReceipt[protected]";
        }
    }

    @FunctionalInterface
    public interface Receipts {
        void accept(Receipt receipt) throws IOException;

        /** Attachment membership belongs to the same private output transaction as the report and its receipt. */
        default void attachment(CdsExportIdentityDto report, CdsExportPackageMemberDto member)
                throws IOException {
            throw new IOException("CDS_EXPORT_REPORT_ATTACHMENT_SINK_REQUIRED");
        }
    }

    public record Result(
            String sourceManifestSha256,
            CdsCategory category,
            long nativeRows,
            long importedRows,
            long nativeOnlyRows,
            long retainedSourceFields) {
        @Override
        public String toString() {
            return "CdsExportClinicalOccurrenceResult[protected]";
        }
    }

    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportHistoryOccurrenceService histories;
    private final CdsExportCategoryOccurrenceRegistry categories;

    public CdsExportClinicalOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures) {
        this(
                values,
                verification,
                witnesses,
                captures,
                new CdsExportCapturedTextRowsReader(captures));
    }

    CdsExportClinicalOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportCapturedTextRowsReader rows) {
        this(
                values,
                verification,
                witnesses,
                captures,
                rows,
                (CdsExportImmunizationOccurrenceService) null,
                (CdsExportAppointmentOccurrenceService) null);
    }

    public CdsExportClinicalOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationOccurrenceService immunizations) {
        this(
                values,
                verification,
                witnesses,
                captures,
                new CdsExportCapturedTextRowsReader(captures),
                Objects.requireNonNull(immunizations),
                null);
    }

    public CdsExportClinicalOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationOccurrenceService immunizations,
            CdsExportAppointmentOccurrenceService appointments) {
        this(
                values,
                verification,
                witnesses,
                captures,
                new CdsExportCapturedTextRowsReader(captures),
                Objects.requireNonNull(immunizations),
                Objects.requireNonNull(appointments));
    }

    public CdsExportClinicalOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationOccurrenceService immunizations,
            CdsExportAppointmentOccurrenceService appointments,
            CdsExportHistoryOccurrenceService histories) {
        this(
                values,
                verification,
                witnesses,
                captures,
                new CdsExportCapturedTextRowsReader(captures),
                Objects.requireNonNull(immunizations),
                Objects.requireNonNull(appointments),
                Objects.requireNonNull(histories));
    }

    public CdsExportClinicalOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationOccurrenceService immunizations,
            CdsExportAppointmentOccurrenceService appointments,
            CdsExportHistoryOccurrenceService histories,
            CdsExportLaboratoryOccurrenceService laboratories) {
        this(
                values,
                verification,
                witnesses,
                captures,
                new CdsExportCapturedTextRowsReader(captures),
                Objects.requireNonNull(immunizations),
                Objects.requireNonNull(appointments),
                Objects.requireNonNull(histories),
                Objects.requireNonNull(laboratories));
    }

    @Autowired
    public CdsExportClinicalOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportHistoryOccurrenceService histories,
            CdsExportCategoryOccurrenceRegistry categories) {
        this(
                values,
                verification,
                witnesses,
                captures,
                new CdsExportCapturedTextRowsReader(captures),
                histories,
                categories);
    }

    private CdsExportClinicalOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportCapturedTextRowsReader rows,
            CdsExportImmunizationOccurrenceService immunizations,
            CdsExportAppointmentOccurrenceService appointments) {
        this(values, verification, witnesses, captures, rows, immunizations, appointments, null);
    }

    private CdsExportClinicalOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportCapturedTextRowsReader rows,
            CdsExportImmunizationOccurrenceService immunizations,
            CdsExportAppointmentOccurrenceService appointments,
            CdsExportHistoryOccurrenceService histories) {
        this(
                values,
                verification,
                witnesses,
                captures,
                rows,
                immunizations,
                appointments,
                histories,
                null);
    }

    private CdsExportClinicalOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportCapturedTextRowsReader rows,
            CdsExportImmunizationOccurrenceService immunizations,
            CdsExportAppointmentOccurrenceService appointments,
            CdsExportHistoryOccurrenceService histories,
            CdsExportLaboratoryOccurrenceService laboratories) {
        this(
                values,
                verification,
                witnesses,
                captures,
                rows,
                histories,
                legacy(immunizations, appointments, laboratories));
    }

    private CdsExportClinicalOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportCapturedTextRowsReader rows,
            CdsExportHistoryOccurrenceService histories,
            CdsExportCategoryOccurrenceRegistry categories) {
        this.histories = histories;
        this.categories = Objects.requireNonNull(categories);
        this.values = Objects.requireNonNull(values);
        this.verification = Objects.requireNonNull(verification);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.captures = Objects.requireNonNull(captures);
        this.rows = Objects.requireNonNull(rows);
    }

    private static CdsExportCategoryOccurrenceRegistry legacy(
            CdsExportImmunizationOccurrenceService immunizations,
            CdsExportAppointmentOccurrenceService appointments,
            CdsExportLaboratoryOccurrenceService laboratories) {
        var selected =
                new EnumMap<CdsCategory, CdsExportCategoryOccurrenceAssembler>(CdsCategory.class);
        if (immunizations != null) selected.put(CdsCategory.IMMUNIZATIONS, immunizations);
        if (appointments != null) selected.put(CdsCategory.APPOINTMENTS, appointments);
        if (laboratories != null) selected.put(CdsCategory.LABORATORY_RESULTS, laboratories);
        return new CdsExportCategoryOccurrenceRegistry(selected);
    }

    /** Keeps seven history wire positions within one authenticated patient source session. */
    public void withHistorySource(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
            Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportHistoryOccurrenceService.Work work)
            throws IOException {
        if (histories == null) scope();
        histories.withSource(
                lease,
                patient,
                patientKey,
                requiredTables,
                source,
                payloadManifest,
                bounds,
                control,
                xml,
                work);
    }

    public Result append(
            CdsCategory category,
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
            Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            Receipts output)
            throws IOException {
        Objects.requireNonNull(xml);
        var result = new Result[1];
        xml.source(
                emitter ->
                        result[0] =
                                assemble(
                                        category,
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

    private Result assemble(
            CdsCategory category,
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
            Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            Receipts output)
            throws IOException {
        Objects.requireNonNull(output);
        patient.require(lease);
        if (CdsExportNoteSourceReader.CATEGORIES.contains(category)) {
            if (histories == null) scope();
            return histories.append(
                    category,
                    lease,
                    patient,
                    patientKey,
                    requiredTables,
                    source,
                    payloadManifest,
                    bounds,
                    control,
                    xml,
                    output);
        }
        var assembler = categories.find(category);
        if (assembler != null)
            return assembler.append(
                    lease,
                    patient,
                    patientKey,
                    requiredTables,
                    source,
                    payloadManifest,
                    bounds,
                    control,
                    xml,
                    output);
        if (!Set.of(
                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                CdsCategory.MEDICATIONS_AND_TREATMENTS)
                        .contains(category)
                || !lease.document().command().categories().contains(category)
                || patientKey == null
                || !patientKey.matches("[0-9a-f]{64}")) scope();
        String table = category == CdsCategory.MEDICATIONS_AND_TREATMENTS ? "drugs" : "allergies",
                idColumn = table.equals("drugs") ? "drugid" : "allergyid";
        var tables = Set.copyOf(requiredTables);
        if (!tables.equals(source.capture().tables().keySet())
                || !tables.containsAll(Set.of(table, "partial_date"))
                || table.equals("drugs") && !tables.contains("provider")) scope();
        if (source.capture().tables().get(table).rows() > bounds.nativeRows()) limit();
        new CdsExportMergeScopeValidator(rows)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        long[] counts = new long[4];
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
                        scope();
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
                    var imported = new HashMap<String, String>();
                    for (var unit : index.tables().get("cds_import_unit").values())
                        if (category.wireName().equals(unit.fields().get("category"))) {
                            control.checkpoint();
                            String address =
                                    unit.fields().get("run_id")
                                            + ":"
                                            + unit.fields().get("unit_key");
                            if (!index.appliedUnits().contains(address)) continue;
                            var refs =
                                    index.references().get(address).stream()
                                            .filter(ref -> ref.table().equals(table))
                                            .toList();
                            if (refs.size() != 1) review();
                            if (imported.putIfAbsent(refs.getFirst().id(), address) != null)
                                ambiguous();
                        }
                    var exact =
                            scanWitnesses(lease, patient, tables, source, bounds, control, table);
                    var lookups =
                            readLookups(lease, patient, tables, source, bounds, control, table);
                    var precision = lookups.precision();
                    var providers = lookups.providers();
                    var usedFields = new HashSet<String>();
                    var finished =
                            rows.forEach(
                                    lease,
                                    patient,
                                    tables,
                                    table,
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    67_108_864,
                                    control,
                                    row -> {
                                        streams.requireActive();
                                        patient.require(lease);
                                        String id = row.fields().get(idColumn);
                                        var witness = exact.remove(id);
                                        if (witness == null) scope();
                                        String importedUnit = imported.remove(id);
                                        var dates = precision.remove(id);
                                        if (dates == null) dates = List.of();
                                        Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>
                                                fields;
                                        CdsExportXmlComposer.Content content;
                                        Set<String> kept = Set.of(), replaced = Set.of();
                                        if (table.equals("drugs")) {
                                            if (importedUnit == null) {
                                                var mapping =
                                                        new CdsExportMedicationMapper()
                                                                .map(
                                                                        patient.patient(),
                                                                        row,
                                                                        dates,
                                                                        providers);
                                                if (!mapping.nativeRowRepresentable()) review();
                                                fields = mapping.fields();
                                                content =
                                                        writer ->
                                                                new CdsExportMedicationXmlWriter()
                                                                        .write(
                                                                                mapping.value(),
                                                                                writer);
                                            } else {
                                                var decision =
                                                        new CdsExportMedicationFieldReconciler()
                                                                .reconcile(
                                                                        patient.patient(),
                                                                        index,
                                                                        importedUnit,
                                                                        checked,
                                                                        row,
                                                                        witness,
                                                                        dates,
                                                                        providers);
                                                if (!decision.mapping().nativeRowRepresentable())
                                                    review();
                                                var plan =
                                                        new CdsExportMedicationRetainedAssembler()
                                                                .prepare(
                                                                        patient.patient(),
                                                                        decision,
                                                                        index,
                                                                        checked,
                                                                        retained,
                                                                        streams,
                                                                        control::checkpoint);
                                                fields = decision.mapping().fields();
                                                content = plan.content();
                                                kept = plan.retainedFields();
                                                replaced = plan.replacedFields();
                                            }
                                        } else if (importedUnit == null) {
                                            var mapping =
                                                    new CdsExportAllergyMapper()
                                                            .map(patient.patient(), row, dates);
                                            if (!mapping.nativeRowRepresentable()) review();
                                            fields = mapping.fields();
                                            content =
                                                    writer ->
                                                            new CdsExportAllergyXmlWriter()
                                                                    .write(mapping.value(), writer);
                                        } else {
                                            var decision =
                                                    new CdsExportAllergyFieldReconciler()
                                                            .reconcile(
                                                                    patient.patient(),
                                                                    index,
                                                                    importedUnit,
                                                                    checked,
                                                                    row,
                                                                    witness,
                                                                    dates);
                                            if (!decision.mapping().nativeRowRepresentable())
                                                review();
                                            var plan =
                                                    new CdsExportAllergyRetainedAssembler()
                                                            .prepare(
                                                                    patient.patient(),
                                                                    decision,
                                                                    index,
                                                                    checked,
                                                                    retained,
                                                                    streams,
                                                                    control::checkpoint);
                                            fields = decision.mapping().fields();
                                            content = plan.content();
                                            kept = plan.retainedFields();
                                            replaced = plan.replacedFields();
                                        }
                                        var assessedRows =
                                                new ArrayList<CdsExportCapturedTextRowDto>();
                                        assessedRows.add(row);
                                        assessedRows.addAll(dates);
                                        if (table.equals("drugs")) {
                                            String provider = row.fields().get("provider_no");
                                            if (provider != null && !provider.isEmpty()) {
                                                var resolved = providers.get(provider);
                                                if (resolved == null) scope();
                                                assessedRows.add(resolved);
                                            }
                                        }
                                        new CdsExportNativeFieldAssessmentValidator()
                                                .requireRepresentable(
                                                        assessedRows,
                                                        fields,
                                                        1_000_000,
                                                        control::checkpoint);
                                        for (String field : kept)
                                            if (!usedFields.add(field)) scope();
                                        for (String field : replaced)
                                            if (!usedFields.add(field)) scope();
                                        var identity =
                                                new CdsExportIdentityDto(
                                                        patientKey,
                                                        category,
                                                        CdsImportKeys.hash(
                                                                "carlos-native-occurrence-v1",
                                                                table,
                                                                id));
                                        xml.emit(
                                                new CdsExportXmlComposer.Section(
                                                        identity, content));
                                        output.accept(
                                                new Receipt(
                                                        identity,
                                                        index.sourceManifestSha256(),
                                                        table,
                                                        id,
                                                        importedUnit,
                                                        fields,
                                                        kept,
                                                        replaced));
                                        counts[0]++;
                                        counts[importedUnit == null ? 2 : 1]++;
                                        counts[3] =
                                                Math.addExact(
                                                        counts[3],
                                                        kept.size() + (long) replaced.size());
                                    });
                    if (!finished.equals(source.capture())
                            || !precision.isEmpty()
                            || !exact.isEmpty()
                            || counts[0] != source.capture().tables().get(table).rows()) scope();
                    if (!imported.isEmpty()) review();
                    var expectedFields = new HashSet<String>();
                    for (var field : retained)
                        if (field.category() == category) expectedFields.add(field.id());
                    if (!usedFields.equals(expectedFields)) scope();
                    streams.requireActive();
                    captures.authorizeSources(lease, patient, tables, control);
                    patient.require(lease);
                    control.checkpoint();
                });
        return new Result(
                source.manifest().plaintextSha256(),
                category,
                counts[0],
                counts[1],
                counts[2],
                counts[3]);
    }

    private record Lookups(
            Map<String, List<CdsExportCapturedTextRowDto>> precision,
            Map<String, CdsExportCapturedTextRowDto> providers) {
        @Override
        public String toString() {
            return "Lookups[protected]";
        }
    }

    private Lookups readLookups(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            Bounds bounds,
            CdsExportRunControl control,
            String table)
            throws IOException {
        var precision = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        var providers = new HashMap<String, CdsExportCapturedTextRowDto>();
        long[] lookup = new long[2];
        var lookupTables =
                table.equals("drugs") ? Set.of("partial_date", "provider") : Set.of("partial_date");
        var first =
                rows.forEachTable(
                        lease,
                        patient,
                        tables,
                        lookupTables,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        65_536,
                        control,
                        row -> {
                            if (++lookup[0] > bounds.lookupRows()) limit();
                            for (var value : row.fields().values())
                                if (value != null)
                                    lookup[1] =
                                            Math.addExact(
                                                    lookup[1],
                                                    value.getBytes(StandardCharsets.UTF_8).length
                                                            + 64L);
                            if (lookup[1] > bounds.indexBytes()) limit();
                            if (row.table().equals("provider")) {
                                String id = row.fields().get("provider_no");
                                if (id == null
                                        || !row.fields()
                                                .keySet()
                                                .equals(
                                                        io.github.carlos_emr.carlos.commn.dto
                                                                .CdsExportProviderRowDto.COLUMNS)
                                        || !CdsExportPublicProviderSourceReader.key(id)
                                                .equals(row.recordKey())
                                        || providers.putIfAbsent(id, row) != null) scope();
                                return;
                            }
                            String owner = row.fields().get("table_name");
                            if (!Set.of("1", "2").contains(owner)) scope();
                            if (!owner.equals(table.equals("drugs") ? "2" : "1")) {
                                String otherTable = owner.equals("1") ? "allergies" : "drugs";
                                var otherCategory =
                                        owner.equals("1")
                                                ? CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS
                                                : CdsCategory.MEDICATIONS_AND_TREATMENTS;
                                if (!tables.contains(otherTable)
                                        || !lease.document()
                                                .command()
                                                .categories()
                                                .contains(otherCategory)) scope();
                                return;
                            }
                            String parent = row.fields().get("table_id");
                            if (parent == null) scope();
                            precision
                                    .computeIfAbsent(parent, ignored -> new ArrayList<>())
                                    .add(row);
                        });
        if (!first.equals(source.capture())) scope();
        return new Lookups(precision, providers);
    }

    private Map<String, CdsExportNativeRowReceiptDto> scanWitnesses(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            Bounds bounds,
            CdsExportRunControl control,
            String table)
            throws IOException {
        var exact = new HashMap<String, CdsExportNativeRowReceiptDto>();
        long[] witnessBytes = {0};
        var witnessScan =
                witnesses.scan(
                        lease,
                        patient,
                        tables,
                        source,
                        bounds.fields(),
                        bounds.bytes(),
                        control,
                        witness -> {
                            if (!witness.table().equals(table)) return;
                            if (exact.size() >= bounds.nativeRows()) limit();
                            witnessBytes[0] =
                                    Math.addExact(
                                            witnessBytes[0],
                                            1024L
                                                    + (witness.state() == null
                                                            ? 0
                                                            : witness.state().columns().size()
                                                                    * 256L));
                            if (witnessBytes[0] > bounds.indexBytes()
                                    || exact.putIfAbsent(witness.id(), witness) != null) limit();
                        });
        if (!witnessScan.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || exact.size() != source.capture().tables().get(table).rows()) scope();
        return exact;
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

    private static void ambiguous() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
    }
}
