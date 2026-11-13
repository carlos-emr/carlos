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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
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
import java.util.HashSet;
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
 * <p>Every captured field of the record is assessed MAPPED to the summary (an e-mail access
 * credential is withheld and assessed OPERATIONAL) and checked by {@link
 * CdsExportNativeFieldAssessmentValidator}; the inventory, the emission and the package ReadMe use
 * the same deterministic plan. The document of a kind comes from a {@link Renderer}: the data
 * summary by default, and a faithful rendering (an eForm, a consultation letter, a Rourke chart)
 * can replace it for one kind without changing capture or emission.
 *
 * <p>eForms (plan item 2.5). Only the eForms the chart lists are exported: a removed instance
 * (status 0: deleted, or replaced by an edit) or a patient-independent one is never a Reports
 * entry; every captured field of it, and of the rows that belong to it, is accounted OPERATIONAL
 * ({@link Withheld}) and the package ReadMe counts them per patient. Each listed eForm keeps its
 * data summary, the lossless record of every stored value, and also gets a second Reports entry
 * holding the form as CARLOS displays it ({@value CdsExportEformRenderingSourceReader#TABLE}).
 * When the rendered form could not be produced its data summary says so, with the reason, in Notes
 * and on the summary itself; a render never holds the patient.
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

    /** Notes of the Reports entry that holds an eForm as CARLOS displays it. */
    static final String RENDERED_NOTES =
            "CARLOS eForm: the attached PDF is this saved eForm as CARLOS displays it, rendered when"
                    + " the export ran. Every stored value of the eForm is also in its CARLOS data"
                    + " summary Report.";

    /** Why an eForm the chart does not list is captured but never exported. */
    enum Withheld {
        REMOVED_EFORM("Removed eForm (deleted or replaced by an edit)", "CARLOS_EFORM_REMOVED_NOT_EXPORTED"),
        PATIENT_INDEPENDENT_EFORM("Patient-independent eForm", "CARLOS_EFORM_PATIENT_INDEPENDENT_NOT_EXPORTED");

        private final String label;
        private final String rule;

        Withheld(String label, String rule) {
            this.label = label;
            this.rule = rule;
        }

        /** The ReadMe label of the count. */
        String label() {
            return label;
        }

        /** The OPERATIONAL rule every captured field of such a record is accounted under. */
        String rule() {
            return rule;
        }
    }

    /** A captured record that is not exported, and why. */
    record Hidden(CdsExportDataSummaryPlanner.Group group, Withheld why) {
        @Override
        public String toString() {
            return "CdsExportDataSummaryOccurrenceService.Hidden[" + why + "]";
        }
    }

    static final String EFORM_TABLE = "eform_data";

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

    /**
     * The exported records, the captured records that are not exported, the patient header and,
     * when asked for, each listed eForm's captured render outcome by eForm identity.
     */
    record Plan(
            List<CdsExportDataSummaryPlanner.Group> groups,
            CdsExportDataSummaryHtmlComposer.Patient patient,
            List<Hidden> withheld,
            Map<String, CdsExportEformRenderings.Rendering> renderings) {
        Plan {
            groups = List.copyOf(groups);
            withheld = List.copyOf(withheld);
            renderings = java.util.Collections.unmodifiableMap(new TreeMap<>(renderings));
        }

        Plan(
                List<CdsExportDataSummaryPlanner.Group> groups,
                CdsExportDataSummaryHtmlComposer.Patient patient) {
            this(groups, patient, List.of(), Map.of());
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
        return plan(lease, patient, tables, source, bounds, control, false);
    }

    Plan plan(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            boolean withRenderings)
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
        var exported = new ArrayList<CdsExportDataSummaryPlanner.Group>();
        var withheld = new ArrayList<Hidden>();
        for (var group : CdsExportDataSummaryPlanner.plan(read.rows(), zone)) {
            var why = withheld(group);
            if (why == null) exported.add(group);
            else withheld.add(new Hidden(group, why));
        }
        Map<String, CdsExportEformRenderings.Rendering> renderings = Map.of();
        if (withRenderings) {
            renderings =
                    CdsExportEformRenderings.read(
                            captures, lease, patient, tables, source, bounds, control);
            requireRenderingScope(exported, tables, renderings);
        }
        return new Plan(
                exported,
                CdsExportDataSummaryHtmlComposer.Patient.of(read.demographic()),
                withheld,
                renderings);
    }

    /**
     * Whether a record is an eForm the chart does not list: its root {@code eform_data} row is
     * patient-independent, or has status 0 (deleted, or replaced by an edit). These are the rows
     * {@code EFormDataDao.findByDemographicIdCurrent(patient, true)} leaves out, and the renderer
     * never sees them ({@code CdsExportEformRenderReadDao}).
     */
    static Withheld withheld(CdsExportDataSummaryPlanner.Group group) {
        if (group.kind() != CdsExportDataSummaryRegistry.Kind.EFORM
                || !EFORM_TABLE.equals(group.table())
                || group.detached()) return null;
        var root = group.root();
        if (!"0".equals(root.text("patient_independent")))
            return Withheld.PATIENT_INDEPENDENT_EFORM;
        if ("0".equals(root.text("status"))) return Withheld.REMOVED_EFORM;
        return null;
    }

    /**
     * Every listed eForm has exactly one captured render outcome and nothing else has one: both
     * were read in the same snapshot with the same selection, so any difference is a defect.
     */
    static void requireRenderingScope(
            List<CdsExportDataSummaryPlanner.Group> exported,
            Set<String> tables,
            Map<String, CdsExportEformRenderings.Rendering> renderings)
            throws IOException {
        if (!CdsExportEformRenderings.captured(tables)) {
            if (!renderings.isEmpty()) throw new IOException("CDS_EXPORT_EFORM_RENDERING_SCOPE_INVALID");
            return;
        }
        var listed = new java.util.TreeSet<String>();
        for (var group : exported)
            if (listed(group) && !listed.add(group.id()))
                throw new IOException("CDS_EXPORT_EFORM_RENDERING_SCOPE_INVALID");
        if (!listed.equals(renderings.keySet()))
            throw new IOException("CDS_EXPORT_EFORM_RENDERING_SCOPE_INVALID");
    }

    /** An exported eForm record the chart lists, which has a render outcome. */
    private static boolean listed(CdsExportDataSummaryPlanner.Group group) {
        return group.kind() == CdsExportDataSummaryRegistry.Kind.EFORM
                && EFORM_TABLE.equals(group.table())
                && !group.detached();
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
        var plan = plan(lease, patient, tables, source, bounds, control, true);
        for (var group : plan.groups()) result.add(identity(patientKey, group));
        // One more Reports entry for each listed eForm whose rendered form was captured.
        for (var rendering : plan.renderings().values())
            if (rendering.rendered()) result.add(renderedIdentity(patientKey, rendering.fdid()));
        return List.copyOf(result);
    }

    /**
     * Captured records that are not exported, per ReadMe label (removed and patient-independent
     * eForms); empty when Reports was not selected.
     */
    public Map<String, Long> notExported(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (!captured(tables)) return Map.of();
        var counts = new EnumMap<Withheld, Long>(Withheld.class);
        for (var hidden : plan(lease, patient, tables, source, bounds, control).withheld())
            counts.merge(hidden.why(), 1L, Long::sum);
        var result = new LinkedHashMap<String, Long>();
        counts.forEach((why, count) -> result.put(why.label(), count));
        return java.util.Collections.unmodifiableMap(result);
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
        var plan = plan(lease, patient, tables, source, bounds, control, true);
        requireAuditCapacity(plan.groups(), bounds);
        // Records the chart does not list: every captured field accounted, nothing emitted.
        for (var hidden : plan.withheld()) {
            control.checkpoint();
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(
                            hidden.group().rows().stream()
                                    .map(CdsExportDataSummaryPlanner.Row::assessed)
                                    .toList(),
                            withheldAssessments(hidden),
                            1_000_000,
                            control::checkpoint);
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
        long emitted = 0;
        try {
            for (var group : plan.groups()) {
                control.checkpoint();
                control.authorizeCategory(patient.patient(), CdsCategory.REPORTS, false);
                var rendering = listed(group) ? plan.renderings().get(group.id()) : null;
                var assessments = new LinkedHashMap<>(assessments(group));
                var rows = new ArrayList<CdsExportCapturedTextRowDto>();
                for (var row : group.rows()) rows.add(row.assessed());
                String notes = NOTES;
                var summary = group;
                if (rendering != null && !rendering.rendered()) {
                    // The rendered form is unavailable: this summary says so, on the PDF and in
                    // Notes, with a fixed reason and never a patient value.
                    String note = unavailableNote(rendering.reason());
                    notes = NOTES + " " + note;
                    summary = group.withNotice(note);
                    rows.add(rendering.row());
                    assessments.putAll(unavailableAssessments(rendering));
                }
                new CdsExportNativeFieldAssessmentValidator()
                        .requireRepresentable(rows, assessments, 1_000_000, control::checkpoint);
                byte[] pdf =
                        renderings.getOrDefault(group.kind(), summaries).render(summary, plan.patient());
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
                String written = notes;
                xml.emit(
                        new CdsExportXmlComposer.Section(
                                identity, body -> write(group, relative, written, body)));
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
                if (rendering != null && rendering.rendered()) {
                    appendRendered(
                            group,
                            rendering,
                            patient,
                            patientKey,
                            source,
                            store,
                            captured,
                            attachments,
                            control,
                            xml,
                            output);
                    emitted++;
                }
            }
            captured.close();
            captures.authorizeSources(lease, patient, tables, control);
            complete = true;
            return emitted;
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (!complete) {
                try {
                    captured.close();
                } catch (IOException cleanup) {
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
     * The second Reports entry of a listed eForm: the captured rendered form, copied from the
     * authenticated capture straight into its encrypted attachment (never held in heap).
     */
    private void appendRendered(
            CdsExportDataSummaryPlanner.Group group,
            CdsExportEformRenderings.Rendering rendering,
            CdsExportCohortService.Patient patient,
            String patientKey,
            CdsExportPatientCaptureDto source,
            CdsExportStagingService store,
            CdsExportCapturedValues captured,
            List<CdsExportStagedFileDto> attachments,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        control.checkpoint();
        control.authorizeCategory(patient.patient(), CdsCategory.REPORTS, false);
        var entry = rendered(group, rendering);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(
                        List.of(group.root().assessed(), rendering.row()),
                        entry.fields(),
                        1_000_000,
                        control::checkpoint);
        var content = rendering.content();
        var staged =
                store.stage(
                        "REPORT_ATTACHMENT",
                        outputBytes -> {
                            try (var input =
                                    captured.open(
                                            content,
                                            control::checkpoint,
                                            () ->
                                                    control.authorizeCategory(
                                                            patient.patient(),
                                                            CdsCategory.REPORTS,
                                                            false))) {
                                input.transferTo(outputBytes);
                            }
                        });
        attachments.add(staged);
        if (staged.plaintextBytes() != content.bytes()
                || !staged.plaintextSha256().equals(content.sha256()))
            throw new IOException("CDS_EXPORT_EFORM_RENDERING_CAPTURE_INVALID");
        var identity = renderedIdentity(patientKey, rendering.fdid());
        String relative =
                "Reports/"
                        + CdsImportKeys.hash(
                                "cds-export-eform-rendering-file-v1", patientKey, rendering.fdid())
                        + ".pdf";
        output.attachment(identity, new CdsExportPackageMemberDto(relative, staged));
        xml.emit(
                new CdsExportXmlComposer.Section(
                        identity, body -> writeRendered(group, entry, relative, body)));
        control.authorizeCategory(patient.patient(), CdsCategory.REPORTS, false);
        output.accept(
                new CdsExportClinicalOccurrenceService.Receipt(
                        identity,
                        source.manifest().plaintextSha256(),
                        CdsExportEformRenderingSourceReader.TABLE,
                        rendering.fdid(),
                        null,
                        entry.fields(),
                        Set.of(),
                        Set.of()));
    }

    /** The data summary's note when the eForm's rendered form is unavailable; fixed text only. */
    static String unavailableNote(CdsExportEformRenderService.Reason reason) {
        return "The eForm as CARLOS displays it could not be rendered for this export ("
                + Objects.requireNonNull(reason).description()
                + "), so this data summary is its only Report.";
    }

    /** Every captured field of a record that is not exported, under its {@link Withheld} rule. */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> withheldAssessments(
            Hidden hidden) {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var row : hidden.group().rows())
            row.values()
                    .forEach(
                            (column, value) -> {
                                if (result.putIfAbsent(
                                                new CdsExportNativeFieldKeyDto(
                                                        row.table(), row.recordKey(), column),
                                                new CdsExportFieldAssessmentDto(
                                                        presence(value),
                                                        Outcome.OPERATIONAL,
                                                        "",
                                                        hidden.why().rule()))
                                        != null)
                                    throw new IllegalStateException(
                                            "CDS_EXPORT_DATA_SUMMARY_FIELD_DUPLICATE");
                            });
        return java.util.Collections.unmodifiableMap(result);
    }

    /** The captured outcome of an unavailable render: its reason is the summary's note. */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> unavailableAssessments(
            CdsExportEformRenderings.Rendering rendering) {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var fields = new CdsExportNativeFieldAssessments(rendering.row(), result);
        for (String column : rendering.row().fields().keySet())
            if (column.equals("reason"))
                fields.set(column, Outcome.MAPPED, "Reports/Notes", "CARLOS_EFORM_RENDER_UNAVAILABLE_NOTE");
            else fields.operational(column, "CARLOS_EFORM_RENDER_OUTCOME");
        return result;
    }

    /** What the rendered-form entry of one listed eForm says, and the account of every field it uses. */
    record RenderedEntry(
            String subClass,
            String notes,
            String authorFirstName,
            String authorLastName,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        RenderedEntry {
            fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        @Override
        public String toString() {
            return "CdsExportDataSummaryOccurrenceService.RenderedEntry[protected]";
        }
    }

    /**
     * The rendered entry's metadata, taken from the eForm row as the chart lists it: SubClass is
     * the form name (when it fits the 60-character SubClass), EventDateTime the form's date and
     * time, SourceAuthorPhysician the provider who saved it, and Notes add the "additional
     * information" (subject) the list shows. Every field of the eForm row and of its captured
     * render outcome is accounted: a value the entry carries is MAPPED to its element, the rest
     * are OPERATIONAL because the eForm's data summary carries them.
     */
    static RenderedEntry rendered(
            CdsExportDataSummaryPlanner.Group group, CdsExportEformRenderings.Rendering rendering) {
        var root = group.root();
        if (!listed(group) || !EFORM_TABLE.equals(root.table()) || !group.id().equals(rendering.fdid())
                || !rendering.rendered())
            throw new IllegalStateException("CDS_EXPORT_EFORM_RENDERING_SCOPE_INVALID");
        String name = root.text("form_name");
        boolean named =
                name != null && !name.isBlank() && CdsExportNativeFieldAssessments.legal(name, 60);
        String subject = root.text("subject");
        boolean described =
                subject != null
                        && !subject.isBlank()
                        && CdsExportNativeFieldAssessments.legal(subject, 4000);
        String first = rendering.authorFirstName(), last = rendering.authorLastName();
        boolean authored =
                first != null
                        && !first.isBlank()
                        && last != null
                        && !last.isBlank()
                        && CdsExportNativeFieldAssessments.legal(first, 50)
                        && CdsExportNativeFieldAssessments.legal(last, 50);
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        String elsewhere = "CARLOS_EFORM_VALUE_IN_DATA_SUMMARY";
        root.values()
                .forEach(
                        (column, value) -> {
                            String destination, rule;
                            if (column.equals("form_data")) {
                                destination = "Reports/FilePath";
                                rule = "CARLOS_EFORM_RENDERED_FORM";
                            } else if (column.equals("form_name") && named) {
                                destination = "Reports/SubClass";
                                rule = "CARLOS_EFORM_FORM_NAME";
                            } else if (column.equals("subject") && described) {
                                destination = "Reports/Notes";
                                rule = "CARLOS_EFORM_ADDITIONAL_INFORMATION";
                            } else if ((column.equals("form_date") || column.equals("form_time"))
                                    && group.event() != null) {
                                destination = "Reports/EventDateTime";
                                rule = "CARLOS_EFORM_FORM_DATE";
                            } else if (column.equals("form_provider") && authored) {
                                destination = "Reports/SourceAuthorPhysician";
                                rule = "CARLOS_EFORM_AUTHOR";
                            } else {
                                destination = "";
                                rule = elsewhere;
                            }
                            fields.put(
                                    new CdsExportNativeFieldKeyDto(
                                            root.table(), root.recordKey(), column),
                                    new CdsExportFieldAssessmentDto(
                                            presence(value),
                                            destination.isEmpty() ? Outcome.OPERATIONAL : Outcome.MAPPED,
                                            destination,
                                            rule));
                        });
        var outcome = new CdsExportNativeFieldAssessments(rendering.row(), fields);
        for (String column : rendering.row().fields().keySet())
            switch (column) {
                case "content" ->
                        outcome.set(column, Outcome.MAPPED, "Reports/FilePath", "CARLOS_EFORM_RENDERED_FORM");
                case "author_first_name", "author_last_name" -> {
                    if (authored)
                        outcome.set(
                                column,
                                Outcome.MAPPED,
                                "Reports/SourceAuthorPhysician/AuthorName",
                                "CARLOS_EFORM_AUTHOR");
                    else outcome.operational(column, "CARLOS_EFORM_AUTHOR_NOT_REPRESENTABLE");
                }
                default -> outcome.operational(column, "CARLOS_EFORM_RENDER_OUTCOME");
            }
        return new RenderedEntry(
                named ? name : group.kind().label(),
                RENDERED_NOTES + (described ? " Additional information: " + subject : ""),
                authored ? first : null,
                authored ? last : null,
                fields);
    }

    /** The rendered-form entry in CDS 5.2 schema order. */
    static void writeRendered(
            CdsExportDataSummaryPlanner.Group group,
            RenderedEntry entry,
            String relative,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "Media", "Download");
        xml.element("cds", "Format", "Binary");
        xml.element("cds", "FileExtensionAndVersion", "pdf");
        xml.element("cds", "FilePath", relative);
        xml.element("cds", "Class", group.kind().reportClass());
        xml.element("cds", "SubClass", entry.subClass());
        if (group.event() != null) {
            xml.start("cds", "EventDateTime");
            CdsExportNativeHistoryXmlWriter.dateContents(xml, group.event());
            xml.end();
        }
        if (entry.authorFirstName() != null) {
            xml.start("cds", "SourceAuthorPhysician");
            xml.start("cds", "AuthorName");
            xml.element("cds_dt", "FirstName", entry.authorFirstName());
            xml.element("cds_dt", "LastName", entry.authorLastName());
            xml.end();
            xml.end();
        }
        xml.element("cds", "Notes", entry.notes());
    }

    /** The identity of a listed eForm's rendered-form entry, distinct from its data summary's. */
    static CdsExportIdentityDto renderedIdentity(String patientKey, String fdid) {
        return new CdsExportIdentityDto(
                patientKey,
                CdsCategory.REPORTS,
                CdsImportKeys.hash(
                        "carlos-native-occurrence-v1", CdsExportEformRenderingSourceReader.TABLE, fdid));
    }

    /**
     * The data summaries' share of the patient's reconciliation audit, whose size is bounded per
     * patient ({@code maxIndexBytes}) and stops the whole job when exceeded. The audit groups a
     * record's field decisions by table and assessment, so its size grows with rows and column
     * names, not with one line per field; a long encounter-form history fits. Only a chart whose
     * data summaries alone would still take most of the budget is held, with this code, instead of
     * stopping every patient's export.
     */
    static void requireAuditCapacity(
            List<CdsExportDataSummaryPlanner.Group> groups,
            CdsExportClinicalOccurrenceService.Bounds bounds)
            throws IOException {
        if (auditBytes(groups) > bounds.indexBytes() / 4 * 3)
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportMappingReviewException(AUDIT_LIMIT);
    }

    /** A generous upper bound of the audit bytes the data summaries' field decisions take. */
    static long auditBytes(List<CdsExportDataSummaryPlanner.Group> groups) {
        long bytes = 0;
        for (var group : groups) {
            var lines = new HashSet<String>();
            for (var row : group.rows()) {
                // A row's key repeats in each line (one per presence) that holds its columns.
                var presences = java.util.EnumSet.noneOf(Presence.class);
                for (var value : row.values().entrySet()) {
                    presences.add(presence(value.getValue()));
                    lines.add(row.table() + '\0' + presence(value.getValue()));
                    bytes =
                            Math.addExact(
                                    bytes, AUDIT_BYTES_PER_COLUMN + 2L * value.getKey().length());
                }
                bytes =
                        Math.addExact(
                                bytes,
                                presences.size()
                                        * (AUDIT_BYTES_PER_ROW + 2L * row.recordKey().length()));
            }
            bytes = Math.addExact(bytes, AUDIT_BYTES_PER_LINE * lines.size());
        }
        return bytes;
    }

    /** Upper bounds of one grouped audit line's fixed part, one row's key and one column name. */
    static final long AUDIT_BYTES_PER_LINE = 1024;

    static final long AUDIT_BYTES_PER_ROW = 16;

    static final long AUDIT_BYTES_PER_COLUMN = 8;

    static final String AUDIT_LIMIT = "CDS_EXPORT_DATA_SUMMARY_AUDIT_LIMIT";

    /** Reports content in CDS 5.2 schema order. */
    static void write(
            CdsExportDataSummaryPlanner.Group group,
            String relative,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        write(group, relative, NOTES, xml);
    }

    /** Reports content in CDS 5.2 schema order, with these Notes. */
    static void write(
            CdsExportDataSummaryPlanner.Group group,
            String relative,
            String notes,
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
        xml.element("cds", "Notes", notes);
    }

    private static Presence presence(CdsExportDataSummaryPlanner.Value value) {
        return switch (value.storage()) {
            case SQL_NULL -> Presence.SQL_NULL;
            case TEXT -> value.text().isEmpty() ? Presence.EMPTY : Presence.VALUE;
            case BINARY -> value.bytes() == 0 ? Presence.EMPTY : Presence.VALUE;
        };
    }

    /**
     * Every captured field of the record is carried, with its exact value, by the summary, except
     * a credential value the registry withholds (assessed OPERATIONAL, never printed).
     */
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
                                var presence = presence(value);
                                var assessment =
                                        presence == Presence.VALUE
                                                        && CdsExportDataSummaryRegistry.withheld(
                                                                row.table(), column)
                                                ? new CdsExportFieldAssessmentDto(
                                                        presence,
                                                        Outcome.OPERATIONAL,
                                                        "",
                                                        CdsExportDataSummaryRegistry.WITHHELD_RULE)
                                                : new CdsExportFieldAssessmentDto(
                                                        presence,
                                                        Outcome.MAPPED,
                                                        "Reports/FilePath",
                                                        "CARLOS_DATA_SUMMARY_EXACT_VALUE");
                                if (result.putIfAbsent(
                                                new CdsExportNativeFieldKeyDto(
                                                        row.table(), row.recordKey(), column),
                                                assessment)
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
