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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
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
import org.springframework.beans.factory.ObjectProvider;
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
 * summary by default, and a faithful rendering can replace it for one kind without changing
 * capture or emission.
 *
 * <p>A record a {@link Printer} prints (a current consultation request, the current Rourke 2017 or
 * 2020 revision) also gets the clinician's own printed document as a second Reports entry beside
 * its data summary, which stays the lossless accounting. The printed entry is made only when every
 * current identity may read the record's own security object, and a printed form that cannot be
 * produced never holds the patient: the data summary is exported with a note naming the reason
 * category. A printer is a function of the captured record, so the inventory and the emission
 * reach the same answer.
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

    /** The Notes of a printed form, followed by its own notes. */
    static final String PRINTED_NOTES =
            "CARLOS printed form: the attached PDF is this record as CARLOS EMR prints it, rendered"
                    + " from the stored record when it was exported. The CARLOS data summary of the"
                    + " same record lists every stored value.";

    /** The assessment rule of a stored value a printed form uses as its Reports metadata. */
    static final String PRINTED_METADATA_RULE = "CARLOS_PRINTED_FORM_METADATA";

    /** The reason category recorded when the exporting identities may not read the record. */
    static final String NOT_AUTHORIZED = "NOT_AUTHORIZED";

    /** The reason category of any other printing failure. */
    static final String RENDER_FAILED = "RENDER_FAILED";

    /**
     * The clinician's own printed document of a record, exported as its own Reports entry beside
     * the record's data summary.
     */
    interface Printer {
        /**
         * True when this printer prints the record. It must depend on the captured record alone,
         * so the inventory and the emission agree.
         */
        boolean prints(CdsExportDataSummaryPlanner.Group group);

        /** The security object the chart requires to read the record. */
        String readObject();

        /**
         * The printed record, rendered from the captured record and the reference values the
         * chart's own Print uses; a deterministic function of them.
         *
         * @throws PrintRefusal when the record cannot be printed completely, with its category
         */
        Printed print(CdsExportDataSummaryPlanner.Group group, Plan plan) throws IOException;
    }

    /**
     * A printed record: its PDF, its Reports SubClass, event date and author as the chart shows
     * them, the root-row columns that supplied that metadata (column to Reports element) and
     * sentences naming anything requested that it could not show (a signature, a logo).
     */
    record Printed(
            byte[] pdf,
            String subClass,
            CdsExportDateDto event,
            String author,
            Map<String, String> metadata,
            List<String> notes) {
        Printed {
            Objects.requireNonNull(pdf);
            Objects.requireNonNull(subClass);
            metadata = java.util.Collections.unmodifiableMap(new TreeMap<>(metadata));
            notes = List.copyOf(notes);
            if (subClass.isBlank() || subClass.length() > 60 || author != null && author.length() > 120)
                throw new IllegalArgumentException("CDS_EXPORT_PRINTED_FORM_METADATA_INVALID");
        }

        Printed withPdf(byte[] bytes) {
            return new Printed(bytes, subClass, event, author, metadata, notes);
        }

        @Override
        public String toString() {
            return "CdsExportDataSummaryOccurrenceService.Printed[protected]";
        }
    }

    /** A record that cannot be printed completely; the reason is a fixed category, never PHI. */
    static final class PrintRefusal extends IOException {
        private static final long serialVersionUID = 1L;
        private final String reason;

        PrintRefusal(String reason) {
            super(reason);
            if (reason == null || !reason.matches("[A-Z][A-Z_]{1,63}"))
                throw new IllegalArgumentException("CDS_EXPORT_PRINTED_FORM_REASON_INVALID");
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }

    /** The printed form of one record, or why it could not be made. */
    record PrintOutcome(Printed printed, String reason) {
        @Override
        public String toString() {
            return "CdsExportDataSummaryOccurrenceService.PrintOutcome[protected]";
        }
    }

    private final CdsExportNativeCaptureService captures;
    private final CdsExportStagingFactory stores;
    private final Renderer summaries;
    private final Map<CdsExportDataSummaryRegistry.Kind, Renderer> renderings;
    private final List<Printer> printers;

    @Autowired
    public CdsExportDataSummaryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores,
            CdsExportDataSummaryPdfService pdfs,
            ObjectProvider<Printer> printers) {
        this(
                captures,
                stores,
                Objects.requireNonNull(pdfs)::render,
                Map.of(),
                printers.orderedStream().toList());
    }

    CdsExportDataSummaryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores,
            Renderer summaries,
            Map<CdsExportDataSummaryRegistry.Kind, Renderer> renderings) {
        this(captures, stores, summaries, renderings, List.of());
    }

    CdsExportDataSummaryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores,
            Renderer summaries,
            Map<CdsExportDataSummaryRegistry.Kind, Renderer> renderings,
            List<Printer> printers) {
        this.captures = Objects.requireNonNull(captures);
        this.stores = Objects.requireNonNull(stores);
        this.summaries = Objects.requireNonNull(summaries);
        this.renderings = Map.copyOf(renderings);
        this.printers = List.copyOf(printers);
    }

    /**
     * The exported records, the captured records that are not exported, the patient header, the
     * patient's captured demographic row (the printed forms show the patient as the chart does)
     * and, when asked for, each listed eForm's captured render outcome by eForm identity.
     */
    record Plan(
            List<CdsExportDataSummaryPlanner.Group> groups,
            CdsExportDataSummaryHtmlComposer.Patient patient,
            List<Hidden> withheld,
            Map<String, CdsExportEformRenderings.Rendering> renderings,
            Map<String, String> demographic) {
        Plan {
            groups = List.copyOf(groups);
            withheld = List.copyOf(withheld);
            renderings = java.util.Collections.unmodifiableMap(new TreeMap<>(renderings));
            demographic = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(demographic));
        }

        Plan(
                List<CdsExportDataSummaryPlanner.Group> groups,
                CdsExportDataSummaryHtmlComposer.Patient patient) {
            this(groups, patient, List.of(), Map.of(), Map.of());
        }

        Plan(
                List<CdsExportDataSummaryPlanner.Group> groups,
                CdsExportDataSummaryHtmlComposer.Patient patient,
                Map<String, String> demographic) {
            this(groups, patient, List.of(), Map.of(), demographic);
        }

        Plan(
                List<CdsExportDataSummaryPlanner.Group> groups,
                CdsExportDataSummaryHtmlComposer.Patient patient,
                List<Hidden> withheld,
                Map<String, CdsExportEformRenderings.Rendering> renderings) {
            this(groups, patient, withheld, renderings, Map.of());
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
                renderings,
                read.demographic());
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
        for (var group : plan.groups()) {
            control.checkpoint();
            result.add(identity(patientKey, group));
            // The printed form is expected only when it can be printed; the emission prints the
            // same captured record again and reaches the same answer.
            var outcome = printed(group, plan, patient.patient(), control);
            if (outcome != null && outcome.printed() != null) {
                result.add(printedIdentity(patientKey, group));
                java.util.Arrays.fill(outcome.printed().pdf(), (byte) 0);
            }
        }
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
                // The clinician's printed form of the record, when it has one; the summary says
                // whether it was produced.
                var outcome = printed(group, plan, patient.patient(), control);
                String notes = notes(group, outcome);
                var summary = group;
                if (rendering != null && !rendering.rendered()) {
                    // The rendered form is unavailable: this summary says so, on the PDF and in
                    // Notes, with a fixed reason and never a patient value.
                    String note = unavailableNote(rendering.reason());
                    notes = notes + " " + note;
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
                if (outcome != null && outcome.printed() != null) {
                    emitPrinted(
                            patientKey, group, outcome.printed(), source, store, attachments, xml,
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

    /**
     * The printed form of a record a printer prints: null when no printer prints it, otherwise
     * the printed PDF (made reproducible) or the reason category it could not be made. Whether
     * the exporting identities may read the record is checked afresh each time.
     */
    PrintOutcome printed(
            CdsExportDataSummaryPlanner.Group group,
            Plan plan,
            int patient,
            CdsExportRunControl control) {
        Printer printer = null;
        for (var candidate : printers)
            if (candidate.prints(group)) {
                if (printer != null)
                    throw new IllegalStateException("CDS_EXPORT_PRINTED_FORM_PRINTER_AMBIGUOUS");
                printer = candidate;
            }
        if (printer == null) return null;
        if (!control.recordReadable(patient, printer.readObject()))
            return new PrintOutcome(null, NOT_AUTHORIZED);
        try {
            var printed = printer.print(group, plan);
            byte[] pdf =
                    CdsExportPrintedPdf.reproducible(
                            printed.pdf(),
                            CdsImportKeys.hash(
                                    "cds-export-printed-form-pdf-v1", group.table(), group.id()));
            CdsExportDataSummaryPdfService.requirePdf(pdf);
            return new PrintOutcome(printed.withPdf(pdf), null);
        } catch (PrintRefusal refused) {
            return new PrintOutcome(null, refused.reason());
        } catch (SecurityException denied) {
            throw denied;
        } catch (IOException | RuntimeException failed) {
            // The data summary still carries every stored value; only the category is recorded.
            return new PrintOutcome(null, RENDER_FAILED);
        }
    }

    /** The data summary's Notes: the statement, the revision and what became of its printed form. */
    static String notes(CdsExportDataSummaryPlanner.Group group, PrintOutcome outcome) {
        var notes = new StringBuilder(NOTES);
        String revision = group.revisionLabel();
        if (!revision.isEmpty()) notes.append(' ').append(revision).append('.');
        if (outcome != null && outcome.printed() != null) {
            notes.append(" The record as CARLOS EMR prints it is exported as a separate Reports entry (")
                    .append(outcome.printed().subClass())
                    .append(").");
            for (String note : outcome.printed().notes()) notes.append(' ').append(note);
        } else if (outcome != null)
            notes.append(" The record as CARLOS EMR prints it could not be produced (")
                    .append(outcome.reason())
                    .append("); this summary carries every stored value.");
        return notes.toString();
    }

    /** Stages and emits the printed form of a record as its own Reports entry and receipt. */
    private static void emitPrinted(
            String patientKey,
            CdsExportDataSummaryPlanner.Group group,
            Printed printed,
            CdsExportPatientCaptureDto source,
            CdsExportStagingService store,
            List<CdsExportStagedFileDto> attachments,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        byte[] pdf = printed.pdf();
        var staged = store.stage("REPORT_ATTACHMENT", outputBytes -> outputBytes.write(pdf));
        attachments.add(staged);
        java.util.Arrays.fill(pdf, (byte) 0);
        var identity = printedIdentity(patientKey, group);
        String relative =
                "Reports/"
                        + CdsImportKeys.hash(
                                "cds-export-printed-form-file-v1",
                                patientKey,
                                group.table(),
                                group.id())
                        + ".pdf";
        output.attachment(identity, new CdsExportPackageMemberDto(relative, staged));
        xml.emit(
                new CdsExportXmlComposer.Section(
                        identity, body -> writePrinted(group, printed, relative, body)));
        output.accept(
                new CdsExportClinicalOccurrenceService.Receipt(
                        identity,
                        source.manifest().plaintextSha256(),
                        group.table(),
                        group.id(),
                        null,
                        printedAssessments(group, printed),
                        Set.of(),
                        Set.of()));
    }

    /**
     * The printed form's own field decisions: each stored value of the record's root row that
     * supplied its Reports metadata, mapped to that element. Every field of the record stays
     * accounted by the data summary as well.
     */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> printedAssessments(
            CdsExportDataSummaryPlanner.Group group, Printed printed) {
        var root = group.rows().getFirst();
        var result = new TreeMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>(
                java.util.Comparator.comparing(CdsExportNativeFieldKeyDto::column));
        printed.metadata()
                .forEach(
                        (column, destination) -> {
                            var value = root.values().get(column);
                            if (value == null)
                                throw new IllegalStateException(
                                        "CDS_EXPORT_PRINTED_FORM_METADATA_INVALID");
                            result.put(
                                    new CdsExportNativeFieldKeyDto(
                                            root.table(), root.recordKey(), column),
                                    new CdsExportFieldAssessmentDto(
                                            presence(value),
                                            Outcome.MAPPED,
                                            destination,
                                            PRINTED_METADATA_RULE));
                        });
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    /** The printed form's Reports content in CDS 5.2 schema order. */
    static void writePrinted(
            CdsExportDataSummaryPlanner.Group group,
            Printed printed,
            String relative,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "Media", "Download");
        xml.element("cds", "Format", "Binary");
        xml.element("cds", "FileExtensionAndVersion", "pdf");
        xml.element("cds", "FilePath", relative);
        xml.element("cds", "Class", group.kind().reportClass());
        xml.element("cds", "SubClass", printed.subClass());
        if (printed.event() != null) {
            xml.start("cds", "EventDateTime");
            CdsExportNativeHistoryXmlWriter.dateContents(xml, printed.event());
            xml.end();
        }
        if (printed.author() != null && !printed.author().isBlank()) {
            xml.start("cds", "SourceAuthorPhysician");
            xml.element("cds", "AuthorFreeText", printed.author());
            xml.end();
        }
        var notes = new StringBuilder(PRINTED_NOTES);
        for (String note : printed.notes()) notes.append(' ').append(note);
        xml.element("cds", "Notes", notes.toString());
    }

    static CdsExportIdentityDto printedIdentity(
            String patientKey, CdsExportDataSummaryPlanner.Group group) {
        return new CdsExportIdentityDto(
                patientKey,
                CdsCategory.REPORTS,
                CdsImportKeys.hash("carlos-printed-form-occurrence-v1", group.table(), group.id()));
    }

    /** Reports content in CDS 5.2 schema order. */
    static void write(
            CdsExportDataSummaryPlanner.Group group,
            String relative,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        write(group, relative, notes(group, null), xml);
    }

    /** Reports content in CDS 5.2 schema order, with the record's Notes. */
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
