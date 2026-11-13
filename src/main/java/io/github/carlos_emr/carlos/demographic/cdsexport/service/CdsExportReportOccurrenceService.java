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
                    "cds_import_file_reference");
    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportStagingFactory stores;
    private final CdsExportCapturedTextRowsReader rows;

    public CdsExportReportOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores) {
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
        var captured = new CapturedValues(store, source.capture().values());
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
                        var providers =
                                hasNative
                                        ? new CdsExportPublicProviderReader(captures)
                                                .read(
                                                        lease, patient, tables, source, bounds,
                                                        control)
                                        : Map.<String, CdsExportCapturedTextRowDto>of();
                        var zone =
                                hasNative
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
                            if (!checked.unchangedUnits().contains(address)) review();
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
                                        || file.content().bytes() != variant.length()
                                        || !file.content().sha256().equals(variant.digest()))
                                    review();
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
                                    if (ref.size() != 1
                                            || !owned.add(nativeKey)
                                            || !ref.getFirst()
                                                    .digest()
                                                    .equals(exact.get(nativeKey).digest()))
                                        review();
                                    nativeRows.add(row);
                                }
                                bind(
                                        document,
                                        link,
                                        prepared,
                                        variant,
                                        expectedFilename,
                                        patient.patient());
                                assess(document, link, assessments);
                                assessFile(file, variant.name(), fileAssessments);
                                if (variant.name().equals("referenced")) {
                                    if (sourceValues.filePath() == null
                                            || sourceValues.originalFilePath() == null
                                            || sourceValues.originalFilePath().isBlank()) invalid();
                                    String relative =
                                            "Reports/"
                                                    + CdsImportKeys.hash(
                                                            "cds-export-report-file-v1",
                                                            patientKey,
                                                            address,
                                                            variant.name())
                                                    + "."
                                                    + variant.extension();
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
                                                                    control::checkpoint);
                                                        }
                                                    });
                                    attachments.add(copied);
                                    if (copied.plaintextBytes() != variant.length()
                                            || !copied.plaintextSha256().equals(variant.digest()))
                                        invalid();
                                    streams.requireActive();
                                    control.authorizeCategory(patient.patient(), category(), false);
                                    output.attachment(
                                            identity,
                                            new CdsExportPackageMemberDto(relative, copied));
                                    String root =
                                            CdsExportRetainedOccurrencePath.root(
                                                    sourceValues.filePath(), category());
                                    changes.add(
                                            new CdsExportRetainedSectionWriter.Change(
                                                    sourceValues
                                                            .filePath()
                                                            .sourcePath()
                                                            .substring(root.length() + 1),
                                                    Set.of(sourceValues.filePath().id()),
                                                    content -> content.text(relative)));
                                }
                            }
                            new CdsExportNativeFieldAssessmentValidator()
                                    .requireRepresentable(
                                            nativeRows,
                                            assessments,
                                            1_000_000,
                                            control::checkpoint);
                            for (var entry : fileAssessments.entrySet())
                                if (assessments.putIfAbsent(entry.getKey(), entry.getValue())
                                        != null) invalid();
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
                        }
                        // Every document row no applied import accounts for is a native chart
                        // document. Each is classified as the chart shows it, and mapped, before
                        // anything is staged: a document that holds the patient costs no copy.
                        var nativeRows = new TreeMap<Integer, CdsExportCapturedTextRowDto>();
                        for (var row : current.values())
                            if (row.table().equals("document"))
                                nativeRows.put(Integer.parseInt(row.fields().get("document_no")), row);
                        var natives = new ArrayList<NativeDocument>();
                        for (var entry : nativeRows.entrySet()) {
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
                                new CdsExportNativeFieldAssessmentValidator()
                                        .requireRepresentable(
                                                List.of(document, link),
                                                CdsExportDocumentRepresentation.removedDispositions(
                                                        document, link),
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
                            var mapped =
                                    new CdsExportNativeReportMapper()
                                            .map(
                                                    patient.patient(),
                                                    document,
                                                    link,
                                                    content,
                                                    providers,
                                                    zone);
                            new CdsExportNativeFieldAssessmentValidator()
                                    .requireRepresentable(
                                            mapped.rows(),
                                            mapped.fields(),
                                            1_000_000,
                                            control::checkpoint);
                            natives.add(new NativeDocument(documentId, document, link, file, mapped));
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
                                                        zone);
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
                        // Re-authenticates the whole captured values member before any staged
                        // attachment can be accepted with this patient's output.
                        captured.close();
                        var expectedSource = new HashSet<String>();
                        for (var field : retained)
                            if (field.category() == category()) expectedSource.add(field.id());
                        if (!owned.equals(current.keySet()) || !usedFiles.equals(files.keySet()))
                            review();
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

    /**
     * The patient's captured values member, authenticated once for all of its attachments
     * (review R2). Reopening the member for each attachment decrypted it from byte 0 to the
     * attachment's offset and then drained it to its end, so N attachments cost N complete
     * passes over everything captured for the patient. Opening this scope verifies every chunk
     * of the member once and pins its descriptor; each attachment then decrypts only the
     * chunks of its own range, each under its own authentication tag and chunk index, and its
     * SHA-256 is checked by both the range and the enclosing stream. Closing verifies the
     * whole member again, terminator and length included, before the attachments are accepted.
     */
    private static final class CapturedValues {
        private final CdsExportStagingService store;
        private final CdsExportStagedFileDto member;
        private CdsExportStagingService.VerifiedRanges ranges;
        private boolean closed;

        CapturedValues(CdsExportStagingService store, CdsExportStagedFileDto member) {
            this.store = Objects.requireNonNull(store);
            this.member = Objects.requireNonNull(member);
        }

        InputStream open(
                CdsExportCapturedFieldDto field,
                CdsExportAuthenticatedRangeInputStream.Checkpoint checkpoint,
                Runnable authorize)
                throws IOException {
            if (closed || field.kind() != CdsExportCapturedFieldDto.Kind.BINARY) invalid();
            if (ranges == null) ranges = store.verifiedRanges(member);
            return new CdsExportAuthenticatedRangeInputStream(
                    ranges.open(field.offset(), field.bytes(), field.sha256()),
                    0,
                    field.bytes(),
                    field.sha256(),
                    checkpoint,
                    authorize);
        }

        void close() throws IOException {
            closed = true;
            var open = ranges;
            ranges = null;
            if (open != null) open.close();
        }
    }

    /** A mapped native document and, when its content is a stored file, that captured file. */
    private record NativeDocument(
            String id,
            CdsExportCapturedTextRowDto document,
            CdsExportCapturedTextRowDto link,
            CdsExportCapturedDocumentFiles.File file,
            CdsExportNativeReportMapper.Result mapped) {
        @Override
        public String toString() {
            return "NativeDocument[protected]";
        }
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
        var row = document.fields();
        var metadata = prepared.values();
        String source = metadata.get("NativeSourceAuthor");
        if (source != null && source.codePointCount(0, source.length()) > 60) source = null;
        String subclass = metadata.get("SubClass"),
                description =
                        (subclass == null || subclass.isBlank() ? "Imported report" : subclass)
                                + (prepared.variants().size() == 1
                                        ? ""
                                        : " (" + variant.name() + " representation)")
                                + (variant.mediaReviewRequired()
                                        ? " (source media review required)"
                                        : "");
        if (!"CDS".equals(row.get("doctype"))
                || !"A".equals(row.get("status"))
                || !filename.equals(row.get("docfilename"))
                || !Objects.equals(metadata.get("Class"), row.get("docClass"))
                || !Objects.equals(subclass, row.get("docSubClass"))
                || !Objects.equals(metadata.get("SourceFacility"), row.get("sourceFacility"))
                || !Objects.equals(source, row.get("source"))
                || !variant.mime().equals(row.get("contenttype"))
                || !prepared.event().precision().name().equals(row.get("cds_content_precision"))
                || !description.equals(row.get("docdesc"))) invalid();
        if (variant.mediaReviewRequired()
                || !"0".equals(row.get("abnormal"))
                || !"0".equals(row.get("public1"))
                || !"0".equals(row.get("restrictToProgram"))
                || !empty(row.get("docxml"))
                || !empty(row.get("report_media"))
                || !empty(row.get("responsible"))
                || !empty(row.get("reviewer"))
                || row.get("reviewdatetime") != null
                || row.get("appointment_no") != null && !row.get("appointment_no").equals("0"))
            review();
        if (!"demographic".equals(link.fields().get("module"))
                || !Integer.toString(patient).equals(link.fields().get("module_id"))
                || !row.get("document_no").equals(link.fields().get("document_no"))
                || !"A".equals(link.fields().get("status"))) invalid();
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
