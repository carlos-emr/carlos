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
            int lookupRows,
            int windowRows,
            long windowBytes) {
        /**
         * The window of a section that reads its rows window by window
         * ({@link CdsExportSectionWindowPlanner}): {@value #WINDOW_ROWS} owners and 64 MiB of
         * loaded state. These are the two sizes that used to refuse a chart, so every chart that
         * was exported before is still read whole, in one window, with the same output; a larger
         * chart is read in several windows instead of being refused.
         */
        public static final int WINDOW_ROWS = 8192;

        public static final long WINDOW_BYTES = 64L << 20;

        public Bounds {
            if (fields < 1
                    || bytes < 1
                    || indexRows < 1
                    || indexRows > 1_000_000
                    || indexBytes < 1
                    || indexBytes > 64L << 20
                    || nativeRows < 1
                    || nativeRows > 8192
                    || lookupRows < 1
                    || lookupRows > 131_072
                    || windowRows < 1
                    || windowBytes < 1)
                throw new IllegalArgumentException("CDS_EXPORT_OCCURRENCE_BOUNDS_INVALID");
        }

        /** The installed window; the four middle components belong to sections not yet windowed. */
        public Bounds(
                long fields, long bytes, int indexRows, long indexBytes, int nativeRows, int lookupRows) {
            this(
                    fields,
                    bytes,
                    indexRows,
                    indexBytes,
                    nativeRows,
                    lookupRows,
                    WINDOW_ROWS,
                    WINDOW_BYTES);
        }

        /** The same bounds with another window size; a test states a small one. */
        public Bounds withWindow(int rows, long windowBytes) {
            return new Bounds(
                    fields, bytes, indexRows, indexBytes, nativeRows, lookupRows, rows, windowBytes);
        }

        CdsExportSectionWindowPlanner.Limits window() {
            return new CdsExportSectionWindowPlanner.Limits(windowRows, windowBytes);
        }

        /**
         * The value of {@link #fields} and {@link #bytes} in the installed plan: a chart's
         * capture has no size limit. The two components remain because every read of a capture
         * states what its caller will accept, and tests state small values.
         */
        public static final long UNLIMITED = Long.MAX_VALUE;
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
        // A prescription's reasons are part of it: a medication capture without them would drop
        // every indication silently.
        if (!tables.equals(source.capture().tables().keySet())
                || !tables.containsAll(Set.of(table, "partial_date"))
                || table.equals("drugs")
                        && !tables.containsAll(
                                Set.of("provider", CdsExportMedicationReasonMapper.TABLE)))
            scope();
        long tableRows = source.capture().tables().get(table).rows();
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
                    var usedFields = new HashSet<String>();
                    // [0] child rows (precision, reasons, drug references) taken by the window of
                    // their owner, over every window; [1] the child rows the capture holds.
                    long[] taken = new long[2];
                    // The chart is read whole when it fits one window, as it always was, and
                    // window by window otherwise (CdsExportSectionWindowPlanner): witnesses,
                    // lookups and rows of one window are held at a time.
                    CdsExportSectionWindowPlanner.forEachWindow(
                            tableRows,
                            bounds.window(),
                            owners -> {
                                var listed =
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
                                                row -> owners.add(row.fields().get(idColumn)));
                                if (!listed.equals(source.capture())) scope();
                            },
                            (window, budget) -> {
                    var exact =
                            scanWitnesses(
                                    lease, patient, tables, source, bounds, control, table, window,
                                    budget);
                    if (exact == null) return false;
                    var lookups =
                            readLookups(
                                    lease, patient, tables, source, bounds, control, table, window,
                                    budget);
                    if (lookups == null) return false;
                    var precision = lookups.precision();
                    var providers = lookups.providers();
                    var references = lookups.references();
                    var referenceRows = Map.copyOf(references);
                    var reasons = lookups.reasons();
                    taken[0] = Math.addExact(taken[0], lookups.taken());
                    taken[1] = lookups.children();
                    long[] emitted = {0};
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
                                        if (!window.contains(id)) {
                                            // A row with no id belongs to no window: refused.
                                            if (id == null) scope();
                                            return;
                                        }
                                        var witness = exact.remove(id);
                                        if (witness == null) scope();
                                        String importedUnit = imported.remove(id);
                                        var dates = precision.remove(id);
                                        if (dates == null) dates = List.of();
                                        Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>
                                                fields;
                                        CdsExportXmlComposer.Content content;
                                        Set<String> kept = Set.of(), replaced = Set.of();
                                        var indications = reasons.remove(id);
                                        if (indications == null) indications = List.of();
                                        if (table.equals("drugs")) {
                                            if (importedUnit == null) {
                                                var mapping =
                                                        new CdsExportNativeMedicationMapper()
                                                                .map(
                                                                        patient.patient(),
                                                                        row,
                                                                        dates,
                                                                        providers,
                                                                        references.remove(id),
                                                                        indications);
                                                fields = mapping.fields();
                                                content =
                                                        writer ->
                                                                new CdsExportNativeMedicationMapper()
                                                                        .write(mapping, writer);
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
                                                // The importer writes no drugReason: every
                                                // reason here was recorded in CARLOS later.
                                                var added =
                                                        new CdsExportMedicationReasonMapper()
                                                                .map(
                                                                        patient.patient(),
                                                                        row,
                                                                        indications);
                                                var assembly =
                                                        new CdsExportMedicationRetainedAssembler()
                                                                .prepare(
                                                                        patient.patient(),
                                                                        decision,
                                                                        added,
                                                                        index,
                                                                        checked,
                                                                        retained,
                                                                        streams,
                                                                        control::checkpoint);
                                                var plan = assembly.prepared();
                                                var decided =
                                                        new HashMap<>(decision.mapping().fields());
                                                decided.putAll(
                                                        added.fields(
                                                                assembly.problemCodeWritten()));
                                                fields = decided;
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
                                            var reference = referenceRows.get(id);
                                            if (reference != null && importedUnit == null)
                                                assessedRows.add(reference);
                                            assessedRows.addAll(indications);
                                            String provider = row.fields().get("provider_no");
                                            if (provider != null && !provider.isEmpty()) {
                                                // A provider number with no provider record
                                                // has no row to assess: the mapper carried
                                                // the stored number (decision A2).
                                                var resolved = providers.get(provider);
                                                if (resolved != null) assessedRows.add(resolved);
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
                                        emitted[0]++;
                                        counts[0]++;
                                        counts[importedUnit == null ? 2 : 1]++;
                                        counts[3] =
                                                Math.addExact(
                                                        counts[3],
                                                        kept.size() + (long) replaced.size());
                                    });
                    // Every witness and child row this window loaded was used by one of its rows.
                    if (!finished.equals(source.capture())
                            || !precision.isEmpty()
                            || !exact.isEmpty()
                            || !references.isEmpty()
                            || !reasons.isEmpty()
                            || emitted[0] != window.size()) scope();
                    return true;
                            });
                    // Every row of the table was emitted by one window, and every child row was
                    // taken by the window of its owner: a child whose owner is in no window (an
                    // orphan) is taken by none and makes the count fall short.
                    if (counts[0] != tableRows || taken[0] != taken[1]) scope();
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
            Map<String, CdsExportCapturedTextRowDto> providers,
            Map<String, CdsExportCapturedTextRowDto> references,
            Map<String, List<CdsExportCapturedTextRowDto>> reasons,
            long taken,
            long children) {
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
            String table,
            CdsExportSectionWindowPlanner.Window window,
            CdsExportSectionWindowPlanner.Budget budget)
            throws IOException {
        var precision = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        var providers = new HashMap<String, CdsExportCapturedTextRowDto>();
        var references = new HashMap<String, CdsExportCapturedTextRowDto>();
        var reasons = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        // [0] child rows taken by this window, [1] child rows of this category in the capture
        long[] lookup = new long[2];
        var lookupTables = new HashSet<String>();
        lookupTables.add("partial_date");
        if (table.equals("drugs")) {
            lookupTables.add("provider");
            lookupTables.add(CdsExportMedicationReasonMapper.TABLE);
            if (tables.contains("medication_reference")) lookupTables.add("medication_reference");
        }
        var first =
                rows.forEachTable(
                        lease,
                        patient,
                        tables,
                        lookupTables,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        262_144,
                        control,
                        row -> {
                            // A window found too large keeps nothing more; it is read again
                            // with fewer owners.
                            if (budget.exceeded()) return;
                            if (row.table().equals("medication_reference")) {
                                String id = row.fields().get("drugid");
                                if (id == null) scope();
                                lookup[1]++;
                                if (!window.contains(id) || !held(budget, row)) return;
                                if (references.putIfAbsent(id, row) != null) scope();
                                lookup[0]++;
                                return;
                            }
                            // Grouped by their prescription; one with no exported prescription
                            // is refused after the occurrences (a reason is never left behind).
                            if (row.table().equals(CdsExportMedicationReasonMapper.TABLE)) {
                                String drug = row.fields().get("drugId");
                                if (drug == null) scope();
                                lookup[1]++;
                                if (!window.contains(drug) || !held(budget, row)) return;
                                reasons.computeIfAbsent(drug, ignored -> new ArrayList<>())
                                        .add(row);
                                lookup[0]++;
                                return;
                            }
                            if (row.table().equals("provider")) {
                                // Providers are the clinic's, not the chart's rows: every
                                // window holds the ones this chart names, so a smaller
                                // window would hold as many and they are not counted.
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
                            lookup[1]++;
                            if (!window.contains(parent) || !held(budget, row)) return;
                            precision
                                    .computeIfAbsent(parent, ignored -> new ArrayList<>())
                                    .add(row);
                            lookup[0]++;
                        });
        if (budget.exceeded()) return null;
        if (!first.equals(source.capture())) scope();
        return new Lookups(precision, providers, references, reasons, lookup[0], lookup[1]);
    }

    /** Counts one lookup row a window keeps: its values and 64 bytes for each. */
    private static boolean held(
            CdsExportSectionWindowPlanner.Budget budget, CdsExportCapturedTextRowDto row) {
        long size = 0;
        for (var value : row.fields().values())
            if (value != null) size += value.getBytes(StandardCharsets.UTF_8).length + 64L;
        return budget.add(1, size);
    }

    private Map<String, CdsExportNativeRowReceiptDto> scanWitnesses(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            Bounds bounds,
            CdsExportRunControl control,
            String table,
            CdsExportSectionWindowPlanner.Window window,
            CdsExportSectionWindowPlanner.Budget budget)
            throws IOException {
        var exact = new HashMap<String, CdsExportNativeRowReceiptDto>();
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
                            if (!witness.table().equals(table)
                                    || !window.contains(witness.id())
                                    || !budget.owner(
                                            1024L
                                                    + (witness.state() == null
                                                            ? 0
                                                            : witness.state().columns().size()
                                                                    * 256L))) return;
                            if (exact.putIfAbsent(witness.id(), witness) != null) scope();
                        });
        if (budget.exceeded()) return null;
        if (!witnessScan.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || exact.size() != window.size()) scope();
        return exact;
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

    private static void ambiguous() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
    }
}
