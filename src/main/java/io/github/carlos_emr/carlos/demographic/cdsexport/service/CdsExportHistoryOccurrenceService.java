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
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Emits current native and imported history through one occurrence boundary. Every revision,
 * linked issue and retained source field has a disposition before private output can succeed.
 * PastHealth also carries the patient's native screenings (non-immunization preventions), as
 * the legacy exporter did; they are emitted at the same wire position after the notes.
 */
@Service
public class CdsExportHistoryOccurrenceService {
    record Occurrence(
            CdsCategory category,
            String address,
            long noteId,
            List<Long> revisions,
            String issueId) {
        Occurrence {
            revisions = List.copyOf(revisions);
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

    /** Without a screening assembler; a capture that holds screening rows is then refused. */
    public CdsExportHistoryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNoteRevisionInventoryService revisions,
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification) {
        this(captures, witnesses, revisions, values, verification, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportHistoryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNoteRevisionInventoryService revisions,
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportScreeningOccurrenceService screenings) {
        this.captures = Objects.requireNonNull(captures);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.revisions = Objects.requireNonNull(revisions);
        this.values = Objects.requireNonNull(values);
        this.verification = Objects.requireNonNull(verification);
        this.screenings = screenings;
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
                                    if (requiresNativeContext(occurrence, context)) {
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
                                                inventory,
                                                context,
                                                index,
                                                checked,
                                                retained,
                                                streams,
                                                occurrences,
                                                providers,
                                                zone,
                                                control,
                                                emitter);
                                try {
                                    work.use(session);
                                    if (session.failed || !session.emitted.equals(selected))
                                        throw new IOException(
                                                "CDS_EXPORT_HISTORY_SOURCE_WORK_INCOMPLETE");
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
        private final CdsExportNoteRevisionPlanner.Result inventory;
        private final CdsExportHistoryCurrentContext context;
        private final CdsExportRetainedIndexDto index;
        private final CdsExportRetainedReceiptVerificationDto checked;
        private final List<CdsExportRetainedFieldDto> retained;
        private final CdsExportRetainedSectionWriter.Values streams;
        private final List<Occurrence> occurrences;
        private final Map<String, CdsExportCapturedTextRowDto> providers;
        private final java.time.ZoneId zone;
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
                CdsExportNoteRevisionPlanner.Result inventory,
                CdsExportHistoryCurrentContext context,
                CdsExportRetainedIndexDto index,
                CdsExportRetainedReceiptVerificationDto checked,
                List<CdsExportRetainedFieldDto> retained,
                CdsExportRetainedSectionWriter.Values streams,
                List<Occurrence> occurrences,
                Map<String, CdsExportCapturedTextRowDto> providers,
                java.time.ZoneId zone,
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
            this.inventory = inventory;
            this.context = context;
            this.index = index;
            this.checked = checked;
            this.retained = List.copyOf(retained);
            this.streams = streams;
            this.occurrences = occurrences;
            this.providers = providers;
            this.zone = zone;
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
                for (var occurrence : occurrences)
                    if (occurrence.category() == category) {
                        control.checkpoint();
                        streams.requireActive();
                        var access = context.scope(occurrence.noteId());
                        if (occurrence.address() == null) {
                            var current = new ArrayList<CdsExportHistoryFieldReconciler.Current>();
                            for (long id : occurrence.revisions()) {
                                control.authorizeNote(category, context.scope(id));
                                current.add(context.note(id));
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
                                                        zone);
                            } else {
                                var issue =
                                        context.original("casemgmt_issue", occurrence.issueId());
                                if (issue == null) invalid();
                                var definition =
                                        context.original(
                                                "issue", issue.value().fields().get("issue_id"));
                                mapped =
                                        new CdsExportNativeHistoryMapper()
                                                .mapIssue(patient.patient(), issue, definition);
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
                        if (requiresNativeContext(occurrence, context)) {
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
                                                    zone);
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

        @Override
        public String toString() {
            return "CdsExportHistorySource[protected]";
        }
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
        var issueIds = new HashSet<String>();
        var definitions = new HashSet<String>();
        for (var chain : inventory.chains()) {
            checkpoint.run();
            var parents = new ArrayList<Occurrence>();
            for (long id : chain.revisions()) {
                var parent = byNote.remove(id);
                if (parent != null) parents.add(parent);
            }
            if (parents.size() > 1) ambiguous();
            var parent = parents.isEmpty() ? null : parents.getFirst();
            var occurrence =
                    new Occurrence(
                            parent == null
                                    ? CdsExportNativeHistoryMapper.category(
                                            context.note(chain.currentNoteId()))
                                    : parent.category(),
                            parent == null ? null : parent.address(),
                            chain.currentNoteId(),
                            chain.revisions());
            for (long revision : chain.revisions()) {
                var note = context.note(revision);
                for (var link : note.links()) {
                    String id = link.fields().get("id");
                    issueIds.add(id);
                    var issue = note.patientIssues().get(id);
                    if (issue == null) invalid();
                    definitions.add(issue.value().fields().get("issue_id"));
                }
            }
            result.add(occurrence);
        }
        if (!byNote.isEmpty()) review();
        for (String id : context.patientIssueIds()) {
            var issue = context.original("casemgmt_issue", id);
            var definition = context.original("issue", issue.value().fields().get("issue_id"));
            if (definition == null) invalid();
            if (issueIds.contains(id) && CdsExportNativeHistoryMapper.cppDefinition(definition))
                continue;
            definitions.add(definition.value().fields().get("issue_id"));
            result.add(
                    new Occurrence(
                            CdsExportNativeHistoryMapper.issueCategory(definition),
                            null,
                            0,
                            List.of(),
                            id));
        }
        if (!definitions.equals(context.definitionIds())) review();
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
            Occurrence occurrence, CdsExportHistoryCurrentContext context) throws IOException {
        if (occurrence.issueId() != null) return false;
        if (occurrence.address() == null || occurrence.revisions().size() > 1) return true;
        // A single imported revision can acquire archive, signature or encounter context too.
        // Route it through the same qualified native carrier used for complete revision chains.
        var fields = context.note(occurrence.noteId()).note().value().fields();
        for (String column :
                List.of(
                        "archived",
                        "signed",
                        "signing_provider_no",
                        "history",
                        "encounter_type",
                        "billing_code",
                        "appointmentNo",
                        "hourOfEncounterTime",
                        "minuteOfEncounterTime",
                        "hourOfEncTransportationTime",
                        "minuteOfEncTransportationTime")) {
            String value = fields.get(column);
            if (value != null && !value.isEmpty() && !value.equals("0")) return true;
        }
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
