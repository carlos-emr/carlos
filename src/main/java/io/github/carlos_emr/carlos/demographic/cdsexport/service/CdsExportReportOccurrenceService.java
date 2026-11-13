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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsReportNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

/** Original report metadata, both representations and all native bytes must close together before private output succeeds. */
@Service
public class CdsExportReportOccurrenceService implements CdsExportCategoryOccurrenceAssembler {
    private static final Set<String> METADATA = Set.of("document", "ctl_document");
    private static final Set<String> REQUIRED =
            Set.of(
                    "document",
                    "ctl_document",
                    CdsExportDocumentFileSourceReader.TABLE,
                    "cds_import_file",
                    "cds_import_file_reference",
                    CdsExportDocumentRouteSourceReader.TABLE,
                    "document_review",
                    "DocumentExtraReviewer",
                    "provider");
    /** Reviewer captures of the patient's documents, each with its document reference column. */
    private static final Map<String, String> REVIEWERS =
            Map.of(
                    CdsExportDocumentRouteSourceReader.TABLE, "lab_no",
                    "document_review", "document_no",
                    "DocumentExtraReviewer", "documentNo");
    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportStagingFactory stores;
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportDataSummaryOccurrenceService dataSummaries;
    private final CdsExportHospitalReportOccurrenceService hospitalReports;

    /**
     * Without data summaries or HRM reports: a capture that holds data-summary or HRM tables is
     * refused.
     */
    public CdsExportReportOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores) {
        this(values, verification, witnesses, captures, stores, null, null);
    }

    /** Without HRM reports: a capture that holds the HRM tables is refused. */
    public CdsExportReportOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores,
            CdsExportDataSummaryOccurrenceService dataSummaries) {
        this(values, verification, witnesses, captures, stores, dataSummaries, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportReportOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores,
            CdsExportDataSummaryOccurrenceService dataSummaries,
            CdsExportHospitalReportOccurrenceService hospitalReports) {
        this.dataSummaries = dataSummaries;
        this.hospitalReports = hospitalReports;
        this.values = Objects.requireNonNull(values);
        this.verification = Objects.requireNonNull(verification);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.captures = Objects.requireNonNull(captures);
        this.stores = Objects.requireNonNull(stores);
        rows = new CdsExportCapturedTextRowsReader(captures);
    }

    @Override
    public CdsCategory category() {
        return CdsCategory.REPORTS;
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
        // HRM reports and data-summary records, each a native-only occurrence.
        long[] appended = {0};
        if (CdsExportDataSummaryOccurrenceService.captured(requiredTables) && dataSummaries == null
                || CdsExportHospitalReportOccurrenceService.captured(requiredTables)
                        && hospitalReports == null) invalid();
        xml.source(
                emitter -> {
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
                                    output);
                    // HRM reports: after the native and imported documents, in the same Reports
                    // source scope.
                    if (hospitalReports != null)
                        appended[0] =
                                hospitalReports.append(
                                        lease,
                                        patient,
                                        patientKey,
                                        requiredTables,
                                        source,
                                        bounds,
                                        control,
                                        emitter,
                                        output);
                    // Data-summary records (registration point): after the native and imported
                    // documents and the HRM reports, in the same Reports source scope.
                    if (dataSummaries != null)
                        appended[0] +=
                                dataSummaries.append(
                                        lease,
                                        patient,
                                        patientKey,
                                        requiredTables,
                                        source,
                                        bounds,
                                        control,
                                        emitter,
                                        output);
                });
        var documents = Objects.requireNonNull(result[0]);
        return new CdsExportClinicalOccurrenceService.Result(
                documents.sourceManifestSha256(),
                documents.category(),
                Math.addExact(documents.nativeRows(), appended[0]),
                documents.importedRows(),
                Math.addExact(documents.nativeOnlyRows(), appended[0]),
                documents.retainedSourceFields());
    }

    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
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
                || !tables.containsAll(REQUIRED)
                || !tables.equals(source.capture().tables().keySet())) invalid();
        new CdsExportMergeScopeValidator(captures)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var current = new TreeMap<String, CdsExportCapturedTextRowDto>();
        long[] memory = {0};
        var scanned =
                rows.forEachTable(
                        lease,
                        patient,
                        tables,
                        METADATA,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        67_108_864,
                        control,
                        row -> {
                            String id = id(row, patient.patient());
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
        var exact = new HashMap<String, CdsExportNativeRowReceiptDto>();
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
                            if (!METADATA.contains(row.table())) return;
                            if (exact.size() >= bounds.nativeRows()) limit();
                            if (exact.putIfAbsent(key(row.table(), row.id()), row) != null)
                                invalid();
                        });
        if (!proof.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || !current.keySet().equals(exact.keySet())) invalid();
        for (var entry : current.entrySet())
            verify(
                    entry.getValue(),
                    exact.get(entry.getKey()),
                    patient.patient(),
                    control::checkpoint);
        for (String table : METADATA)
            if (current.values().stream().filter(row -> row.table().equals(table)).count()
                    != source.capture().tables().get(table).rows()) invalid();
        var reviewers = reviewers(lease, patient, tables, source, bounds, control, current);
        // Every reviewer and route row is accounted with the document it reviews.
        var reviewed = new HashSet<String>();
        long reviewerRows = reviewers.values().stream().mapToLong(List::size).sum();
        var files =
                new CdsExportCapturedDocumentFiles()
                        .read(captures, lease, patient, tables, source, bounds, control);
        // Only a document whose content is its stored file has one (Plan.DOCUMENT_FILES);
        // the native loop proves the exact correspondence document by document.
        if (files.size() > source.capture().tables().get("document").rows()) invalid();
        for (var file : files.values()) {
            var document = current.get(key("document", Integer.toString(file.document())));
            var link = current.get(key("ctl_document", patient.patient() + "_" + file.document()));
            if (document == null
                    || link == null
                    || !file.filename().equals(document.fields().get("docfilename"))) invalid();
        }
        var store =
                stores.openExisting(
                        lease.runId(),
                        lease.epoch(),
                        lease.document().storage(),
                        control::checkpoint);
        var attachments = new ArrayList<CdsExportStagedFileDto>();
        var captured = new CdsExportCapturedValues(store, source.capture().values());
        boolean complete = false;
        Throwable primary = null;
        long[] totals = {0, 0, 0};
        try {
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
                        if (!index.sourceManifestSha256()
                                .equals(source.manifest().plaintextSha256())) invalid();
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
                        var usedFiles = new HashSet<Integer>();
                        var importedDocuments = new HashSet<String>();
                        for (String address : index.appliedUnits())
                            for (var reference :
                                    index.references().getOrDefault(address, List.of()))
                                if (reference.table().equals("document"))
                                    importedDocuments.add(reference.id());
                        boolean hasNative =
                                current.values().stream()
                                        .anyMatch(
                                                row ->
                                                        row.table().equals("document")
                                                                && !importedDocuments.contains(
                                                                        row.fields()
                                                                                .get(
                                                                                        "document_no")));
                        // Reviews recorded in CARLOS on an imported document are mapped too.
                        // So are an edited imported document's first reviewer and edits.
                        boolean reviewing =
                                hasNative || !reviewers.isEmpty() || !checked.reviewUnits().isEmpty();
                        var providers =
                                reviewing
                                        ? new CdsExportPublicProviderReader(captures)
                                                .read(
                                                        lease, patient, tables, source, bounds,
                                                        control)
                                        : Map.<String, CdsExportCapturedTextRowDto>of();
                        var zone =
                                reviewing
                                        ? new CdsExportClinicalZoneService(captures)
                                                .read(
                                                        lease,
                                                        patient,
                                                        tables,
                                                        source,
                                                        bounds.fields(),
                                                        bounds.bytes(),
                                                        control)
                                                .zone()
                                        : null;
                        for (var unit :
                                index.tables().getOrDefault("cds_import_unit", Map.of()).values()) {
                            if (!category().wireName().equals(unit.fields().get("category")))
                                continue;
                            control.checkpoint();
                            String address =
                                    unit.fields().get("run_id")
                                            + ":"
                                            + unit.fields().get("unit_key");
                            if (!index.appliedUnits().contains(address)) continue;
                            // An unchanged unit, or one whose only change is an edit-page edit of
                            // its document rows (anything else still holds the patient).
                            var editedBaselines =
                                    CdsExportImportedDocumentEditReconciler.editedDocuments(
                                            address, index, checked);
                            var refs = index.references().get(address);
                            var compared = checked.units().get(address);
                            if (refs == null
                                    || compared == null
                                    || !refs.equals(
                                            compared.stream()
                                                    .map(
                                                            CdsExportRetainedReceiptVerificationDto
                                                                            .CheckedReference
                                                                    ::expected)
                                                    .toList())) invalid();
                            var prepared =
                                    new CdsExportRetainedPreparationReader()
                                            .report(index, address)
                                            .value();
                            var sourceValues =
                                    new CdsExportReportRetainedSource()
                                            .read(
                                                    patient.patient(),
                                                    address,
                                                    index,
                                                    checked,
                                                    retained,
                                                    streams,
                                                    bounds.bytes(),
                                                    control);
                            CdsExportReportPublicationEvidence.require(
                                    address,
                                    index,
                                    refs,
                                    sourceValues.variants(),
                                    control::checkpoint);
                            var assessments =
                                    new LinkedHashMap<
                                            CdsExportNativeFieldKeyDto,
                                            CdsExportFieldAssessmentDto>();
                            var nativeRows = new ArrayList<CdsExportCapturedTextRowDto>();
                            var fileAssessments =
                                    new LinkedHashMap<
                                            CdsExportNativeFieldKeyDto,
                                            CdsExportFieldAssessmentDto>();
                            var identity =
                                    new CdsExportIdentityDto(
                                            patientKey,
                                            category(),
                                            CdsImportKeys.hash(
                                                    "carlos-retained-occurrence-v1", address));
                            var changes = new ArrayList<>(sourceValues.changes());
                            var unitReviews = new ArrayList<CdsExportCapturedTextRowDto>();
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
                            var editReviewers = new ArrayList<CdsExportNativeReportMapper.Reviewed>();
                            var editContexts = new ArrayList<Map<String, Object>>();
                            var editFields =
                                    new LinkedHashMap<
                                            CdsExportNativeFieldKeyDto,
                                            CdsExportFieldAssessmentDto>();
                            var editedElements = new HashMap<String, String>();
                            for (var variant : sourceValues.variants().values()) {
                                String expectedFilename =
                                        "cds-"
                                                + address.substring(37)
                                                + "-"
                                                + variant.name()
                                                + "-"
                                                + variant.digest()
                                                + "."
                                                + variant.extension();
                                var matches =
                                        refs.stream()
                                                .filter(ref -> ref.table().equals("document"))
                                                .map(ref -> current.get(key(ref.table(), ref.id())))
                                                .filter(Objects::nonNull)
                                                .filter(
                                                        row ->
                                                                expectedFilename.equals(
                                                                        row.fields()
                                                                                .get(
                                                                                        "docfilename")))
                                                .toList();
                                // A file replaced on the document edit page: the row the import
                                // wrote this representation to, as its sealed baseline proves.
                                boolean replaced = false;
                                if (matches.isEmpty()) {
                                    matches =
                                            refs.stream()
                                                    .filter(ref -> ref.table().equals("document"))
                                                    .filter(
                                                            ref ->
                                                                    editedBaselines.containsKey(ref.id())
                                                                            && CdsExportImportedDocumentEditReconciler
                                                                                    .wrote(
                                                                                            editedBaselines
                                                                                                    .get(
                                                                                                            ref
                                                                                                                    .id()),
                                                                                            "docfilename",
                                                                                            expectedFilename))
                                                    .map(ref -> current.get(key(ref.table(), ref.id())))
                                                    .filter(Objects::nonNull)
                                                    .toList();
                                    replaced = matches.size() == 1;
                                }
                                if (matches.size() != 1) review();
                                var document = matches.getFirst();
                                int documentId =
                                        Integer.parseInt(document.fields().get("document_no"));
                                var file = files.get(documentId);
                                var link =
                                        current.get(
                                                key(
                                                        "ctl_document",
                                                        patient.patient() + "_" + documentId));
                                if (file == null
                                        || link == null
                                        || !usedFiles.add(documentId)
                                        || (replaced
                                                ? file.content().bytes() < 1
                                                : file.content().bytes() != variant.length()
                                                        || !file.content()
                                                                .sha256()
                                                                .equals(variant.digest())))
                                    review();
                                var baseline =
                                        editedBaselines.get(document.fields().get("document_no"));
                                for (var row : List.of(document, link)) {
                                    String nativeKey = key(row.table(), id(row, patient.patient()));
                                    var ref =
                                            refs.stream()
                                                    .filter(
                                                            reference ->
                                                                    key(
                                                                                    reference
                                                                                            .table(),
                                                                                    reference.id())
                                                                            .equals(nativeKey))
                                                    .toList();
                                    // An edited document is the row the verified receipt saw.
                                    String expectedDigest =
                                            row == document && baseline != null
                                                    ? currentDigest(compared, ref)
                                                    : ref.size() == 1 ? ref.getFirst().digest() : null;
                                    if (ref.size() != 1
                                            || !owned.add(nativeKey)
                                            || !Objects.equals(
                                                    expectedDigest, exact.get(nativeKey).digest()))
                                        review();
                                    nativeRows.add(row);
                                }
                                var currentState =
                                        exact.get(key("document", Integer.toString(documentId)))
                                                .state();
                                // An edit is decided column by column, from the full row state.
                                if (baseline != null && currentState == null) review();
                                Set<String> editedColumns =
                                        baseline == null
                                                ? Set.of()
                                                : io.github.carlos_emr.carlos.demographic.cds
                                                        .contract.CdsNativeRowComparison.compare(
                                                                baseline, currentState)
                                                        .changedColumns();
                                bind(
                                        document,
                                        link,
                                        prepared,
                                        variant,
                                        expectedFilename,
                                        patient.patient(),
                                        editedColumns);
                                assess(document, link, assessments);
                                if (baseline != null) {
                                    // The user's edit supersedes the imported value.
                                    var edit =
                                            new CdsExportImportedDocumentEditReconciler()
                                                    .reconcile(
                                                            document,
                                                            baseline,
                                                            currentState,
                                                            CdsExportImportedDocumentEditReconciler
                                                                    .imported(
                                                                            prepared,
                                                                            variant,
                                                                            expectedFilename),
                                                            fields,
                                                            streams,
                                                            providers,
                                                            zone);
                                    for (var change : edit.changes()) {
                                        String text = edit.elements().get(change.relativePath());
                                        String prior =
                                                editedElements.putIfAbsent(change.relativePath(), text);
                                        // Two representations edited to different values.
                                        if (prior != null && !prior.equals(text))
                                            throw new CdsExportMappingReviewException(
                                                    "CDS_EXPORT_IMPORTED_REPORT_EDIT_CONFLICT");
                                        if (prior == null) changes.add(change);
                                    }
                                    editReviewers.addAll(edit.reviewers());
                                    var context = new LinkedHashMap<>(edit.context());
                                    if (replaced)
                                        context.put(
                                                "replacedFile",
                                                replacedFile(variant, expectedFilename, document, file));
                                    editContexts.add(context);
                                    editFields.putAll(edit.fields());
                                    var present = new HashSet<String>();
                                    for (var row : nativeRows)
                                        present.add(row.table() + "/" + row.recordKey());
                                    for (var row : edit.rows())
                                        if (present.add(row.table() + "/" + row.recordKey()))
                                            nativeRows.add(row);
                                }
                                // Reviews and inbox routes recorded in CARLOS after the import,
                                // decided below for the whole occurrence.
                                for (var row :
                                        reviewers.getOrDefault(
                                                Integer.toString(documentId), List.of())) {
                                    if (!reviewed.add(key(row.table(), row.recordKey())))
                                        invalid();
                                    unitReviews.add(row);
                                }
                                // A replaced embedded representation beside a referenced one has
                                // no free FilePath: its current bytes become the inline Media.
                                boolean inline =
                                        replaced
                                                && variant.name().equals("embedded")
                                                && sourceValues.variants().containsKey("referenced");
                                if (replaced)
                                    assessFile(
                                            file,
                                            inline ? "Reports/Content" : "Reports/FilePath",
                                            CdsExportImportedDocumentEditReconciler.REPLACED_FILE,
                                            fileAssessments);
                                else assessFile(file, variant.name(), fileAssessments);
                                if (inline) {
                                    byte[] bytes =
                                            currentBytes(
                                                    captured, file, control, patient.patient());
                                    changes.add(inlineReplacement(fields, bytes));
                                    // Media is binary content (a report with a FilePath already is).
                                    if (!"Binary".equals(prepared.values().get("Format")))
                                        changes.add(
                                                new CdsExportRetainedSectionWriter.Change(
                                                        "Format[1]",
                                                        CdsExportImportedDocumentEditReconciler
                                                                .priorIds(fields, "Format[1]"),
                                                        content -> content.text("Binary")));
                                } else if (variant.name().equals("referenced") || replaced) {
                                    if (variant.name().equals("referenced")
                                            && (sourceValues.filePath() == null
                                                    || sourceValues.originalFilePath() == null
                                                    || sourceValues.originalFilePath().isBlank()))
                                        invalid();
                                    var prefix =
                                            new ByteArrayOutputStream(CdsExportReportFileFormats.PREFIX);
                                    var copied =
                                            store.stage(
                                                    "REPORT_ATTACHMENT",
                                                    outputBytes -> {
                                                        try (var input =
                                                                captured.open(
                                                                        file.content(),
                                                                        control::checkpoint,
                                                                        () ->
                                                                                control
                                                                                        .authorizeCategory(
                                                                                                patient
                                                                                                        .patient(),
                                                                                                category(),
                                                                                                false))) {
                                                            transfer(
                                                                    input,
                                                                    outputBytes,
                                                                    control::checkpoint,
                                                                    prefix);
                                                        }
                                                    });
                                    attachments.add(copied);
                                    // The imported bytes, or the replacement the chart now shows.
                                    if (replaced
                                            ? copied.plaintextBytes() != file.content().bytes()
                                                    || !copied.plaintextSha256()
                                                            .equals(file.content().sha256())
                                            : copied.plaintextBytes() != variant.length()
                                                    || !copied.plaintextSha256()
                                                            .equals(variant.digest())) invalid();
                                    // A replacement is declared by what its own bytes are.
                                    String extension =
                                            replaced
                                                    ? CdsExportReportFileFormats.extension(
                                                            document.fields().get("docfilename"),
                                                            document.fields().get("contenttype"),
                                                            prefix.toByteArray())
                                                    : variant.extension();
                                    String relative =
                                            "Reports/"
                                                    + CdsImportKeys.hash(
                                                            "cds-export-report-file-v1",
                                                            patientKey,
                                                            address,
                                                            variant.name())
                                                    + "."
                                                    + extension;
                                    streams.requireActive();
                                    control.authorizeCategory(patient.patient(), category(), false);
                                    output.attachment(
                                            identity,
                                            new CdsExportPackageMemberDto(relative, copied));
                                    changes.addAll(
                                            representationChanges(
                                                    variant.name(),
                                                    replaced,
                                                    sourceValues.variants().size(),
                                                    prepared.values().get("Format"),
                                                    fields,
                                                    sourceValues.filePath(),
                                                    relative,
                                                    extension));
                                }
                            }
                            // The retained report keeps the reviewers the sending EMR recorded.
                            // Each review recorded in CARLOS afterwards (an inbox acknowledgement,
                            // a review record) is one more ReportReviewed appended after them, so
                            // every imported element keeps its exact bytes. The review rows travel
                            // whole, with their exact times and comments, in Notes appended to the
                            // occurrence, or in a supplementary report when the import already
                            // wrote Notes.
                            var supplementary =
                                    importedReviews(
                                            unitReviews,
                                            providers,
                                            zone,
                                            fields,
                                            changes,
                                            assessments,
                                            nativeRows,
                                            new Edits(editReviewers, editContexts, editFields));
                            new CdsExportNativeFieldAssessmentValidator()
                                    .requireRepresentable(
                                            nativeRows,
                                            assessments,
                                            1_000_000,
                                            control::checkpoint);
                            for (var entry : fileAssessments.entrySet())
                                if (assessments.putIfAbsent(entry.getKey(), entry.getValue())
                                        != null) invalid();
                            var content =
                                    new CdsExportRetainedSectionWriter()
                                            .prepareChanges(
                                                    fields, streams, changes, control::checkpoint);
                            var all = new HashSet<>(content.retainedFields());
                            all.addAll(content.replacedFields());
                            if (!all.equals(sourceValues.sourceFields())) invalid();
                            for (String field : all) if (!consumed.add(field)) invalid();
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
                            totals[1] = Math.addExact(totals[1], all.size());
                            if (supplementary != null) {
                                var reviewIdentity =
                                        new CdsExportIdentityDto(
                                                patientKey,
                                                category(),
                                                importedReviewsId(address));
                                streams.requireActive();
                                control.authorizeCategory(patient.patient(), category(), false);
                                xml.emit(
                                        new CdsExportXmlComposer.Section(
                                                reviewIdentity,
                                                body -> writeImportedReviews(supplementary.text(), body)));
                                output.accept(
                                        new CdsExportClinicalOccurrenceService.Receipt(
                                                reviewIdentity,
                                                index.sourceManifestSha256(),
                                                "cds_import_unit",
                                                address.substring(37),
                                                null,
                                                supplementary.fields(),
                                                Set.of(),
                                                Set.of()));
                                control.authorizeCategory(patient.patient(), category(), false);
                                totals[0]++;
                                totals[2]++;
                            }
                        }
                        // Every document row no applied import accounts for is a native chart
                        // document. Each is classified as the chart shows it, and mapped, before
                        // anything is staged: a document that holds the patient costs no copy.
                        var documentRows = new TreeMap<Integer, CdsExportCapturedTextRowDto>();
                        for (var row : current.values())
                            if (row.table().equals("document"))
                                documentRows.put(
                                        Integer.parseInt(row.fields().get("document_no")), row);
                        var natives = new ArrayList<NativeDocument>();
                        // A document shown only inside a program is written when a fresh check
                        // says every exporting identity works in it; each program so granted is
                        // checked again after the documents are written.
                        var granted = new java.util.TreeSet<String>();
                        java.util.function.Predicate<String> readable =
                                program -> {
                                    boolean allowed =
                                            control.restrictedProgramReadable(
                                                    patient.patient(),
                                                    CdsCategory.REPORTS,
                                                    program);
                                    if (allowed) granted.add(program);
                                    return allowed;
                                };
                        for (var entry : documentRows.entrySet()) {
                            control.checkpoint();
                            if (usedFiles.contains(entry.getKey())) continue;
                            var document = entry.getValue();
                            String documentId = Integer.toString(entry.getKey());
                            if (importedDocuments.contains(documentId)) review();
                            String linkKey =
                                    key("ctl_document", patient.patient() + "_" + documentId);
                            var link = current.get(linkKey);
                            if (link == null
                                    || !owned.add(key("document", documentId))
                                    || !owned.add(linkKey)) invalid();
                            var file = files.get(entry.getKey());
                            var documentReviewers = reviewers.getOrDefault(documentId, List.of());
                            for (var row : documentReviewers)
                                if (!reviewed.add(key(row.table(), row.recordKey()))) invalid();
                            var representation =
                                    CdsExportDocumentRepresentation.of(
                                            document.fields(), link.fields());
                            // The capture read a file exactly for a document whose content is
                            // its stored file (Plan.DOCUMENT_FILES applies the same rule).
                            if ((representation == CdsExportDocumentRepresentation.FILE)
                                    != (file != null)) invalid();
                            if (representation == CdsExportDocumentRepresentation.REMOVED) {
                                // Soft-deleted, or its patient link deleted: the chart no longer
                                // shows it, so it is not exported, and every captured field is
                                // still accounted for explicitly.
                                // Its reviewers and routes go with it, under the same rule.
                                var removedRows = new ArrayList<>(List.of(document, link));
                                removedRows.addAll(documentReviewers);
                                var removedFields =
                                        new LinkedHashMap<
                                                CdsExportNativeFieldKeyDto,
                                                CdsExportFieldAssessmentDto>(
                                                CdsExportDocumentRepresentation.removedDispositions(
                                                        document, link));
                                removedFields.putAll(
                                        CdsExportInboxReviewMapper.removed(
                                                documentReviewers,
                                                CdsExportDocumentRepresentation.REMOVED_RULE));
                                new CdsExportNativeFieldAssessmentValidator()
                                        .requireRepresentable(
                                                removedRows,
                                                removedFields,
                                                1_000_000,
                                                control::checkpoint);
                                continue;
                            }
                            if (representation == CdsExportDocumentRepresentation.UNREPRESENTED)
                                throw new CdsExportMappingReviewException(
                                        "CDS_EXPORT_NATIVE_REPORT_STATUS_REQUIRES_REVIEW");
                            if (file != null && file.content().bytes() < 1)
                                throw new CdsExportMappingReviewException(
                                        "CDS_EXPORT_NATIVE_REPORT_FILE_EMPTY");
                            // A file's declaration needs its leading bytes, read while it is
                            // staged; this first mapping only surfaces any hold before staging.
                            var content =
                                    file == null
                                            ? CdsExportNativeReportMapper.Content.html()
                                            : nativeFile(
                                                    patientKey,
                                                    documentId,
                                                    CdsExportReportFileFormats.OPAQUE);
                            var reviews =
                                    CdsExportInboxReviewMapper.report()
                                            .map(
                                                    documentReviewers.stream()
                                                            .filter(
                                                                    row ->
                                                                            row.table()
                                                                                    .equals(
                                                                                            CdsExportDocumentRouteSourceReader
                                                                                                    .TABLE))
                                                            .toList(),
                                                    documentReviewers.stream()
                                                            .filter(
                                                                    row ->
                                                                            !row.table()
                                                                                    .equals(
                                                                                            CdsExportDocumentRouteSourceReader
                                                                                                    .TABLE))
                                                            .toList(),
                                                    providers,
                                                    zone);
                            var mapped =
                                    new CdsExportNativeReportMapper()
                                            .map(
                                                    patient.patient(),
                                                    document,
                                                    link,
                                                    content,
                                                    providers,
                                                    zone,
                                                    reviews,
                                                    readable);
                            new CdsExportNativeFieldAssessmentValidator()
                                    .requireRepresentable(
                                            mapped.rows(),
                                            mapped.fields(),
                                            1_000_000,
                                            control::checkpoint);
                            natives.add(
                                    new NativeDocument(
                                            documentId, document, link, file, reviews, mapped));
                        }
                        for (var report : natives) {
                            control.checkpoint();
                            var file = report.file();
                            var mapped = report.mapped();
                            CdsExportStagedFileDto copied = null;
                            if (file != null) {
                                control.authorizeCategory(patient.patient(), category(), false);
                                var prefix = new ByteArrayOutputStream(CdsExportReportFileFormats.PREFIX);
                                copied =
                                        store.stage(
                                                "REPORT_ATTACHMENT",
                                                outputBytes -> {
                                                    try (var input =
                                                            captured.open(
                                                                    file.content(),
                                                                    control::checkpoint,
                                                                    () ->
                                                                            control
                                                                                    .authorizeCategory(
                                                                                            patient
                                                                                                    .patient(),
                                                                                            category(),
                                                                                            false))) {
                                                        transfer(
                                                                input,
                                                                outputBytes,
                                                                control::checkpoint,
                                                                prefix);
                                                    }
                                                });
                                attachments.add(copied);
                                if (copied.plaintextBytes() != file.content().bytes()
                                        || !copied.plaintextSha256()
                                                .equals(file.content().sha256())) invalid();
                                // The authenticated copy's own leading bytes decide what the
                                // file is declared as; the stored name and type stay in Notes.
                                mapped =
                                        new CdsExportNativeReportMapper()
                                                .map(
                                                        patient.patient(),
                                                        report.document(),
                                                        report.link(),
                                                        nativeFile(
                                                                patientKey,
                                                                report.id(),
                                                                CdsExportReportFileFormats.extension(
                                                                        file.filename(),
                                                                        report.document()
                                                                                .fields()
                                                                                .get("contenttype"),
                                                                        prefix.toByteArray())),
                                                        providers,
                                                        zone,
                                                        report.reviews(),
                                                        readable);
                                new CdsExportNativeFieldAssessmentValidator()
                                        .requireRepresentable(
                                                mapped.rows(),
                                                mapped.fields(),
                                                1_000_000,
                                                control::checkpoint);
                            }
                            var section = mapped;
                            var identity =
                                    new CdsExportIdentityDto(
                                            patientKey,
                                            category(),
                                            CdsImportKeys.hash(
                                                    "carlos-native-occurrence-v1",
                                                    "document",
                                                    report.id()));
                            var assessments = new LinkedHashMap<>(section.fields());
                            if (file != null) assessFile(file, "referenced", assessments);
                            streams.requireActive();
                            control.authorizeCategory(patient.patient(), category(), false);
                            if (copied != null)
                                output.attachment(
                                        identity,
                                        new CdsExportPackageMemberDto(
                                                section.text().get("FilePath"), copied));
                            xml.emit(
                                    new CdsExportXmlComposer.Section(
                                            identity,
                                            body ->
                                                    new CdsExportNativeReportMapper()
                                                            .write(section, body)));
                            streams.requireActive();
                            control.authorizeCategory(patient.patient(), category(), false);
                            output.accept(
                                    new CdsExportClinicalOccurrenceService.Receipt(
                                            identity,
                                            index.sourceManifestSha256(),
                                            "document",
                                            report.id(),
                                            null,
                                            assessments,
                                            Set.of(),
                                            Set.of()));
                            if (file != null && !usedFiles.add(file.document())) invalid();
                            totals[0]++;
                            totals[2]++;
                        }
                        // Embedded documents of native HL7 laboratory reports, after the chart's
                        // own documents.
                        long laboratoryDocuments =
                                laboratoryDocuments(
                                        lease,
                                        patient,
                                        patientKey,
                                        tables,
                                        source,
                                        index,
                                        bounds,
                                        control,
                                        store,
                                        attachments,
                                        xml,
                                        output);
                        totals[0] = Math.addExact(totals[0], laboratoryDocuments);
                        totals[2] = Math.addExact(totals[2], laboratoryDocuments);
                        for (String program : granted)
                            if (!control.restrictedProgramReadable(
                                    patient.patient(), CdsCategory.REPORTS, program))
                                throw new SecurityException("CDS_EXPORT_ACCESS_CHANGED");
                        // Re-authenticates the whole captured values member before any staged
                        // attachment can be accepted with this patient's output.
                        captured.close();
                        var expectedSource = new HashSet<String>();
                        for (var field : retained)
                            if (field.category() == category()) expectedSource.add(field.id());
                        if (!owned.equals(current.keySet())
                                || !usedFiles.equals(files.keySet())
                                || reviewed.size() != reviewerRows) review();
                        if (!consumed.equals(expectedSource)) invalid();
                        streams.requireActive();
                        captures.authorizeSources(lease, patient, tables, control);
                        patient.require(lease);
                        control.authorizeCategory(patient.patient(), category(), false);
                        control.checkpoint();
                    });
            complete = true;
            return new CdsExportClinicalOccurrenceService.Result(
                    source.manifest().plaintextSha256(),
                    category(),
                    totals[0],
                    totals[0] - totals[2],
                    totals[2],
                    totals[1]);
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (!complete) {
                try {
                    captured.close();
                } catch (IOException | RuntimeException cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
                for (var attachment : attachments.reversed())
                    try {
                        store.discardUnpublished(attachment);
                    } catch (IOException cleanup) {
                        if (primary != null) primary.addSuppressed(cleanup);
                        else throw cleanup;
                    }
            }
        }
    }

    /** The supplementary record of the reviews of an imported report: its text and fields. */
    record ImportedReviews(
            String text, Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        ImportedReviews {
            fields = Map.copyOf(fields);
        }

        @Override
        public String toString() {
            return "ImportedReviews[protected]";
        }
    }

    /** The Reports identity of the supplementary review record of the imported unit at {@code address}. */
    static String importedReviewsId(String address) {
        return CdsImportKeys.hash("carlos-native-occurrence-v1", "imported_report_reviews", address);
    }

    /** Whether the retained fields of an imported report include its own Notes. */
    static boolean importedNotes(List<CdsExportRetainedFieldDto> fields) throws IOException {
        return CdsExportInboxReviewMapper.nextOrdinal(fields, CdsCategory.REPORTS, "Notes") > 1;
    }

    /**
     * Decides the reviews recorded in CARLOS on the documents of one imported report: appends a
     * ReportReviewed change for each dated review and puts the rows that travel whole in Notes
     * appended to the occurrence, or returns them as a supplementary record when the imported
     * report already has Notes.
     *
     * @return the supplementary record, or null when none is needed
     */
    static ImportedReviews importedReviews(
            List<CdsExportCapturedTextRowDto> rows,
            Map<String, CdsExportCapturedTextRowDto> providers,
            java.time.ZoneId zone,
            List<CdsExportRetainedFieldDto> fields,
            List<CdsExportRetainedSectionWriter.Change> changes,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments,
            List<CdsExportCapturedTextRowDto> nativeRows)
            throws IOException {
        return importedReviews(
                rows, providers, zone, fields, changes, assessments, nativeRows, Edits.NONE);
    }

    /**
     * What CARLOS edits of an imported report's document rows add to its reviews: the first
     * reviewers stored on the rows, each edited row's edit context and the decisions for its
     * changed columns ({@link CdsExportImportedDocumentEditReconciler}).
     */
    record Edits(
            List<CdsExportNativeReportMapper.Reviewed> reviewers,
            List<Map<String, Object>> contexts,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        static final Edits NONE = new Edits(List.of(), List.of(), Map.of());

        Edits {
            reviewers = List.copyOf(reviewers);
            contexts = List.copyOf(contexts);
            fields = Map.copyOf(fields);
        }

        @Override
        public String toString() {
            return "Edits[protected]";
        }
    }

    /** As above, with the reviewers and edit contexts of edited document rows. */
    static ImportedReviews importedReviews(
            List<CdsExportCapturedTextRowDto> rows,
            Map<String, CdsExportCapturedTextRowDto> providers,
            java.time.ZoneId zone,
            List<CdsExportRetainedFieldDto> fields,
            List<CdsExportRetainedSectionWriter.Change> changes,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments,
            List<CdsExportCapturedTextRowDto> nativeRows,
            Edits edits)
            throws IOException {
        if (rows.isEmpty() && edits.contexts().isEmpty()) return null;
        var reviews =
                rows.isEmpty()
                        ? CdsExportInboxReviewMapper.Reviews.NONE
                        : CdsExportInboxReviewMapper.report()
                                .map(
                                        rows.stream()
                                                .filter(
                                                        row ->
                                                                row.table()
                                                                        .equals(
                                                                                CdsExportDocumentRouteSourceReader
                                                                                        .TABLE))
                                                .toList(),
                                        rows.stream()
                                                .filter(
                                                        row ->
                                                                !row.table()
                                                                        .equals(
                                                                                CdsExportDocumentRouteSourceReader
                                                                                        .TABLE))
                                                .toList(),
                                        providers,
                                        zone);
        var present = new HashSet<String>();
        for (var row : nativeRows) present.add(row.table() + "/" + row.recordKey());
        for (var row : reviews.rows())
            if (present.add(row.table() + "/" + row.recordKey())) nativeRows.add(row);
        long next = CdsExportInboxReviewMapper.nextOrdinal(fields, CdsCategory.REPORTS, "ReportReviewed");
        // The first review stored on a document row comes first, then every later review.
        var appended = new ArrayList<>(edits.reviewers());
        for (var review : reviews.reviewers())
            appended.add(
                    new CdsExportNativeReportMapper.Reviewed(
                            review.provider(),
                            new CdsExportDateDto(
                                    CdsExportDateDto.Precision.DATE,
                                    review.reviewed().lexical().substring(0, 10))));
        var written = new HashSet<String>();
        for (var reviewed : appended) {
            // The element holds a date: the same provider on the same day is written once.
            if (!written.add(
                    reviewed.provider().firstName()
                            + "\u0000"
                            + reviewed.provider().lastName()
                            + "\u0000"
                            + reviewed.provider().ohipNumber()
                            + "\u0000"
                            + reviewed.date().lexical())) continue;
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            "ReportReviewed[" + next++ + "]",
                            Set.of(),
                            body -> CdsExportNativeReportMapper.reviewedContent(body, reviewed)));
        }
        var decided = new LinkedHashMap<>(reviews.fields());
        decided.putAll(edits.fields());
        if (reviews.carried().isEmpty() && edits.contexts().isEmpty()) {
            assessments.putAll(decided);
            return null;
        }
        var details = CdsExportInboxReviewMapper.carriedContext(reviews);
        if (!edits.contexts().isEmpty()) details.put("edits", edits.contexts());
        String context =
                com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .build()
                        .writeValueAsString(details);
        String what = edits.contexts().isEmpty() ? "reviews" : "reviews and edits";
        if (!importedNotes(fields)) {
            String notes = "[CARLOS " + what + " recorded after the import]\n" + context;
            if (!CdsExportNativeFieldAssessments.legal(notes, 32000))
                throw new CdsExportMappingReviewException(
                        "CDS_EXPORT_IMPORTED_REPORT_REVIEW_NOTES_CAPACITY");
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            "Notes[1]", Set.of(), body -> body.text(notes)));
            assessments.putAll(decided);
            return null;
        }
        var relocated =
                CdsExportInboxReviewMapper.relocated(decided, "Reports/Notes", "Reports/Content");
        assessments.putAll(relocated);
        String text =
                "CARLOS data summary: "
                        + what
                        + " recorded in CARLOS on a report from an earlier CDS import. The report"
                        + " itself is exported from its imported elements.\n"
                        + context;
        if (!CdsExportNativeFieldAssessments.legal(text, Integer.MAX_VALUE))
            throw new CdsExportMappingReviewException(
                    "CDS_EXPORT_IMPORTED_REPORT_REVIEW_TEXT_REQUIRES_REVIEW");
        // Every field of every carried row and named provider, in the supplementary record.
        return new ImportedReviews(text, relocated);
    }

    /** The supplementary text report of the reviews of an imported report. */
    private static void writeImportedReviews(String text, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "Format", "Text");
        xml.element("cds", "FileExtensionAndVersion", "txt");
        xml.start("cds", "Content");
        xml.element("cds_dt", "TextContent", text);
        xml.end();
        xml.element("cds", "Class", "Medical Records Report");
        xml.element("cds", "SubClass", "CARLOS reviews of an imported report");
        xml.element(
                "cds",
                "Notes",
                "CARLOS data summary: the report these reviews belong to is the imported report"
                        + " exported beside this one.");
    }

    /**
     * Emits one Reports entry for every embedded document (OBX-2 {@code ED}) of the patient's
     * native HL7 laboratory reports ({@link CdsExportLaboratoryDocumentMapper}), with its decoded
     * bytes staged as the report's attachment. Each report's inbox acknowledgements are its
     * reviews. Nothing is read when LaboratoryResults was not selected: the reports are not
     * captured then.
     *
     * @return the number of entries emitted
     */
    private long laboratoryDocuments(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportRetainedIndexDto index,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportStagingService store,
            List<CdsExportStagedFileDto> attachments,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        if (!lease.document().command().categories().contains(CdsCategory.LABORATORY_RESULTS)
                || !tables.contains("hl7TextMessage")) return 0;
        var laboratories =
                new CdsExportLaboratoryOccurrenceService(values, verification, witnesses, captures);
        var sources =
                laboratories.nativeSources(lease, patient, tables, source, index, bounds, control)
                        .stream()
                        .filter(laboratory -> !laboratory.parsed().documents().isEmpty())
                        .toList();
        if (sources.isEmpty()) return 0;
        var routes = laboratories.routes(lease, patient, tables, source, bounds, control);
        var providers =
                new CdsExportPublicProviderReader(captures)
                        .read(lease, patient, tables, source, bounds, control);
        var zone =
                new CdsExportClinicalZoneService(captures)
                        .read(lease, patient, tables, source, bounds.fields(), bounds.bytes(), control)
                        .zone();
        var mapper = new CdsExportLaboratoryDocumentMapper();
        long emitted = 0;
        for (var laboratory : sources) {
            var reviews =
                    CdsExportInboxReviewMapper.report()
                            .map(routes.getOrDefault(laboratory.id(), List.of()), List.of(), providers, zone);
            var assessments = CdsExportNativeLaboratoryMapper.assessments(laboratory, reviews);
            var rows = new ArrayList<>(laboratory.rows());
            rows.addAll(reviews.rows());
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(rows, assessments, 1_000_000, control::checkpoint);
            for (int i = 0; i < laboratory.parsed().documents().size(); i++) {
                control.checkpoint();
                String id = laboratory.id() + ":" + laboratory.parsed().documents().get(i).ordinal();
                var payload = CdsExportLaboratoryDocumentMapper.payload(laboratory, i);
                byte[] bytes = payload.bytes();
                String extension =
                        bytes == null
                                ? null
                                : CdsExportReportFileFormats.extension(
                                        null, null, CdsExportLaboratoryDocumentMapper.prefix(bytes));
                String path =
                        bytes == null
                                ? null
                                : "Reports/"
                                        + CdsImportKeys.hash(
                                                "cds-export-native-lab-document-file-v1",
                                                patientKey,
                                                id)
                                        + "."
                                        + extension;
                // Mapped before anything is staged: a document that holds the patient costs no copy.
                var result = mapper.map(laboratory, i, payload, path, extension, zone, reviews);
                var identity =
                        new CdsExportIdentityDto(
                                patientKey,
                                category(),
                                CdsImportKeys.hash("carlos-native-occurrence-v1", "hl7TextMessage", id));
                control.authorizeCategory(patient.patient(), CdsCategory.LABORATORY_RESULTS, true);
                control.authorizeCategory(patient.patient(), category(), false);
                if (bytes != null) {
                    var copied = store.stage("REPORT_ATTACHMENT", target -> target.write(bytes));
                    attachments.add(copied);
                    if (copied.plaintextBytes() != bytes.length
                            || !copied.plaintextSha256().equals(sha256(bytes))) invalid();
                    output.attachment(identity, new CdsExportPackageMemberDto(path, copied));
                }
                xml.emit(new CdsExportXmlComposer.Section(identity, body -> mapper.write(result, body)));
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
                control.authorizeCategory(patient.patient(), CdsCategory.LABORATORY_RESULTS, true);
                control.authorizeCategory(patient.patient(), category(), false);
                emitted++;
            }
        }
        return emitted;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** A mapped native document and, when its content is a stored file, that captured file. */
    private record NativeDocument(
            String id,
            CdsExportCapturedTextRowDto document,
            CdsExportCapturedTextRowDto link,
            CdsExportCapturedDocumentFiles.File file,
            CdsExportInboxReviewMapper.Reviews reviews,
            CdsExportNativeReportMapper.Result mapped) {
        @Override
        public String toString() {
            return "NativeDocument[protected]";
        }
    }

    /**
     * The reviewer and inbox route rows of the patient's documents, by document number, from the
     * authenticated capture (Plan.DOCUMENT_ROUTES, DOCUMENT_REVIEWS, DOCUMENT_EXTRA_REVIEWERS). Read
     * like the public provider lookup: they have no historical receipt, and each one is accounted
     * with the captured document it reviews.
     */
    private Map<String, List<CdsExportCapturedTextRowDto>> reviewers(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            Map<String, CdsExportCapturedTextRowDto> current)
            throws IOException {
        long expected = 0;
        for (String table : REVIEWERS.keySet()) {
            var scope = source.capture().tables().get(table);
            if (scope == null || !scope.columns().equals(reviewerColumns(table))) invalid();
            expected = Math.addExact(expected, scope.rows());
        }
        if (expected > bounds.nativeRows()) limit();
        var result = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
        var ids = new HashSet<String>();
        long[] memory = {0};
        var read =
                rows.forEachTable(
                        lease,
                        patient,
                        tables,
                        REVIEWERS.keySet(),
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        67_108_864,
                        control,
                        row -> {
                            control.checkpoint();
                            if (!row.fields().keySet().equals(reviewerColumns(row.table()))
                                    || !row.exactNumbers().isEmpty()
                                    || !ids.add(key(row.table(), number(row, "id"))))
                                invalid();
                            if (!CdsExportPatientTableSourceReader.key(
                                            Long.parseLong(row.fields().get("id")))
                                    .equals(row.recordKey())) invalid();
                            if (row.table().equals(CdsExportDocumentRouteSourceReader.TABLE)
                                    && !"DOC".equals(row.fields().get("lab_type"))) invalid();
                            String document = number(row, REVIEWERS.get(row.table()));
                            if (!current.containsKey(key("document", document))) invalid();
                            for (var value : row.fields().values())
                                if (value != null)
                                    memory[0] =
                                            Math.addExact(
                                                    memory[0],
                                                    value.getBytes(StandardCharsets.UTF_8).length
                                                            + 64L);
                            memory[0] = Math.addExact(memory[0], 1024L);
                            if (memory[0] > bounds.indexBytes()) limit();
                            result.computeIfAbsent(document, ignored -> new ArrayList<>()).add(row);
                        });
        if (!read.equals(source.capture()) || ids.size() != expected) invalid();
        var copy = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
        result.forEach((document, list) -> copy.put(document, List.copyOf(list)));
        return Collections.unmodifiableMap(copy);
    }

    private static Set<String> reviewerColumns(String table) throws IOException {
        return Set.copyOf(
                switch (table) {
                    case CdsExportDocumentRouteSourceReader.TABLE ->
                            CdsExportPatientTableReadSupport.Plan.DOCUMENT_ROUTES.columns();
                    case "document_review" ->
                            CdsExportPatientTableReadSupport.Plan.DOCUMENT_REVIEWS.columns();
                    case "DocumentExtraReviewer" ->
                            CdsExportPatientTableReadSupport.Plan.DOCUMENT_EXTRA_REVIEWERS
                                    .columns();
                    default -> throw new IOException("CDS_EXPORT_REPORT_OCCURRENCE_INVALID");
                });
    }

    /** A positive canonical decimal number stored in one column of a captured row. */
    private static String number(CdsExportCapturedTextRowDto row, String column)
            throws IOException {
        String value = row.fields().get(column);
        try {
            long number = Long.parseLong(value);
            if (number < 1 || !Long.toString(number).equals(value)) invalid();
        } catch (NumberFormatException malformed) {
            invalid();
        }
        return value;
    }

    private static CdsExportNativeReportMapper.Content nativeFile(
            String patientKey, String documentId, String extension) {
        return CdsExportNativeReportMapper.Content.file(
                "Reports/"
                        + CdsImportKeys.hash(
                                "cds-export-native-report-file-v1", patientKey, documentId)
                        + "."
                        + extension,
                extension);
    }

    static void bind(
            CdsExportCapturedTextRowDto document,
            CdsExportCapturedTextRowDto link,
            CdsReportNativeDto prepared,
            CdsReportNativeDto.Variant variant,
            String filename,
            int patient)
            throws IOException {
        bind(document, link, prepared, variant, filename, patient, Set.of());
    }

    /**
     * Binds the document row to the values the import wrote. A column the document edit page
     * changed after the import ({@code edited}, verified against the sealed baseline) is not
     * compared: {@link CdsExportImportedDocumentEditReconciler} decides it.
     */
    static void bind(
            CdsExportCapturedTextRowDto document,
            CdsExportCapturedTextRowDto link,
            CdsReportNativeDto prepared,
            CdsReportNativeDto.Variant variant,
            String filename,
            int patient,
            Set<String> edited)
            throws IOException {
        var row = document.fields();
        var imported = CdsExportImportedDocumentEditReconciler.imported(prepared, variant, filename);
        for (String column :
                List.of(
                        "doctype",
                        "status",
                        "docfilename",
                        "docClass",
                        "docSubClass",
                        "sourceFacility",
                        "source",
                        "contenttype",
                        "cds_content_precision",
                        "docdesc"))
            if (!edited.contains(column) && !Objects.equals(imported.get(column), row.get(column)))
                invalid();
        if (variant.mediaReviewRequired()
                || !edited.contains("abnormal") && !"0".equals(row.get("abnormal"))
                || !edited.contains("public1") && !"0".equals(row.get("public1"))
                || !"0".equals(row.get("restrictToProgram"))
                // The edit page stores "" for the HTML of a file document: never content.
                || !empty(row.get("docxml"))
                || !empty(row.get("report_media"))
                || !edited.contains("responsible") && !empty(row.get("responsible"))
                || !edited.contains("reviewer") && !empty(row.get("reviewer"))
                || !edited.contains("reviewdatetime") && row.get("reviewdatetime") != null
                || row.get("appointment_no") != null && !row.get("appointment_no").equals("0"))
            review();
        if (!"demographic".equals(link.fields().get("module"))
                || !Integer.toString(patient).equals(link.fields().get("module_id"))
                || !row.get("document_no").equals(link.fields().get("document_no"))
                || !"A".equals(link.fields().get("status"))) invalid();
    }

    /** The digest the verified receipt saw for the one reference in {@code ref}. */
    private static String currentDigest(
            List<CdsExportRetainedReceiptVerificationDto.CheckedReference> compared,
            List<CdsExportRetainedIndexDto.Reference> ref) {
        if (ref.size() != 1) return null;
        for (var checked : compared)
            if (checked.expected().equals(ref.getFirst())) return checked.currentDigest();
        return null;
    }

    private static void assess(
            CdsExportCapturedTextRowDto document,
            CdsExportCapturedTextRowDto link,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> output) {
        var fields = new CdsExportNativeFieldAssessments(document, output);
        for (String column : document.fields().keySet()) {
            String destination =
                    switch (column) {
                        case "docClass" -> "Class";
                        case "docSubClass" -> "SubClass";
                        case "source" -> "SourceAuthorPhysician";
                        case "sourceFacility" -> "SourceFacility";
                        case "contenttype" -> "Format";
                        case "contentdatetime", "observationdate", "cds_content_precision" ->
                                "EventDateTime";
                        case "receivedDate" -> "ReceivedDateTime";
                        case "sent_date_time" -> "SentDateTime";
                        default -> null;
                    };
            if (destination == null)
                fields.operational(
                        column,
                        "UNCHANGED_LOCAL_STORAGE_ENTRY_DISPLAY_CACHE_AND_EMPTY_CONTEXT_NEVER_SOURCE_AUTHOR");
            else
                fields.set(
                        column,
                        empty(document.fields().get(column)) ? Outcome.ABSENT : Outcome.MAPPED,
                        "Reports/" + destination,
                        "SEALED_PREPARATION_AND_UNCHANGED_WHOLE_DOCUMENT_WITH_COMPLETE_TYPED_SOURCE");
        }
        var links = new CdsExportNativeFieldAssessments(link, output);
        for (String column : link.fields().keySet())
            links.operational(column, "EXACT_ACTIVE_PATIENT_DOCUMENT_ASSOCIATION");
    }

    /**
     * The retained-report changes that point an imported report at its attached file: the
     * imported FilePath now names the package member; a replaced file (the document edit page's
     * "File") of an embedded-only report takes the Content's place, and with one representation
     * the report's FileExtensionAndVersion and Format follow the replacement. An unreplaced
     * embedded representation needs no change.
     */
    static List<CdsExportRetainedSectionWriter.Change> representationChanges(
            String variant,
            boolean replaced,
            int representations,
            String format,
            List<CdsExportRetainedFieldDto> fields,
            CdsExportRetainedFieldDto filePath,
            String relative,
            String extension)
            throws IOException {
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        if (variant.equals("referenced")) {
            if (filePath == null) invalid();
            String root = CdsExportRetainedOccurrencePath.root(filePath, CdsCategory.REPORTS);
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            filePath.sourcePath().substring(root.length() + 1),
                            Set.of(filePath.id()),
                            content -> content.text(relative)));
        } else if (replaced) {
            // The only representation was embedded: its Content gives way to the attachment.
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            "Content[1]",
                            CdsExportImportedDocumentEditReconciler.priorIds(fields, "Content[1]"),
                            null));
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            "FilePath[1]", Set.of(), content -> content.text(relative)));
        }
        if (replaced && representations == 1) {
            // One representation: the report's declarations follow the replacement.
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            "FileExtensionAndVersion[1]",
                            CdsExportImportedDocumentEditReconciler.priorIds(
                                    fields, "FileExtensionAndVersion[1]"),
                            content -> content.text(extension)));
            if (!"Binary".equals(format))
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(
                                "Format[1]",
                                CdsExportImportedDocumentEditReconciler.priorIds(fields, "Format[1]"),
                                content -> content.text("Binary")));
        }
        return List.copyOf(changes);
    }

    /** The inline Media of a replaced embedded representation beside a referenced one. */
    static CdsExportRetainedSectionWriter.Change inlineReplacement(
            List<CdsExportRetainedFieldDto> fields, byte[] bytes) throws IOException {
        byte[] copy = bytes.clone();
        return new CdsExportRetainedSectionWriter.Change(
                "Content[1]",
                CdsExportImportedDocumentEditReconciler.priorIds(fields, "Content[1]"),
                content -> {
                    content.start("cds_dt", "Media");
                    content.binary(new ByteArrayInputStream(copy));
                    content.end();
                });
    }

    /**
     * The imported file's identity, sealed in the receipt baseline and the import's preparation,
     * beside the replacement the chart now shows.
     */
    static Map<String, Object> replacedFile(
            CdsReportNativeDto.Variant variant,
            String importedName,
            CdsExportCapturedTextRowDto document,
            CdsExportCapturedDocumentFiles.File file) {
        var imported = new LinkedHashMap<String, Object>();
        imported.put("docfilename", importedName);
        imported.put("contenttype", variant.mime());
        imported.put("sha256", variant.digest());
        imported.put("bytes", variant.length());
        var current = new LinkedHashMap<String, Object>();
        current.put("docfilename", document.fields().get("docfilename"));
        current.put("contenttype", document.fields().get("contenttype"));
        current.put("sha256", file.content().sha256());
        current.put("bytes", file.content().bytes());
        var result = new LinkedHashMap<String, Object>();
        result.put("rule", CdsExportImportedDocumentEditReconciler.REPLACED_FILE);
        result.put("representation", variant.name());
        result.put("imported", imported);
        result.put("current", current);
        return result;
    }

    /** The replacement bytes of an inline representation, read once and bounded. */
    private static byte[] currentBytes(
            CdsExportCapturedValues captured,
            CdsExportCapturedDocumentFiles.File file,
            CdsExportRunControl control,
            int patient)
            throws IOException {
        if (file.content().bytes() > INLINE_CONTENT_BYTES)
            throw new CdsExportMappingReviewException(
                    "CDS_EXPORT_IMPORTED_REPORT_REPLACED_CONTENT_CAPACITY");
        var buffer = new ByteArrayOutputStream();
        try (var input =
                captured.open(
                        file.content(),
                        control::checkpoint,
                        () -> control.authorizeCategory(patient, CdsCategory.REPORTS, false))) {
            transfer(input, buffer, control::checkpoint);
        }
        byte[] bytes = buffer.toByteArray();
        if (bytes.length != file.content().bytes()) invalid();
        return bytes;
    }

    /** The largest replacement written inline as Media (32 MiB, as the composer bounds it). */
    private static final long INLINE_CONTENT_BYTES = 32L << 20;

    /** One captured file's fields, under {@code rule}, exported at {@code destination}. */
    private static void assessFile(
            CdsExportCapturedDocumentFiles.File file,
            String destination,
            String rule,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> output)
            throws IOException {
        String key = file.content().recordKey();
        for (String column : Set.of("document_no", "filename", "content")) {
            var assessment =
                    new CdsExportFieldAssessmentDto(
                            column.equals("content") && file.content().bytes() == 0
                                    ? Presence.EMPTY
                                    : Presence.VALUE,
                            column.equals("content") ? Outcome.MAPPED : Outcome.OPERATIONAL,
                            column.equals("content") ? destination : "",
                            rule);
            if (output.putIfAbsent(
                            new CdsExportNativeFieldKeyDto(
                                    CdsExportDocumentFileSourceReader.TABLE, key, column),
                            assessment)
                    != null) invalid();
        }
    }

    private static void assessFile(
            CdsExportCapturedDocumentFiles.File file,
            String variant,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> output)
            throws IOException {
        String key = file.content().recordKey();
        for (String column : Set.of("document_no", "filename", "content")) {
            var assessment =
                    new CdsExportFieldAssessmentDto(
                            column.equals("content") && file.content().bytes() == 0
                                    ? Presence.EMPTY
                                    : Presence.VALUE,
                            column.equals("content") ? Outcome.MAPPED : Outcome.OPERATIONAL,
                            column.equals("content")
                                    ? "Reports/"
                                            + (variant.equals("embedded") ? "Content" : "FilePath")
                                    : "",
                            "COMPLETE_AUTHENTICATED_NATIVE_REPRESENTATION_BOUND_TO_ORIGINAL_DIGEST");
            if (output.putIfAbsent(
                            new CdsExportNativeFieldKeyDto(
                                    CdsExportDocumentFileSourceReader.TABLE, key, column),
                            assessment)
                    != null) invalid();
        }
    }

    private static void verify(
            CdsExportCapturedTextRowDto row,
            CdsExportNativeRowReceiptDto witness,
            int patient,
            Runnable checkpoint)
            throws IOException {
        checkpoint.run();
        if (witness == null
                || witness.state() == null
                || witness.partial()
                || !Objects.equals(witness.digest(), witness.state().digest())
                || !row.table().equals(witness.table())
                || !id(row, patient).equals(witness.id())
                || !row.recordKey().equals(witness.recordKey())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        witness.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) invalid();
        for (String column : row.fields().keySet()) {
            checkpoint.run();
            if (!CdsExportCapturedTextWitness.matches(
                    witness.state(), column, row.fields().get(column))) invalid();
        }
    }

    private static String id(CdsExportCapturedTextRowDto row, int patient) throws IOException {
        String id = row.fields().get("document_no");
        try {
            int value = Integer.parseInt(id);
            if (value < 1
                    || !Integer.toString(value).equals(id)
                    || !CdsExportPatientTableSourceReader.key(value).equals(row.recordKey()))
                invalid();
        } catch (NumberFormatException malformed) {
            throw new IOException("CDS_EXPORT_REPORT_OCCURRENCE_INVALID");
        }
        if (row.table().equals("ctl_document")) {
            if (!"demographic".equals(row.fields().get("module"))
                    || !Integer.toString(patient).equals(row.fields().get("module_id"))) invalid();
            return patient + "_" + id;
        }
        if (!row.table().equals("document")) invalid();
        return id;
    }

    private static Set<String> columns(String table) throws IOException {
        return Set.copyOf(
                switch (table) {
                    case "document" -> CdsExportPatientTableReadSupport.Plan.DOCUMENTS.columns();
                    case "ctl_document" ->
                            CdsExportPatientTableReadSupport.Plan.DOCUMENT_LINKS.columns();
                    default -> throw new IOException("CDS_EXPORT_REPORT_OCCURRENCE_INVALID");
                });
    }

    private static void transfer(InputStream input, OutputStream output, Runnable checkpoint)
            throws IOException {
        transfer(input, output, checkpoint, null);
    }

    /** Copies every byte; when {@code prefix} is given it also keeps the leading bytes. */
    private static void transfer(
            InputStream input,
            OutputStream output,
            Runnable checkpoint,
            ByteArrayOutputStream prefix)
            throws IOException {
        byte[] buffer = new byte[65536];
        try {
            for (int n; (n = input.read(buffer)) != -1; ) {
                checkpoint.run();
                if (n == 0) invalid();
                if (prefix != null && prefix.size() < CdsExportReportFileFormats.PREFIX)
                    prefix.write(
                            buffer,
                            0,
                            Math.min(n, CdsExportReportFileFormats.PREFIX - prefix.size()));
                output.write(buffer, 0, n);
            }
        } finally {
            Arrays.fill(buffer, (byte) 0);
        }
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }

    private static String key(String table, String id) {
        return table + ":" + id;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_REPORT_OCCURRENCE_INVALID");
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }
}
