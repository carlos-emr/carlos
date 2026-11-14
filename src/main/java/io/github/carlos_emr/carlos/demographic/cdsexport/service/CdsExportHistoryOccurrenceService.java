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
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Emits current native and imported history through one occurrence boundary. Every revision,
 * linked issue and retained source field has a disposition before private output can succeed.
 * PastHealth also carries the patient's native screenings (non-immunization preventions), as
 * the legacy exporter did; they are emitted at the same wire position after the notes.
 * ProblemList likewise carries the patient's Disease Registry entries and coded episodes after
 * the CPP Concerns items ({@link CdsExportProblemRegistryOccurrenceService}).
 */
@Service
public class CdsExportHistoryOccurrenceService {
    /**
     * One wire occurrence. For a note chain, {@code revisions} are its note revisions in order; for
     * a standalone patient issue ({@code issueId} set), they are the note revisions linked to that
     * issue, whose live access is rechecked with it. {@code sourceCategory} is set only for an
     * imported item a clinician re-filed into another CPP section: the occurrence is the item's
     * current section and the import's own section is where its retained elements came from.
     */
    record Occurrence(
            CdsCategory category,
            String address,
            long noteId,
            List<Long> revisions,
            String issueId,
            CdsCategory sourceCategory) {
        Occurrence {
            revisions = List.copyOf(revisions);
            if (sourceCategory != null && (address == null || sourceCategory == category))
                throw new IllegalArgumentException("CDS_EXPORT_HISTORY_REFILED_OCCURRENCE_INVALID");
        }

        Occurrence(
                CdsCategory category,
                String address,
                long noteId,
                List<Long> revisions,
                String issueId) {
            this(category, address, noteId, revisions, issueId, null);
        }

        Occurrence(CdsCategory category, String address, long noteId) {
            this(category, address, noteId, List.of(noteId), null);
        }

        Occurrence(CdsCategory category, String address, long noteId, List<Long> revisions) {
            this(category, address, noteId, revisions, null);
        }

        String table() {
            return issueId == null ? "casemgmt_note" : "casemgmt_issue";
        }

        String nativeId() {
            return issueId == null ? Long.toString(noteId) : issueId;
        }

        @Override
        public String toString() {
            return "CdsExportHistoryOccurrence[protected]";
        }
    }

    private final CdsExportNativeCaptureService captures;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNoteRevisionInventoryService revisions;
    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportScreeningOccurrenceService screenings;
    private final CdsExportProblemRegistryOccurrenceService registries;

    /** Without a screening assembler; a capture that holds screening rows is then refused. */
    public CdsExportHistoryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNoteRevisionInventoryService revisions,
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification) {
        this(captures, witnesses, revisions, values, verification, null);
    }

    /** Without a registry assembler; a capture that holds registry or episode rows is then refused. */
    public CdsExportHistoryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNoteRevisionInventoryService revisions,
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportScreeningOccurrenceService screenings) {
        this(captures, witnesses, revisions, values, verification, screenings, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportHistoryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNoteRevisionInventoryService revisions,
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportScreeningOccurrenceService screenings,
            CdsExportProblemRegistryOccurrenceService registries) {
        this.captures = Objects.requireNonNull(captures);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.revisions = Objects.requireNonNull(revisions);
        this.values = Objects.requireNonNull(values);
        this.verification = Objects.requireNonNull(verification);
        this.screenings = screenings;
        this.registries = registries;
    }

    @FunctionalInterface
    public interface Work {
        void use(Source source) throws IOException;
    }

    /** One patient/generation session, shared across the seven wire positions without repeated source discovery. */
    public void withSource(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            Work work)
            throws IOException {
        withSource(
                CdsExportNoteSourceReader.CATEGORIES,
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

    public CdsExportClinicalOccurrenceService.Result append(
            CdsCategory category,
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
        withSource(
                Set.of(category),
                lease,
                patient,
                patientKey,
                requiredTables,
                source,
                payloadManifest,
                bounds,
                control,
                xml,
                session -> result[0] = session.append(category, output));
        return Objects.requireNonNull(result[0]);
    }

    private void withSource(
            Set<CdsCategory> expected,
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            Work work)
            throws IOException {
        Objects.requireNonNull(xml);
        Objects.requireNonNull(work);
        Objects.requireNonNull(bounds);
        Objects.requireNonNull(control);
        var selected = Set.copyOf(expected);
        xml.source(
                emitter -> {
                    patient.require(lease);
                    control.checkpoint();
                    if (selected.isEmpty()
                            || !CdsExportNoteSourceReader.CATEGORIES.containsAll(selected)
                            || !lease.document()
                                    .command()
                                    .categories()
                                    .containsAll(CdsExportNoteSourceReader.CATEGORIES)
                            || patientKey == null
                            || !patientKey.matches("[0-9a-f]{64}")) invalid();
                    var tables = Set.copyOf(requiredTables);
                    if (!tables.equals(source.capture().tables().keySet())) invalid();
                    // The source catalogue makes PastHealth capture the screening rows; a
                    // catalogued capture without them must not export PastHealth without them.
                    if (selected.contains(CdsCategory.PAST_HEALTH)
                            && tables.contains("clinical_source_catalogue")
                            && !tables.containsAll(CdsExportPreventionSources.SCREENING_REQUIRED))
                        invalid();
                    // Likewise the catalogue makes ProblemList capture the Disease Registry and
                    // episodes; a catalogued capture without them must not export ProblemList.
                    if (selected.contains(CdsCategory.PROBLEM_LIST)
                            && tables.contains("clinical_source_catalogue")
                            && !tables.containsAll(CdsExportProblemRegistrySources.TABLES))
                        invalid();
                    new CdsExportMergeScopeValidator(captures)
                            .requireUnmerged(
                                    lease,
                                    patient,
                                    tables,
                                    source,
                                    bounds.fields(),
                                    bounds.bytes(),
                                    control);
                    var inventory =
                            revisions.inventory(
                                    lease,
                                    patient,
                                    tables,
                                    source,
                                    bounds.fields(),
                                    bounds.bytes(),
                                    bounds.indexBytes(),
                                    control);
                    var context =
                            CdsExportHistoryCurrentContext.read(
                                    captures, witnesses, lease, patient, tables, source, bounds,
                                    control);
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
                                var occurrences =
                                        plan(index, inventory, context, control::checkpoint);
                                boolean nativeHistory = false;
                                for (var occurrence : occurrences)
                                    if (requiresNativeContext(occurrence, context, checked)) {
                                        nativeHistory = true;
                                        break;
                                    }
                                var providers =
                                        nativeHistory
                                                ? new CdsExportPublicProviderReader(captures)
                                                        .read(
                                                                lease, patient, tables, source,
                                                                bounds, control)
                                                : Map.<String, CdsExportCapturedTextRowDto>of();
                                var zone =
                                        nativeHistory
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
                                // The demographic forms' patient alert is one more
                                // AlertsAndSpecialNeeds occurrence (CdsExportPatientAlertMapper).
                                var alert =
                                        selected.contains(CdsCategory.ALERTS_AND_SPECIAL_NEEDS)
                                                ? CdsExportPatientAlertMapper.map(
                                                        patient.patient(),
                                                        new CdsExportDemographicSupportingReader(
                                                                        captures)
                                                                .custom(
                                                                        lease, patient, tables,
                                                                        source, bounds, control))
                                                : null;
                                var session =
                                        new Source(
                                                selected,
                                                lease,
                                                patient,
                                                patientKey,
                                                tables,
                                                source,
                                                bounds,
                                                screenings,
                                                registries,
                                                inventory,
                                                context,
                                                index,
                                                checked,
                                                retained,
                                                streams,
                                                occurrences,
                                                providers,
                                                zone,
                                                alert,
                                                control,
                                                emitter);
                                try {
                                    work.use(session);
                                    if (session.failed || !session.emitted.equals(selected))
                                        throw new IOException(
                                                "CDS_EXPORT_HISTORY_SOURCE_WORK_INCOMPLETE");
                                    session.requireEveryRowTaken();
                                    streams.requireActive();
                                    captures.authorizeCapturedSources(
                                            lease, patient, tables, source.capture(), control);
                                    patient.require(lease);
                                    control.checkpoint();
                                } finally {
                                    session.active = false;
                                }
                            });
                });
    }

    /** A callback-owned capability; it cannot be reused after source EOF/authorization or on another thread. */
    public static final class Source {
        private final Thread owner = Thread.currentThread();
        private final Set<CdsCategory> expected;
        private final Set<CdsCategory> emitted = EnumSet.noneOf(CdsCategory.class);
        private final CdsExportLeaseDto lease;
        private final CdsExportCohortService.Patient patient;
        private final String patientKey;
        private final Set<String> tables;
        private final CdsExportPatientCaptureDto source;
        private final CdsExportClinicalOccurrenceService.Bounds bounds;
        private final CdsExportScreeningOccurrenceService screenings;
        private final CdsExportProblemRegistryOccurrenceService registries;
        private final CdsExportNoteRevisionPlanner.Result inventory;
        /** The chart: held whole, or its facts when its rows are read window by window. */
        private final CdsExportHistoryCurrentContext chart;
        /**
         * Rows the windows of a chart read in windows took, over every category: notes,
         * extensions, issue links and note links.
         */
        private final long[] taken = new long[4];
        private final CdsExportRetainedIndexDto index;
        private final CdsExportRetainedReceiptVerificationDto checked;
        private final List<CdsExportRetainedFieldDto> retained;
        private final CdsExportRetainedSectionWriter.Values streams;
        private final List<Occurrence> occurrences;
        private final Map<String, CdsExportCapturedTextRowDto> providers;
        private final java.time.ZoneId zone;
        private final CdsExportPatientAlertMapper.Alert alert;
        private final CdsExportRunControl control;
        private final CdsExportXmlComposer.SectionEmitter xml;
        private volatile boolean active = true, failed;

        private Source(
                Set<CdsCategory> expected,
                CdsExportLeaseDto lease,
                CdsExportCohortService.Patient patient,
                String patientKey,
                Set<String> tables,
                CdsExportPatientCaptureDto source,
                CdsExportClinicalOccurrenceService.Bounds bounds,
                CdsExportScreeningOccurrenceService screenings,
                CdsExportProblemRegistryOccurrenceService registries,
                CdsExportNoteRevisionPlanner.Result inventory,
                CdsExportHistoryCurrentContext context,
                CdsExportRetainedIndexDto index,
                CdsExportRetainedReceiptVerificationDto checked,
                List<CdsExportRetainedFieldDto> retained,
                CdsExportRetainedSectionWriter.Values streams,
                List<Occurrence> occurrences,
                Map<String, CdsExportCapturedTextRowDto> providers,
                java.time.ZoneId zone,
                CdsExportPatientAlertMapper.Alert alert,
                CdsExportRunControl control,
                CdsExportXmlComposer.SectionEmitter xml) {
            this.expected = expected;
            this.lease = lease;
            this.patient = patient;
            this.patientKey = patientKey;
            this.tables = tables;
            this.source = source;
            this.bounds = bounds;
            this.screenings = screenings;
            this.registries = registries;
            this.inventory = inventory;
            this.chart = context;
            this.index = index;
            this.checked = checked;
            // The list is unmodifiable and decodes a field when it is read
            // (CdsExportRetainedFieldList): a copy would keep every field of the chart.
            this.retained = retained;
            this.streams = streams;
            this.occurrences = occurrences;
            this.providers = providers;
            this.zone = zone;
            this.alert = alert;
            this.control = control;
            this.xml = xml;
        }

        public CdsExportClinicalOccurrenceService.Result append(
                CdsCategory category, CdsExportClinicalOccurrenceService.Receipts output)
                throws IOException {
            try {
                if (!active
                        || failed
                        || Thread.currentThread() != owner
                        || !expected.contains(category)
                        || !emitted.add(category)) invalid();
                Objects.requireNonNull(output);
                streams.requireActive();
                patient.require(lease);
                control.checkpoint();
                var used = new HashSet<String>();
                long count = 0, fields = 0, nativeOnly = 0;
                // A chart held whole hands every occurrence the one context it always had. A
                // chart read in windows hands each run of consecutive occurrences the rows of
                // those occurrences, loaded and verified before the first of them is emitted.
                var windows = new Windows(category);
                for (var occurrence : occurrences)
                    if (occurrence.category() == category) {
                        control.checkpoint();
                        streams.requireActive();
                        var context = windows.rows(occurrence);
                        var access = context.scope(occurrence.noteId());
                        // Issue programs accepted by a fresh program check are checked again
                        // after emission, as note scopes are.
                        var granted = new LinkedHashSet<String>();
                        java.util.function.Predicate<String> readable =
                                program -> {
                                    boolean allowed =
                                            control.issueProgramReadable(
                                                    patient.patient(), category, program);
                                    if (allowed) granted.add(program);
                                    return allowed;
                                };
                        if (occurrence.sourceCategory() != null) {
                            refiled(occurrence, category, context, readable, granted, output);
                            count++;
                            fields =
                                    Math.addExact(
                                            fields,
                                            sourceFields(occurrence).size());
                            continue;
                        }
                        if (occurrence.address() == null) {
                            var current = new ArrayList<CdsExportHistoryFieldReconciler.Current>();
                            // For an issue, what its rule reads of the note revisions linked to
                            // it is their program, which each note's verified scope carries; the
                            // notes themselves are not loaded for it.
                            var linkedPrograms = new ArrayList<String>();
                            for (long id : occurrence.revisions()) {
                                var scope = context.scope(id);
                                if (scope == null) invalid();
                                control.authorizeNote(category, scope);
                                if (occurrence.issueId() == null) current.add(context.note(id));
                                else linkedPrograms.add(scope.program());
                            }
                            CdsExportNativeHistoryMapper.Result mapped;
                            if (occurrence.issueId() == null) {
                                mapped =
                                        new CdsExportNativeHistoryMapper()
                                                .map(
                                                        patient.patient(),
                                                        category,
                                                        current,
                                                        providers,
                                                        zone,
                                                        readable);
                            } else {
                                var issue =
                                        context.original("casemgmt_issue", occurrence.issueId());
                                if (issue == null) invalid();
                                var definition =
                                        context.original(
                                                "issue", issue.value().fields().get("issue_id"));
                                // For an issue, revisions() are the note revisions linked to it.
                                mapped =
                                        new CdsExportNativeHistoryMapper()
                                                .mapLinkedIssue(
                                                        patient.patient(),
                                                        issue,
                                                        definition,
                                                        linkedPrograms,
                                                        readable);
                            }
                            new CdsExportNativeFieldAssessmentValidator()
                                    .requireRepresentable(
                                            mapped.rows(),
                                            mapped.fields(),
                                            1_000_000,
                                            control::checkpoint);
                            var identity =
                                    new CdsExportIdentityDto(
                                            patientKey,
                                            category,
                                            CdsImportKeys.hash(
                                                    "carlos-native-occurrence-v1",
                                                    occurrence.table(),
                                                    occurrence.nativeId()));
                            control.authorizeCategory(patient.patient(), category, false);
                            xml.emit(
                                    new CdsExportXmlComposer.Section(
                                            identity,
                                            body ->
                                                    new CdsExportNativeHistoryXmlWriter()
                                                            .write(mapped.value(), body)));
                            for (long id : occurrence.revisions())
                                control.authorizeNote(category, context.scope(id));
                            reauthorizePrograms(control, patient.patient(), category, granted);
                            streams.requireActive();
                            output.accept(
                                    new CdsExportClinicalOccurrenceService.Receipt(
                                            identity,
                                            index.sourceManifestSha256(),
                                            occurrence.table(),
                                            occurrence.nativeId(),
                                            null,
                                            mapped.fields(),
                                            Set.of(),
                                            Set.of()));
                            control.authorizeCategory(patient.patient(), category, false);
                            nativeOnly++;
                            count++;
                            continue;
                        }
                        CdsExportHistoryFieldReconciler.Result decision;
                        CdsExportNativeHistoryMapper.Result nativeHistory = null;
                        if (requiresNativeContext(occurrence, context, checked)) {
                            var current = new ArrayList<CdsExportHistoryFieldReconciler.Current>();
                            for (long id : occurrence.revisions()) {
                                control.authorizeNote(category, context.scope(id));
                                current.add(context.note(id));
                            }
                            nativeHistory =
                                    new CdsExportNativeHistoryMapper()
                                            .map(
                                                    patient.patient(),
                                                    category,
                                                    current,
                                                    providers,
                                                    zone,
                                                    readable);
                        }
                        try {
                            decision =
                                    new CdsExportHistoryFieldReconciler()
                                            .reconcile(
                                                    patient.patient(),
                                                    category,
                                                    occurrence.address(),
                                                    index,
                                                    checked,
                                                    inventory,
                                                    access,
                                                    context,
                                                    retained,
                                                    streams,
                                                    control,
                                                    nativeHistory);
                        } catch (CdsExportHistoryFieldReconciler.MappingReview expectedReview) {
                            throw new CdsExportReviewException(
                                    CdsExportReviewException.Reason.UNMAPPED_DATA);
                        } catch (IOException sourceFailure) {
                            if ("CDS_EXPORT_HISTORY_SOURCE_DATE_REQUIRES_REVIEW"
                                    .equals(sourceFailure.getMessage())) review();
                            throw sourceFailure;
                        }
                        if (!decision.representable()) review();
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        nativeHistory == null
                                                ? assessedRows(context.note(occurrence.noteId()))
                                                : nativeHistory.rows(),
                                        decision.fields(),
                                        1_000_000,
                                        control::checkpoint);
                        var sourceFields =
                                new CdsExportRetainedOccurrenceFields()
                                        .fieldsFor(
                                                patient.patient(),
                                                category,
                                                occurrence.address(),
                                                index,
                                                checked,
                                                retained,
                                                control::checkpoint);
                        var prepared =
                                new CdsExportRetainedSectionWriter()
                                        .prepareChanges(
                                                sourceFields,
                                                streams,
                                                decision.changes(),
                                                control::checkpoint);
                        var accounted = new HashSet<>(prepared.retainedFields());
                        if (!Collections.disjoint(accounted, prepared.replacedFields())) invalid();
                        accounted.addAll(prepared.replacedFields());
                        if (!accounted.equals(decision.sourceFields())) invalid();
                        for (String id : accounted) if (!used.add(id)) invalid();
                        var identity =
                                new CdsExportIdentityDto(
                                        patientKey,
                                        category,
                                        CdsImportKeys.hash(
                                                "carlos-native-occurrence-v1",
                                                "casemgmt_note",
                                                Long.toString(occurrence.noteId())));
                        for (long id : occurrence.revisions())
                            control.authorizeNote(category, context.scope(id));
                        xml.emit(new CdsExportXmlComposer.Section(identity, prepared.content()));
                        streams.requireActive();
                        for (long id : occurrence.revisions())
                            control.authorizeNote(category, context.scope(id));
                        reauthorizePrograms(control, patient.patient(), category, granted);
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        identity,
                                        index.sourceManifestSha256(),
                                        "casemgmt_note",
                                        Long.toString(occurrence.noteId()),
                                        occurrence.address(),
                                        decision.fields(),
                                        prepared.retainedFields(),
                                        prepared.replacedFields()));
                        count++;
                        fields = Math.addExact(fields, accounted.size());
                    }
                windows.finish();
                if (category == CdsCategory.ALERTS_AND_SPECIAL_NEEDS && alert != null) {
                    var identity =
                            new CdsExportIdentityDto(
                                    patientKey,
                                    category,
                                    CdsImportKeys.hash(
                                            "carlos-native-occurrence-v1",
                                            CdsExportPatientAlertMapper.TABLE,
                                            Integer.toString(patient.patient())));
                    streams.requireActive();
                    control.authorizeCategory(patient.patient(), category, false);
                    xml.emit(
                            new CdsExportXmlComposer.Section(
                                    identity, body -> CdsExportPatientAlertMapper.write(alert, body)));
                    output.accept(
                            new CdsExportClinicalOccurrenceService.Receipt(
                                    identity,
                                    index.sourceManifestSha256(),
                                    CdsExportPatientAlertMapper.TABLE,
                                    Integer.toString(patient.patient()),
                                    null,
                                    CdsExportPatientAlertMapper.fields(alert),
                                    Set.of(),
                                    Set.of()));
                    control.authorizeCategory(patient.patient(), category, false);
                    count++;
                    nativeOnly++;
                }
                if (category == CdsCategory.PROBLEM_LIST
                        && !Collections.disjoint(tables, CdsExportProblemRegistrySources.TABLES)) {
                    if (registries == null) invalid();
                    long registered =
                            registries.append(
                                    lease,
                                    patient,
                                    patientKey,
                                    tables,
                                    source,
                                    index,
                                    bounds,
                                    control,
                                    xml,
                                    output);
                    count = Math.addExact(count, registered);
                    nativeOnly = Math.addExact(nativeOnly, registered);
                }
                if (category == CdsCategory.PAST_HEALTH
                        && !Collections.disjoint(
                                tables, CdsExportPreventionSources.SCREENING_TABLES)) {
                    if (screenings == null) invalid();
                    long screened =
                            screenings.append(
                                    lease,
                                    patient,
                                    patientKey,
                                    tables,
                                    source,
                                    index,
                                    bounds,
                                    control,
                                    xml,
                                    output);
                    count = Math.addExact(count, screened);
                    nativeOnly = Math.addExact(nativeOnly, screened);
                }
                // A re-filed imported item is emitted in its current section; its import's
                // section elements are carried there, so they are accounted in this one.
                for (var occurrence : occurrences)
                    if (occurrence.sourceCategory() == category)
                        for (var field : sourceFields(occurrence))
                            if (!used.add(field.id())) invalid();
                var expectedFields = new HashSet<String>();
                for (var field : retained)
                    if (field.category() == category) expectedFields.add(field.id());
                if (!used.equals(expectedFields)) invalid();
                streams.requireActive();
                patient.require(lease);
                control.checkpoint();
                return new CdsExportClinicalOccurrenceService.Result(
                        index.sourceManifestSha256(),
                        category,
                        count,
                        count - nativeOnly,
                        nativeOnly,
                        fields);
            } catch (IOException | RuntimeException | Error failure) {
                failed = true;
                throw failure;
            }
        }

        /** The retained source fields of a re-filed item, in its import's own section. */
        private List<CdsExportRetainedFieldDto> sourceFields(Occurrence occurrence)
                throws IOException {
            return new CdsExportRetainedOccurrenceFields()
                    .fieldsFor(
                            patient.patient(),
                            occurrence.sourceCategory(),
                            occurrence.address(),
                            index,
                            checked,
                            retained,
                            control::checkpoint);
        }

        /**
         * Emits an imported item a clinician re-filed into another CPP section (B5): every native
         * revision under its current section, as any native item, with the import's section
         * elements carried exactly in its native context
         * ({@link CdsExportHistoryFieldReconciler#refiledSource}). The retained source is read
         * through the same authenticated, note-authorized reader as an unmoved imported item, so
         * nothing of the original record is dropped and none of it is presented as current.
         */
        private void refiled(
                Occurrence occurrence,
                CdsCategory category,
                CdsExportHistoryCurrentContext context,
                java.util.function.Predicate<String> readable,
                Set<String> granted,
                CdsExportClinicalOccurrenceService.Receipts output)
                throws IOException {
            var current = new ArrayList<CdsExportHistoryFieldReconciler.Current>();
            for (long id : occurrence.revisions()) {
                control.authorizeNote(category, context.scope(id));
                current.add(context.note(id));
            }
            var references = index.references().get(occurrence.address());
            if (references == null) invalid();
            var notes =
                    references.stream().filter(ref -> ref.table().equals("casemgmt_note")).toList();
            if (notes.size() != 1) invalid();
            long imported;
            try {
                imported = Long.parseLong(notes.getFirst().id());
            } catch (NumberFormatException malformed) {
                invalid();
                return;
            }
            if (!occurrence.revisions().contains(imported)) invalid();
            var source =
                    new CdsExportHistoryRetainedSource()
                            .read(
                                    patient.patient(),
                                    occurrence.sourceCategory(),
                                    occurrence.address(),
                                    index,
                                    checked,
                                    retained,
                                    context.scope(imported),
                                    streams,
                                    control);
            var fields = sourceFields(occurrence);
            var ids = new HashSet<String>();
            for (var field : fields) ids.add(field.id());
            if (!ids.equals(source.sourceFields())) invalid();
            var carried =
                    CdsExportHistoryFieldReconciler.refiledSource(
                            occurrence.sourceCategory(),
                            fields,
                            source.dates(),
                            streams,
                            control::checkpoint);
            var mapped =
                    new CdsExportNativeHistoryMapper()
                            .map(
                                    patient.patient(),
                                    category,
                                    current,
                                    providers,
                                    zone,
                                    readable,
                                    carried);
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(
                            mapped.rows(), mapped.fields(), 1_000_000, control::checkpoint);
            var identity =
                    new CdsExportIdentityDto(
                            patientKey,
                            category,
                            CdsImportKeys.hash(
                                    "carlos-native-occurrence-v1",
                                    "casemgmt_note",
                                    Long.toString(occurrence.noteId())));
            control.authorizeCategory(patient.patient(), category, false);
            xml.emit(
                    new CdsExportXmlComposer.Section(
                            identity,
                            body -> new CdsExportNativeHistoryXmlWriter().write(mapped.value(), body)));
            streams.requireActive();
            for (long id : occurrence.revisions()) control.authorizeNote(category, context.scope(id));
            control.authorizeNote(occurrence.sourceCategory(), context.scope(imported));
            reauthorizePrograms(control, patient.patient(), category, granted);
            output.accept(
                    new CdsExportClinicalOccurrenceService.Receipt(
                            identity,
                            index.sourceManifestSha256(),
                            "casemgmt_note",
                            Long.toString(occurrence.noteId()),
                            occurrence.address(),
                            mapped.fields(),
                            Set.of(),
                            ids));
        }

        /**
         * The rows of one category's occurrences. For a chart read in windows, a window is a run
         * of consecutive occurrences in the order they are emitted, and it holds whole owners:
         * every revision of each note chain with its extensions, issue links and attached
         * records, the patient issue of each standalone issue, the patient issues and catalogue
         * entries their links name, and the rows the import unit of an imported item names. A
         * window that passes the window's bytes is read again with fewer occurrences before any
         * of them is emitted; one occurrence is never abandoned.
         *
         * <p>Nothing a window decides spans notes: which section a chain belongs to, and whether
         * a diagnosis stands alone, were decided by {@code plan} from the chart-wide facts
         * before the first window was read, so a chain or a diagnosis is emitted the same way
         * whichever window holds it.
         */
        private final class Windows {
            private final List<Occurrence> owners;
            private final CdsExportSectionWindowPlanner.Sizes sizes;
            private CdsExportHistoryCurrentContext loaded;
            /** Occurrences handed out, and the end of the window that is loaded. */
            private int next, end;

            private Windows(CdsCategory category) {
                if (!chart.windowed()) {
                    owners = List.of();
                    sizes = null;
                    return;
                }
                var selected = new ArrayList<Occurrence>();
                for (var occurrence : occurrences)
                    if (occurrence.category() == category) selected.add(occurrence);
                owners = selected;
                sizes = new CdsExportSectionWindowPlanner.Sizes(bounds.window());
            }

            private CdsExportHistoryCurrentContext rows(Occurrence occurrence) throws IOException {
                if (!chart.windowed()) return chart;
                if (next >= owners.size() || owners.get(next) != occurrence) invalid();
                if (next == end) {
                    int from = end;
                    // The emitted window is dropped before the next one is read, so a chart
                    // never holds two windows of rows.
                    loaded = null;
                    var window =
                            CdsExportSectionWindowPlanner.loadNext(
                                    sizes,
                                    owners.size() - from,
                                    (length, budget) -> load(from, length, budget));
                    loaded = window.value();
                    end = from + window.length();
                    long[] children = loaded.taken();
                    for (int i = from; i < end; i++)
                        if (owners.get(i).issueId() == null)
                            taken[0] = Math.addExact(taken[0], owners.get(i).revisions().size());
                    for (int i = 0; i < children.length; i++)
                        taken[i + 1] = Math.addExact(taken[i + 1], children[i]);
                }
                next++;
                return loaded;
            }

            private CdsExportHistoryCurrentContext load(
                    int from, int length, CdsExportSectionWindowPlanner.Budget budget)
                    throws IOException {
                var notes = new HashSet<Long>();
                var issues = new HashSet<String>();
                var references = new ArrayList<CdsExportRetainedIndexDto.Reference>();
                for (var owner : owners.subList(from, from + length)) {
                    if (owner.issueId() != null) {
                        issues.add(owner.issueId());
                        continue;
                    }
                    // Each note belongs to one chain and each chain is one occurrence.
                    for (long revision : owner.revisions()) if (!notes.add(revision)) invalid();
                    if (owner.address() != null)
                        references.addAll(
                                index.references().getOrDefault(owner.address(), List.of()));
                }
                return chart.window(notes, issues, references, budget);
            }

            /** Every occurrence of the category was handed the window that holds its rows. */
            private void finish() throws IOException {
                if (chart.windowed() && (next != owners.size() || end != owners.size())) invalid();
                loaded = null;
            }
        }

        /**
         * For a chart read in windows whose seven categories were all emitted: the windows took
         * every note, extension, issue link and note link the capture holds, each once. A row
         * whose note is in no window is taken by none and makes its count fall short.
         */
        private void requireEveryRowTaken() throws IOException {
            if (!chart.windowed() || !expected.equals(CdsExportNoteSourceReader.CATEGORIES))
                return;
            var captured = source.capture().tables();
            if (taken[0] != captured.get("casemgmt_note").rows()
                    || taken[1] != captured.get("casemgmt_note_ext").rows()
                    || taken[2] != captured.get("casemgmt_issue_notes").rows()
                    || taken[3]
                            != (captured.containsKey(CdsExportHistoryCurrentContext.NOTE_LINKS)
                                    ? captured.get(CdsExportHistoryCurrentContext.NOTE_LINKS).rows()
                                    : 0)) invalid();
        }

        @Override
        public String toString() {
            return "CdsExportHistorySource[protected]";
        }
    }

    private static void reauthorizePrograms(
            CdsExportRunControl control, int patient, CdsCategory category, Set<String> programs) {
        for (String program : programs)
            if (!control.issueProgramReadable(patient, category, program))
                throw new SecurityException("CDS_EXPORT_ACCESS_CHANGED");
    }

    /** Every captured note and every patient-issue relation must have an explicit disposition. */
    static List<Occurrence> plan(
            CdsExportRetainedIndexDto index,
            CdsExportNoteRevisionPlanner.Result inventory,
            CdsExportHistoryCurrentContext context,
            Runnable checkpoint)
            throws IOException {
        checkpoint.run();
        if (!inventory.sourceManifestSha256().equals(index.sourceManifestSha256())
                || !context.manifest().equals(index.sourceManifestSha256())
                || context.patient() != inventory.patient()) invalid();
        if (!inventory.qualifiedLineages()) review();
        var capturedNotes = new HashSet<Long>();
        for (var chain : inventory.chains()) capturedNotes.addAll(chain.revisions());
        if (!capturedNotes.equals(context.noteIds())) invalid();
        var byNote = new HashMap<Long, Occurrence>();
        for (var unit : index.tables().getOrDefault("cds_import_unit", Map.of()).values()) {
            String wire = unit.fields().get("category");
            // Import helper/preservation units are separate from the sixteen wire categories.
            if (CdsExportNoteSourceReader.CATEGORIES.stream()
                    .noneMatch(selected -> selected.wireName().equals(wire))) continue;
            CdsCategory category = CdsCategory.fromWireName(wire);
            String address = unit.fields().get("run_id") + ":" + unit.fields().get("unit_key");
            if (!index.appliedUnits().contains(address)) continue;
            var references = index.references().get(address);
            if (references == null) invalid();
            var notes =
                    references.stream().filter(ref -> ref.table().equals("casemgmt_note")).toList();
            if (notes.size() != 1) review();
            long id;
            try {
                id = Long.parseLong(notes.getFirst().id());
                if (id < 1 || !Long.toString(id).equals(notes.getFirst().id())) invalid();
            } catch (NumberFormatException malformed) {
                invalid();
                return List.of();
            }
            if (byNote.putIfAbsent(id, new Occurrence(category, address, id)) != null) ambiguous();
        }
        var result = new ArrayList<Occurrence>();
        var definitions = new HashSet<String>();
        // Patient issue id -> every captured note revision that links it, and their categories.
        var linkedRevisions = new HashMap<String, TreeSet<Long>>();
        var linkedCategories = new HashMap<String, EnumSet<CdsCategory>>();
        for (var chain : inventory.chains()) {
            checkpoint.run();
            var parents = new ArrayList<Occurrence>();
            for (long id : chain.revisions()) {
                var parent = byNote.remove(id);
                if (parent != null) parents.add(parent);
            }
            if (parents.size() > 1) ambiguous();
            var parent = parents.isEmpty() ? null : parents.getFirst();
            var current = context.category(chain.currentNoteId());
            // An imported item keeps its import's section unless a clinician re-filed it into
            // another CPP section (its category tag changed): it is then exported under its
            // current section, carrying the import's section elements (B5 re-filing).
            var occurrence =
                    new Occurrence(
                            current,
                            parent == null ? null : parent.address(),
                            chain.currentNoteId(),
                            chain.revisions(),
                            null,
                            parent == null || parent.category() == current
                                    ? null
                                    : parent.category());
            for (long revision : chain.revisions()) {
                for (String id : context.linkedIssues(revision)) {
                    linkedRevisions.computeIfAbsent(id, ignored -> new TreeSet<>()).add(revision);
                    linkedCategories
                            .computeIfAbsent(id, ignored -> EnumSet.noneOf(CdsCategory.class))
                            .add(occurrence.category());
                    String issue = context.definitionOf(id);
                    if (issue == null) invalid();
                    definitions.add(issue);
                }
            }
            result.add(occurrence);
        }
        if (!byNote.isEmpty()) review();
        for (String id : context.patientIssueIds()) {
            String definition = context.definitionOf(id);
            if (definition == null || !context.hasDefinition(definition)) invalid();
            var categories =
                    linkedCategories.getOrDefault(id, EnumSet.noneOf(CdsCategory.class));
            if (context.systemDefinition(definition)) {
                // A CPP tag or another system marker (OMeds, TicklerNote, eyeform*, PatientLog,
                // CurrentHistory, ...) files notes; it is not a diagnosis. Each linked note
                // carries it: as its CPP category, and with the complete issue and catalogue
                // facts in every revision's native context. The legacy exporter excluded these
                // and never exported a bare issue.
                if (!categories.isEmpty()) continue;
                // Without a note there is nothing clinical to carry and no honest wire home.
                throw new CdsExportMappingReviewException(
                        "CDS_EXPORT_NATIVE_SYSTEM_ISSUE_WITHOUT_NOTE_REQUIRES_REVIEW");
            }
            // A diagnosis linked to a CPP item is that item's coded diagnosis (FamilyHistory,
            // PastHealth, ProblemList) or part of its native issue context: a standalone problem
            // would write it twice or assert a relative's diagnosis as the patient's own. A
            // diagnosis that is unlinked, or linked only to encounter notes, is the patient's
            // own issue (the chart lists it with its own status) and ClinicalNotes has no coded
            // element, so it is exported once, as a ProblemList entry.
            if (categories.stream().anyMatch(category -> category != CdsCategory.CLINICAL_NOTES))
                continue;
            definitions.add(definition);
            result.add(
                    new Occurrence(
                            context.issueCategory(definition),
                            null,
                            0,
                            List.copyOf(linkedRevisions.getOrDefault(id, new TreeSet<>())),
                            id));
        }
        if (!context.definitionsAre(definitions)) review();
        result.sort(
                Comparator.comparing(Occurrence::category)
                        .thenComparing(Occurrence::table)
                        .thenComparing(Occurrence::nativeId));
        checkpoint.run();
        return List.copyOf(result);
    }

    private static List<CdsExportCapturedTextRowDto> assessedRows(
            CdsExportHistoryFieldReconciler.Current current) throws IOException {
        var rows = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        add(rows, current.note().value());
        for (var extension : current.extensions()) add(rows, extension.value());
        for (var link : current.noteLinks()) add(rows, link.value());
        for (var link : current.links()) {
            add(rows, link);
            var issue = current.patientIssues().get(link.fields().get("id"));
            if (issue == null) invalid();
            add(rows, issue.value());
            var definition = current.definitions().get(issue.value().fields().get("issue_id"));
            if (definition == null) invalid();
            add(rows, definition.value());
        }
        return List.copyOf(rows.values());
    }

    private static void add(
            Map<String, CdsExportCapturedTextRowDto> rows, CdsExportCapturedTextRowDto row)
            throws IOException {
        var prior = rows.putIfAbsent(row.table() + "/" + row.recordKey(), row);
        if (prior != null && !prior.equals(row)) invalid();
    }

    private static boolean requiresNativeContext(
            Occurrence occurrence,
            CdsExportHistoryCurrentContext context,
            CdsExportRetainedReceiptVerificationDto checked)
            throws IOException {
        if (occurrence.issueId() != null) return false;
        if (occurrence.address() == null
                || occurrence.sourceCategory() != null
                || occurrence.revisions().size() > 1) return true;
        // A flag of the patient issue that files the imported item (acute, certain, major,
        // resolved), changed in the chart's issue list with no new revision of the item: only
        // the native revision context carries that row. It was a hold
        // (CDS_EXPORT_HISTORY_ISSUE_EDIT_REQUIRES_RECONCILIATION).
        if (CdsExportHistoryFieldReconciler.issueContextChanged(
                checked.units().get(occurrence.address()))) return true;
        // What the note's own row and attached records decide is the same whether the chart
        // is held whole or read in windows (CdsExportHistoryCurrentContext.nativeContextNote).
        if (context.nativeContextNote(occurrence.noteId())) return true;
        return occurrence.category() == CdsCategory.PERSONAL_HISTORY;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_HISTORY_OCCURRENCE_SCOPE_INVALID");
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

    private static void ambiguous() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
    }
}
