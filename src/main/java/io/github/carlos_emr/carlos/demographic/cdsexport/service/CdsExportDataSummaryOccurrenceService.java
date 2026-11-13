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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Exports the data-summary records of one authenticated patient generation as CDS 5.2 Reports
 * (maintainer decision 6). Each record of {@link CdsExportDataSummaryPlanner} becomes one Reports
 * entry, in schema order: Media Download, Format Binary, FileExtensionAndVersion pdf, FilePath of
 * its encrypted attachment, the kind's reviewed Class, a SubClass naming the record kind (and
 * "revision n of m" for an encounter form), EventDateTime from the record's own clinical date at its
 * true precision when it has a valid one, and Notes saying it is a CARLOS data summary.
 *
 * <p>Every captured field of the record is assessed MAPPED to the summary and checked by {@link
 * CdsExportNativeFieldAssessmentValidator}; the inventory, the emission and the package ReadMe use
 * the same deterministic plan. The document of a kind comes from a {@link Renderer}: the data
 * summary by default, and a faithful rendering (an eForm, a consultation letter, a Rourke chart)
 * can replace it for one kind without changing capture or emission.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportDataSummaryOccurrenceService {
    /** Produces the PDF bytes of one record. */
    @FunctionalInterface
    public interface Renderer {
        byte[] render(
                CdsExportDataSummaryPlanner.Group group,
                CdsExportDataSummaryHtmlComposer.Patient patient)
                throws IOException;
    }

    static final String NOTES =
            "CARLOS data summary: CARLOS EMR has no CDS 5.2 element for this record, so the attached"
                    + " PDF lists every stored field of the record and its related rows with the"
                    + " exact values. It is not an original document.";

    private final CdsExportNativeCaptureService captures;
    private final CdsExportStagingFactory stores;
    private final Renderer summaries;
    private final Map<CdsExportDataSummaryRegistry.Kind, Renderer> renderings;

    @Autowired
    public CdsExportDataSummaryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores,
            CdsExportDataSummaryPdfService pdfs) {
        this(captures, stores, Objects.requireNonNull(pdfs)::render, Map.of());
    }

    CdsExportDataSummaryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores,
            Renderer summaries,
            Map<CdsExportDataSummaryRegistry.Kind, Renderer> renderings) {
        this.captures = Objects.requireNonNull(captures);
        this.stores = Objects.requireNonNull(stores);
        this.summaries = Objects.requireNonNull(summaries);
        this.renderings = Map.copyOf(renderings);
    }

    /** The planned records and the patient header of one capture. */
    record Plan(
            List<CdsExportDataSummaryPlanner.Group> groups,
            CdsExportDataSummaryHtmlComposer.Patient patient) {
        Plan {
            groups = List.copyOf(groups);
        }

        @Override
        public String toString() {
            return "CdsExportDataSummaryOccurrenceService.Plan[protected]";
        }
    }

    /** True when the capture scope holds data-summary tables (Reports was selected). */
    public static boolean captured(Set<String> tables) {
        return !CdsExportDataSummaryRowsReader.tables(tables).isEmpty();
    }

    Plan plan(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (!lease.document().command().categories().contains(CdsCategory.REPORTS)
                || !tables.equals(source.capture().tables().keySet()))
            throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
        var read =
                new CdsExportDataSummaryRowsReader(captures)
                        .read(lease, patient, tables, source.manifest(), bounds, control);
        var zone =
                new CdsExportClinicalZoneService(captures)
                        .read(lease, patient, tables, source, bounds.fields(), bounds.bytes(), control)
                        .zone();
        return new Plan(
                CdsExportDataSummaryPlanner.plan(read.rows(), zone),
                CdsExportDataSummaryHtmlComposer.Patient.of(read.demographic()));
    }

    /** The Reports identities the inventory must expect: one per planned record. */
    public List<CdsExportIdentityDto> identities(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        var result = new ArrayList<CdsExportIdentityDto>();
        for (var group : plan(lease, patient, tables, source, bounds, control).groups())
            result.add(identity(patientKey, group));
        return List.copyOf(result);
    }

    /**
     * Records per kind label, for the package ReadMe; empty when Reports was not selected (no
     * data-summary table was captured).
     */
    public Map<String, Long> kinds(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (!captured(tables)) return Map.of();
        var counts = new EnumMap<CdsExportDataSummaryRegistry.Kind, Long>(
                CdsExportDataSummaryRegistry.Kind.class);
        for (var group : plan(lease, patient, tables, source, bounds, control).groups())
            counts.merge(group.kind(), 1L, Long::sum);
        var result = new LinkedHashMap<String, Long>();
        counts.forEach((kind, count) -> result.put(kind.label(), count));
        return java.util.Collections.unmodifiableMap(result);
    }

    /**
     * Emits every planned record as a Reports entry with its staged PDF attachment and receipt.
     *
     * @return the number of records emitted
     */
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    public long append(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        Objects.requireNonNull(output);
        if (patientKey == null || !patientKey.matches("[0-9a-f]{64}"))
            throw new IOException("CDS_EXPORT_DATA_SUMMARY_SCOPE_INVALID");
        if (!captured(tables)) return 0;
        var plan = plan(lease, patient, tables, source, bounds, control);
        requireAuditCapacity(plan.groups(), bounds);
        var store =
                stores.openExisting(
                        lease.runId(),
                        lease.epoch(),
                        lease.document().storage(),
                        control::checkpoint);
        var attachments = new ArrayList<CdsExportStagedFileDto>();
        boolean complete = false;
        Throwable primary = null;
        long emitted = 0;
        try {
            for (var group : plan.groups()) {
                control.checkpoint();
                control.authorizeCategory(patient.patient(), CdsCategory.REPORTS, false);
                var assessments = assessments(group);
                new CdsExportNativeFieldAssessmentValidator()
                        .requireRepresentable(
                                group.rows().stream()
                                        .map(CdsExportDataSummaryPlanner.Row::assessed)
                                        .toList(),
                                assessments,
                                1_000_000,
                                control::checkpoint);
                byte[] pdf = renderings.getOrDefault(group.kind(), summaries).render(group, plan.patient());
                CdsExportDataSummaryPdfService.requirePdf(pdf);
                var staged = store.stage("REPORT_ATTACHMENT", outputBytes -> outputBytes.write(pdf));
                attachments.add(staged);
                java.util.Arrays.fill(pdf, (byte) 0);
                var identity = identity(patientKey, group);
                String relative =
                        "Reports/"
                                + CdsImportKeys.hash(
                                        "cds-export-data-summary-file-v1",
                                        patientKey,
                                        group.table(),
                                        group.id())
                                + ".pdf";
                output.attachment(identity, new CdsExportPackageMemberDto(relative, staged));
                xml.emit(
                        new CdsExportXmlComposer.Section(
                                identity, body -> write(group, relative, body)));
                control.authorizeCategory(patient.patient(), CdsCategory.REPORTS, false);
                output.accept(
                        new CdsExportClinicalOccurrenceService.Receipt(
                                identity,
                                source.manifest().plaintextSha256(),
                                group.table(),
                                group.id(),
                                null,
                                assessments,
                                Set.of(),
                                Set.of()));
                emitted++;
            }
            captures.authorizeSources(lease, patient, tables, control);
            complete = true;
            return emitted;
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (!complete)
                for (var attachment : attachments.reversed())
                    try {
                        store.discardUnpublished(attachment);
                    } catch (IOException cleanup) {
                        if (primary != null) primary.addSuppressed(cleanup);
                        else throw cleanup;
                    }
        }
    }

    /**
     * Each assessed field becomes one line of the patient's reconciliation audit, whose size is
     * bounded per patient ({@code maxIndexBytes}) and stops the whole job when exceeded. A chart
     * whose data summaries alone would take most of that budget (a very long encounter-form
     * history) holds only that patient instead.
     */
    static void requireAuditCapacity(
            List<CdsExportDataSummaryPlanner.Group> groups,
            CdsExportClinicalOccurrenceService.Bounds bounds)
            throws IOException {
        long fields = 0;
        for (var group : groups)
            for (var row : group.rows()) fields = Math.addExact(fields, row.values().size());
        if (fields > bounds.indexBytes() / 4 * 3 / AUDIT_BYTES_PER_FIELD)
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportMappingReviewException(AUDIT_LIMIT);
    }

    /** A generous upper bound of one field-decision audit line. */
    static final long AUDIT_BYTES_PER_FIELD = 512;

    static final String AUDIT_LIMIT = "CDS_EXPORT_DATA_SUMMARY_AUDIT_LIMIT";

    /** Reports content in CDS 5.2 schema order. */
    static void write(
            CdsExportDataSummaryPlanner.Group group,
            String relative,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "Media", "Download");
        xml.element("cds", "Format", "Binary");
        xml.element("cds", "FileExtensionAndVersion", "pdf");
        xml.element("cds", "FilePath", relative);
        xml.element("cds", "Class", group.kind().reportClass());
        xml.element("cds", "SubClass", group.subClass());
        if (group.event() != null) {
            xml.start("cds", "EventDateTime");
            CdsExportNativeHistoryXmlWriter.dateContents(xml, group.event());
            xml.end();
        }
        xml.element("cds", "Notes", NOTES);
    }

    /** Every captured field of the record is carried, with its exact value, by the summary. */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments(
            CdsExportDataSummaryPlanner.Group group) {
        var result = new TreeMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>(
                java.util.Comparator.comparing(CdsExportNativeFieldKeyDto::table)
                        .thenComparing(CdsExportNativeFieldKeyDto::recordKey)
                        .thenComparing(CdsExportNativeFieldKeyDto::column));
        for (var row : group.rows())
            row.values()
                    .forEach(
                            (column, value) -> {
                                var presence =
                                        switch (value.storage()) {
                                            case SQL_NULL -> Presence.SQL_NULL;
                                            case TEXT ->
                                                    value.text().isEmpty()
                                                            ? Presence.EMPTY
                                                            : Presence.VALUE;
                                            case BINARY ->
                                                    value.bytes() == 0
                                                            ? Presence.EMPTY
                                                            : Presence.VALUE;
                                        };
                                if (result.putIfAbsent(
                                                new CdsExportNativeFieldKeyDto(
                                                        row.table(), row.recordKey(), column),
                                                new CdsExportFieldAssessmentDto(
                                                        presence,
                                                        Outcome.MAPPED,
                                                        "Reports/FilePath",
                                                        "CARLOS_DATA_SUMMARY_EXACT_VALUE"))
                                        != null)
                                    throw new IllegalStateException(
                                            "CDS_EXPORT_DATA_SUMMARY_FIELD_DUPLICATE");
                            });
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    static CdsExportIdentityDto identity(
            String patientKey, CdsExportDataSummaryPlanner.Group group) {
        return new CdsExportIdentityDto(
                patientKey,
                CdsCategory.REPORTS,
                CdsImportKeys.hash("carlos-native-occurrence-v1", group.table(), group.id()));
    }
}
