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
    private Map<String, CdsExportCapturedTextRowDto> providers;
    private java.time.ZoneId zone;

    CdsExportReportContextPlanner(CdsExportNativeCaptureService captures) {
        this.captures = Objects.requireNonNull(captures);
    }

    /** Receives the identities of the companion and supplementary records, one at a time. */
    @FunctionalInterface
    interface Identities {
        void accept(CdsExportIdentityDto identity) throws IOException;
    }

    /**
     * The identities {@link #forEachIdentity} reports, as one list.
     *
     * @param nativeDocuments numbers of the captured documents the chart shows that no applied
     *     import accounts for
     * @param documentDigests current witness digest of every captured document row, by number
     * @return the identity of every companion or supplementary Reports record to expect, except
     *     those of documents embedded in laboratory reports ({@link #laboratoryCompanions})
     */
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
            Map<String, String> documentDigests)
            throws IOException {
        var result = new ArrayList<CdsExportIdentityDto>();
        forEachIdentity(
                lease,
                patient,
                patientKey,
                tables,
                source,
                index,
                bounds,
                control,
                nativeDocuments,
                documentDigests,
                result::add);
        return List.copyOf(result);
    }

    /**
     * Feeds {@code sink} the identity of every companion or supplementary Reports record to
     * expect, except those of documents embedded in laboratory reports ({@link
     * #laboratoryCompanions}). The documents are read and mapped window by window ({@link
     * CdsExportSectionWindowPlanner}): the owners are the native documents and the unedited
     * imported reports without Notes, and a window holds the document, link, reviewer and route
     * rows of its owners. A chart that fits one window is read in one pass, as before; no count
     * or size of documents refuses it.
     *
     * @param nativeDocuments numbers of the captured documents the chart shows that no applied
     *     import accounts for
     * @param documentDigests current witness digest of every captured document row, by number
     */
    // FindSecBugs UNSAFE_HASH_EQUALS: SHA-256 of a document row as it is now, compared with the digest the import receipt recorded, to find reports edited since import; not a secret.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "UNSAFE_HASH_EQUALS", justification = "SHA-256 of a document row as it is now, compared with the digest the import receipt recorded, to find reports edited since import; not a secret")
    void forEachIdentity(
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
            Identities sink)
            throws IOException {
        Objects.requireNonNull(sink);
        if (!planned(lease, tables)) return;
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
            sink.accept(
                    new CdsExportIdentityDto(
                            patientKey,
                            CdsCategory.REPORTS,
                            CdsExportReportOccurrenceService.importedReviewsId(address)));
        importedDocuments.values().removeAll(edited);
        if (nativeDocuments.isEmpty() && importedDocuments.isEmpty()) return;
        // The owners: each native document, then each unedited imported report without Notes
        // (references to strings the caller and the index hold).
        var natives = new ArrayList<>(new TreeSet<>(nativeDocuments));
        var byUnit = new TreeMap<String, List<String>>();
        importedDocuments.forEach(
                (document, address) ->
                        byUnit.computeIfAbsent(address, ignored -> new ArrayList<>()).add(document));
        var addresses = new ArrayList<>(byUnit.keySet());
        var wanted = new HashSet<>(REVIEWERS.keySet());
        wanted.add("document");
        wanted.add("ctl_document");
        CdsExportSectionWindowPlanner.forEachWindow(
                (long) natives.size() + addresses.size(),
                bounds.window(),
                owners -> {
                    for (int i = 0; i < natives.size(); i++) owners.add("n" + i);
                    for (int i = 0; i < addresses.size(); i++) owners.add("u" + i);
                },
                (window, budget) -> {
                    // The documents of this window: its native documents, and the documents of
                    // its imported reports with the report each belongs to.
                    var windowNatives = new ArrayList<String>();
                    var windowImported = new HashMap<String, String>();
                    for (int i = 0; i < natives.size(); i++)
                        if (window.contains("n" + i)) windowNatives.add(natives.get(i));
                    for (int i = 0; i < addresses.size(); i++)
                        if (window.contains("u" + i))
                            for (String document : byUnit.get(addresses.get(i)))
                                windowImported.put(document, addresses.get(i));
                    var nativeSet = new HashSet<>(windowNatives);
                    var documents = new HashMap<String, CdsExportCapturedTextRowDto>();
                    var links = new HashMap<String, CdsExportCapturedTextRowDto>();
                    var reviewers = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
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
                                                                .get(
                                                                        column == null
                                                                                ? "document_no"
                                                                                : column);
                                                boolean wantedRow =
                                                        nativeSet.contains(document)
                                                                || column != null
                                                                        && windowImported
                                                                                .containsKey(
                                                                                        document);
                                                // A window found too large keeps nothing more; it
                                                // is read again with fewer documents.
                                                if (!wantedRow || budget.exceeded()) return;
                                                long size = 1024L;
                                                for (String value : row.fields().values())
                                                    if (value != null)
                                                        size =
                                                                Math.addExact(
                                                                        size, 64L + 2L * value.length());
                                                if (!budget.add(0, size)) return;
                                                if (column != null)
                                                    reviewers
                                                            .computeIfAbsent(
                                                                    document,
                                                                    ignored -> new ArrayList<>())
                                                            .add(row);
                                                else if (row.table().equals("document"))
                                                    documents.put(document, row);
                                                else links.put(document, row);
                                            });
                    if (!read.equals(source.capture())) invalid();
                    if (budget.exceeded()) return false;
                    var providers = providers(lease, patient, tables, source, bounds, control);
                    var zone = zone(lease, patient, tables, source, bounds, control);
                    for (String id : windowNatives) {
                        control.checkpoint();
                        var document = documents.get(id);
                        var link = links.get(id);
                        if (document == null || link == null) continue;
                        var representation =
                                CdsExportDocumentRepresentation.of(document.fields(), link.fields());
                        if (representation != CdsExportDocumentRepresentation.FILE
                                && representation != CdsExportDocumentRepresentation.STORED_HTML)
                            continue;
                        try {
                            var mapped =
                                    new CdsExportNativeReportMapper()
                                            .map(
                                                    patient.patient(),
                                                    document,
                                                    link,
                                                    // Notes do not depend on the file's declared
                                                    // extension.
                                                    representation
                                                                    == CdsExportDocumentRepresentation
                                                                            .FILE
                                                            ? CdsExportNativeReportMapper.Content.file(
                                                                    "Reports/" + "0".repeat(64) + ".bin",
                                                                    "bin")
                                                            : CdsExportNativeReportMapper.Content.html(),
                                                    providers,
                                                    zone,
                                                    reviews(
                                                            reviewers.getOrDefault(id, List.of()),
                                                            providers,
                                                            zone),
                                                    // The occurrence service asks the live program
                                                    // question.
                                                    program -> true);
                            if (mapped.companion() != null)
                                sink.accept(
                                        new CdsExportIdentityDto(
                                                patientKey,
                                                CdsCategory.REPORTS,
                                                CdsExportReportContext.id("document", id)));
                        } catch (CdsExportMappingReviewException held) {
                            // The occurrence service holds the patient for this document.
                        }
                    }
                    // Unedited imported reports without Notes: by unit, every review row of its
                    // documents.
                    var units = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
                    windowImported.forEach(
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
                                    CdsExportReportOccurrenceService.importedReviewNotes(
                                            reviews, List.of())))
                                sink.accept(
                                        new CdsExportIdentityDto(
                                                patientKey,
                                                CdsCategory.REPORTS,
                                                CdsExportReportOccurrenceService.importedReviewsId(
                                                        unit.getKey())));
                        } catch (CdsExportMappingReviewException held) {
                            // The occurrence service holds the patient for this report.
                        }
                    }
                    return true;
                });
    }

    /** Whether the job writes Reports records whose context this planner predicts. */
    boolean planned(CdsExportLeaseDto lease, Set<String> tables) {
        return lease.document().command().categories().contains(CdsCategory.REPORTS)
                && tables.contains("document")
                && tables.contains("ctl_document")
                && tables.containsAll(REVIEWERS.keySet());
    }

    /**
     * The companion Reports records of one native laboratory report's embedded documents: one
     * for each document whose notes and reviews are longer than Reports/Notes. The inventory
     * asks report by report, as the reports are read window by window, so no report is held for
     * this. A report a mapper would hold yields none: the occurrence service holds the patient.
     *
     * @param routes the report's inbox routes
     */
    List<CdsExportIdentityDto> laboratoryCompanions(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportLaboratoryOccurrenceService.NativeSource laboratory,
            List<CdsExportCapturedTextRowDto> routes)
            throws IOException {
        if (!planned(lease, tables) || laboratory.parsed().documents().isEmpty()) return List.of();
        var providers = providers(lease, patient, tables, source, bounds, control);
        var zone = zone(lease, patient, tables, source, bounds, control);
        var result = new ArrayList<CdsExportIdentityDto>();
        var mapper = new CdsExportLaboratoryDocumentMapper();
        control.checkpoint();
        try {
            var reviews = CdsExportInboxReviewMapper.report().map(routes, List.of(), providers, zone);
            for (int i = 0; i < laboratory.parsed().documents().size(); i++)
                if (mapper.notes(
                                        laboratory,
                                        i,
                                        CdsExportLaboratoryDocumentMapper.described(laboratory, i),
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
        return List.copyOf(result);
    }

    /** The public providers the chart names, read once for this planner. */
    private Map<String, CdsExportCapturedTextRowDto> providers(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (providers == null)
            providers =
                    new CdsExportPublicProviderReader(captures)
                            .read(lease, patient, tables, source, bounds, control);
        return providers;
    }

    /** The clinic zone, read once for this planner. */
    private java.time.ZoneId zone(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (zone == null)
            zone =
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
        return zone;
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
