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
 * Emits current imported history through the same occurrence boundary as other clinical
 * domains. Complete native/source closure and fresh per-note authorization precede success.
 * Native-only history and chains needing an explicit prior-revision audit carrier require
 * review; neither an old occurrence nor a chart placeholder substitutes for those facts.
 */
@Service
public class CdsExportHistoryOccurrenceService {
    record Occurrence(CdsCategory category, String address, long noteId) {
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

    public CdsExportHistoryOccurrenceService(
            CdsExportNativeCaptureService captures,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNoteRevisionInventoryService revisions,
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification) {
        this.captures = Objects.requireNonNull(captures);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.revisions = Objects.requireNonNull(revisions);
        this.values = Objects.requireNonNull(values);
        this.verification = Objects.requireNonNull(verification);
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
                                var session =
                                        new Source(
                                                selected,
                                                lease,
                                                patient,
                                                patientKey,
                                                inventory,
                                                context,
                                                index,
                                                checked,
                                                retained,
                                                streams,
                                                occurrences,
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
        private final CdsExportNoteRevisionPlanner.Result inventory;
        private final CdsExportHistoryCurrentContext context;
        private final CdsExportRetainedIndexDto index;
        private final CdsExportRetainedReceiptVerificationDto checked;
        private final List<CdsExportRetainedFieldDto> retained;
        private final CdsExportRetainedSectionWriter.Values streams;
        private final List<Occurrence> occurrences;
        private final CdsExportRunControl control;
        private final CdsExportXmlComposer.SectionEmitter xml;
        private volatile boolean active = true, failed;

        private Source(
                Set<CdsCategory> expected,
                CdsExportLeaseDto lease,
                CdsExportCohortService.Patient patient,
                String patientKey,
                CdsExportNoteRevisionPlanner.Result inventory,
                CdsExportHistoryCurrentContext context,
                CdsExportRetainedIndexDto index,
                CdsExportRetainedReceiptVerificationDto checked,
                List<CdsExportRetainedFieldDto> retained,
                CdsExportRetainedSectionWriter.Values streams,
                List<Occurrence> occurrences,
                CdsExportRunControl control,
                CdsExportXmlComposer.SectionEmitter xml) {
            this.expected = expected;
            this.lease = lease;
            this.patient = patient;
            this.patientKey = patientKey;
            this.inventory = inventory;
            this.context = context;
            this.index = index;
            this.checked = checked;
            this.retained = List.copyOf(retained);
            this.streams = streams;
            this.occurrences = occurrences;
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
                long count = 0, fields = 0;
                for (var occurrence : occurrences)
                    if (occurrence.category() == category) {
                        control.checkpoint();
                        streams.requireActive();
                        var access = context.scope(occurrence.noteId());
                        CdsExportHistoryFieldReconciler.Result decision;
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
                                                    control);
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
                                        assessedRows(context.note(occurrence.noteId())),
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
                        control.authorizeNote(category, access);
                        xml.emit(new CdsExportXmlComposer.Section(identity, prepared.content()));
                        streams.requireActive();
                        control.authorizeNote(category, access);
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
                var expectedFields = new HashSet<String>();
                for (var field : retained)
                    if (field.category() == category) expectedFields.add(field.id());
                if (!used.equals(expectedFields)) invalid();
                streams.requireActive();
                patient.require(lease);
                control.checkpoint();
                return new CdsExportClinicalOccurrenceService.Result(
                        index.sourceManifestSha256(), category, count, count, 0, fields);
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
            // The standard is a current clinical snapshot. Prior versions require a separate
            // qualified audit carrier before this service can claim their complete disposition.
            if (chain.revisions().size() != 1) review();
            var occurrence = byNote.remove(chain.currentNoteId());
            if (occurrence == null) review();
            var note = context.note(chain.currentNoteId());
            for (var link : note.links()) {
                String id = link.fields().get("id");
                issueIds.add(id);
                var issue = note.patientIssues().get(id);
                if (issue == null) invalid();
                definitions.add(issue.value().fields().get("issue_id"));
            }
            result.add(occurrence);
        }
        if (!byNote.isEmpty()) review();
        if (!issueIds.equals(context.patientIssueIds())
                || !definitions.equals(context.definitionIds())) review();
        result.sort(
                Comparator.comparing(Occurrence::category).thenComparingLong(Occurrence::noteId));
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
