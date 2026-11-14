/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedIndexDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Tells the patient inventory which Reports records exist only because a report's CARLOS context
 * is longer than Reports/Notes holds ({@link CdsExportReportContext}).
 *
 * <p>Whether a context fits is decided by its exact text, so this planner does not estimate: it
 * reads the same captured rows the report occurrence service reads and runs the same mappers. It
 * answers for three kinds of report:
 *
 * <ul>
 *   <li>a native chart document ({@link CdsExportNativeReportMapper}), whose review rows and
 *       inbox comments can pass Notes;
 *   <li>an embedded document of a native HL7 laboratory report ({@link
 *       CdsExportLaboratoryDocumentMapper}), whose laboratory notes and reviews can;
 *   <li>a report from an earlier CDS import that carries no Notes of its own, whose reviews
 *       recorded in CARLOS are appended as Notes only while they fit and no document row was
 *       edited ({@link CdsExportReportOccurrenceService#importedReviewsInNotes}); otherwise they
 *       travel in the report's supplementary record. An imported report that has its own Notes
 *       always uses that record, and the inventory expects it without this planner.
 * </ul>
 *
 * <p>HRM reports are answered by {@link CdsExportHospitalReportOccurrenceService#identities}. A
 * document a mapper would hold is skipped here: the occurrence service holds the patient for it.
 */
final class CdsExportReportContextPlanner {
    /** Reviewer captures of the patient's documents, each with its document reference column. */
    private static final Map<String, String> REVIEWERS =
            Map.of(
                    CdsExportDocumentRouteSourceReader.TABLE, "lab_no",
                    "document_review", "document_no",
                    "DocumentExtraReviewer", "documentNo");

    private static final Pattern NOTES =
            Pattern.compile(".*/Reports\\[[1-9][0-9]{0,17}\\]/Notes\\[1\\]");

    private final CdsExportNativeCaptureService captures;
    private final CdsExportLaboratoryOccurrenceService laboratories;

    CdsExportReportContextPlanner(
            CdsExportNativeCaptureService captures,
            CdsExportLaboratoryOccurrenceService laboratories) {
        this.captures = Objects.requireNonNull(captures);
        this.laboratories = Objects.requireNonNull(laboratories);
    }

    /**
     * @param nativeDocuments numbers of the captured documents the chart shows that no applied
     *     import accounts for
     * @param documentDigests current witness digest of every captured document row, by number
     * @param laboratoryDocuments the native laboratory reports that hold embedded documents
     * @return the identity of every companion or supplementary Reports record to expect
     */
    // FindSecBugs UNSAFE_HASH_EQUALS: SHA-256 of a document row as it is now, compared with the digest the import receipt recorded, to find reports edited since import; not a secret.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "UNSAFE_HASH_EQUALS", justification = "SHA-256 of a document row as it is now, compared with the digest the import receipt recorded, to find reports edited since import; not a secret")
    List<CdsExportIdentityDto> identities(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportRetainedIndexDto index,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            Set<String> nativeDocuments,
            Map<String, String> documentDigests,
            List<CdsExportLaboratoryOccurrenceService.NativeSource> laboratoryDocuments)
            throws IOException {
        if (!lease.document().command().categories().contains(CdsCategory.REPORTS)
                || !tables.contains("document")
                || !tables.contains("ctl_document")
                || !tables.containsAll(REVIEWERS.keySet())) return List.of();
        var result = new ArrayList<CdsExportIdentityDto>();
        // Imported reports without Notes of their own: an edited one always has a supplementary
        // record; an unedited one is decided below from its review rows.
        var noted = new HashSet<String>();
        for (var row : index.tables().getOrDefault("cds_import_field", Map.of()).values()) {
            String parent = index.nativeParents().get(row.recordKey());
            String path = row.fields().get("source_path");
            if (parent != null && path != null && NOTES.matcher(path).matches()) noted.add(parent);
        }
        var importedDocuments = new HashMap<String, String>();
        var edited = new TreeSet<String>();
        for (var unit : index.tables().getOrDefault("cds_import_unit", Map.of()).values()) {
            control.checkpoint();
            if (!CdsCategory.REPORTS.wireName().equals(unit.fields().get("category"))) continue;
            String address = unit.fields().get("run_id") + ":" + unit.fields().get("unit_key");
            if (!index.appliedUnits().contains(address) || noted.contains(address)) continue;
            for (var reference : index.references().getOrDefault(address, List.of()))
                if (reference.table().equals("document")) {
                    importedDocuments.put(reference.id(), address);
                    String now = documentDigests.get(reference.id());
                    if (now != null && !now.equals(reference.digest())) edited.add(address);
                }
        }
        for (String address : edited)
            result.add(
                    new CdsExportIdentityDto(
                            patientKey,
                            CdsCategory.REPORTS,
                            CdsExportReportOccurrenceService.importedReviewsId(address)));
        importedDocuments.values().removeAll(edited);
        if (nativeDocuments.isEmpty() && importedDocuments.isEmpty() && laboratoryDocuments.isEmpty())
            return List.copyOf(result);
        var documents = new HashMap<String, CdsExportCapturedTextRowDto>();
        var links = new HashMap<String, CdsExportCapturedTextRowDto>();
        var reviewers = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
        if (!nativeDocuments.isEmpty() || !importedDocuments.isEmpty()) {
            var wanted = new HashSet<>(REVIEWERS.keySet());
            wanted.add("document");
            wanted.add("ctl_document");
            var read =
                    new CdsExportCapturedTextRowsReader(captures)
                            .forEachTable(
                                    lease,
                                    patient,
                                    tables,
                                    wanted,
                                    source.manifest(),
                                    bounds.fields(),
                                    bounds.bytes(),
                                    67_108_864,
                                    control,
                                    row -> {
                                        control.checkpoint();
                                        String column = REVIEWERS.get(row.table());
                                        String document =
                                                row.fields()
                                                        .get(column == null ? "document_no" : column);
                                        boolean wantedRow =
                                                nativeDocuments.contains(document)
                                                        || column != null
                                                                && importedDocuments.containsKey(
                                                                        document);
                                        if (!wantedRow) return;
                                        if (column != null)
                                            reviewers
                                                    .computeIfAbsent(
                                                            document, ignored -> new ArrayList<>())
                                                    .add(row);
                                        else if (row.table().equals("document"))
                                            documents.put(document, row);
                                        else links.put(document, row);
                                    });
            if (!read.equals(source.capture())) invalid();
        }
        var providers =
                new CdsExportPublicProviderReader(captures)
                        .read(lease, patient, tables, source, bounds, control);
        var zone =
                new CdsExportClinicalZoneService(captures)
                        .read(
                                lease,
                                patient,
                                tables,
                                source,
                                bounds.fields(),
                                bounds.bytes(),
                                control)
                        .zone();
        for (String id : new TreeSet<>(nativeDocuments)) {
            control.checkpoint();
            var document = documents.get(id);
            var link = links.get(id);
            if (document == null || link == null) continue;
            var representation =
                    CdsExportDocumentRepresentation.of(document.fields(), link.fields());
            if (representation != CdsExportDocumentRepresentation.FILE
                    && representation != CdsExportDocumentRepresentation.STORED_HTML) continue;
            try {
                var mapped =
                        new CdsExportNativeReportMapper()
                                .map(
                                        patient.patient(),
                                        document,
                                        link,
                                        // Notes do not depend on the file's declared extension.
                                        representation == CdsExportDocumentRepresentation.FILE
                                                ? CdsExportNativeReportMapper.Content.file(
                                                        "Reports/" + "0".repeat(64) + ".bin", "bin")
                                                : CdsExportNativeReportMapper.Content.html(),
                                        providers,
                                        zone,
                                        reviews(reviewers.getOrDefault(id, List.of()), providers, zone),
                                        // The occurrence service asks the live program question.
                                        program -> true);
                if (mapped.companion() != null)
                    result.add(
                            new CdsExportIdentityDto(
                                    patientKey,
                                    CdsCategory.REPORTS,
                                    CdsExportReportContext.id("document", id)));
            } catch (CdsExportMappingReviewException held) {
                // The occurrence service holds the patient for this document.
            }
        }
        // Unedited imported reports without Notes: by unit, every review row of its documents.
        var units = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
        importedDocuments.forEach(
                (document, address) ->
                        units.computeIfAbsent(address, ignored -> new ArrayList<>())
                                .addAll(reviewers.getOrDefault(document, List.of())));
        for (var unit : units.entrySet()) {
            control.checkpoint();
            if (unit.getValue().isEmpty()) continue;
            try {
                var reviews = reviews(unit.getValue(), providers, zone);
                if (reviews.carried().isEmpty()) continue;
                if (!CdsExportReportOccurrenceService.importedReviewsInNotes(
                        false,
                        false,
                        CdsExportReportOccurrenceService.importedReviewNotes(reviews, List.of())))
                    result.add(
                            new CdsExportIdentityDto(
                                    patientKey,
                                    CdsCategory.REPORTS,
                                    CdsExportReportOccurrenceService.importedReviewsId(
                                            unit.getKey())));
            } catch (CdsExportMappingReviewException held) {
                // The occurrence service holds the patient for this report.
            }
        }
        if (!laboratoryDocuments.isEmpty()) {
            var routes = laboratories.routes(lease, patient, tables, source, bounds, control);
            var mapper = new CdsExportLaboratoryDocumentMapper();
            for (var laboratory : laboratoryDocuments) {
                control.checkpoint();
                try {
                    var reviews =
                            CdsExportInboxReviewMapper.report()
                                    .map(
                                            routes.getOrDefault(laboratory.id(), List.of()),
                                            List.of(),
                                            providers,
                                            zone);
                    for (int i = 0; i < laboratory.parsed().documents().size(); i++)
                        if (mapper.notes(
                                                laboratory,
                                                i,
                                                CdsExportLaboratoryDocumentMapper.described(
                                                        laboratory, i),
                                                reviews)
                                        .companion()
                                != null)
                            result.add(
                                    new CdsExportIdentityDto(
                                            patientKey,
                                            CdsCategory.REPORTS,
                                            CdsExportReportContext.id(
                                                    "hl7TextMessage",
                                                    laboratory.id()
                                                            + ":"
                                                            + laboratory
                                                                    .parsed()
                                                                    .documents()
                                                                    .get(i)
                                                                    .ordinal())));
                } catch (CdsExportMappingReviewException held) {
                    // The occurrence service holds the patient for this report.
                }
            }
        }
        return List.copyOf(result);
    }

    private static CdsExportInboxReviewMapper.Reviews reviews(
            List<CdsExportCapturedTextRowDto> rows,
            Map<String, CdsExportCapturedTextRowDto> providers,
            java.time.ZoneId zone)
            throws IOException {
        if (rows.isEmpty()) return CdsExportInboxReviewMapper.Reviews.NONE;
        return CdsExportInboxReviewMapper.report()
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
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_PATIENT_INVENTORY_INVALID");
    }
}
