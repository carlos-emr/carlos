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

import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsHistoryNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.util.*;

/** Fieldwise current history selection; an unrelated edit never grants permission to replay an entire old occurrence. */
public final class CdsExportHistoryFieldReconciler {
    /** Expected mapping refusal, distinguishable from unreadable or unauthenticated source bytes. */
    public static final class MappingReview extends IOException {
        private MappingReview(String code) {
            super(code);
        }
    }

    public record Row(CdsExportCapturedTextRowDto value, CdsExportNativeRowReceiptDto witness) {
        public Row {
            Objects.requireNonNull(value);
            Objects.requireNonNull(witness);
        }

        @Override
        public String toString() {
            return "CdsExportHistoryFieldReconciler.Row[protected]";
        }
    }

    /**
     * Caller supplies every extension, issue link and note link of the current note, from
     * the same completely scanned source generation. {@code links} are the note's patient-issue
     * relations ({@code casemgmt_issue_notes}); {@code noteLinks} are the records the note is
     * attached to ({@code casemgmt_note_link}), each with its full-row witness.
     */
    record Current(
            Row note,
            List<Row> extensions,
            List<CdsExportCapturedTextRowDto> links,
            Map<String, Row> patientIssues,
            Map<String, Row> definitions,
            List<Row> noteLinks) {
        Current {
            Objects.requireNonNull(note);
            extensions = List.copyOf(extensions);
            links = List.copyOf(links);
            patientIssues = Map.copyOf(patientIssues);
            definitions = Map.copyOf(definitions);
            noteLinks = List.copyOf(noteLinks);
        }

        /** A note attached to no other record. */
        Current(
                Row note,
                List<Row> extensions,
                List<CdsExportCapturedTextRowDto> links,
                Map<String, Row> patientIssues,
                Map<String, Row> definitions) {
            this(note, extensions, links, patientIssues, definitions, List.of());
        }

        @Override
        public String toString() {
            return "CdsExportHistoryFieldReconciler.Current[protected]";
        }
    }

    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            CdsCategory category,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportRetainedSectionWriter.Change> changes,
            Set<String> sourceFields) {
        public Result {
            fields = Map.copyOf(fields);
            changes = List.copyOf(changes);
            sourceFields = Set.copyOf(sourceFields);
        }

        public boolean representable() {
            return fields.values().stream()
                    .noneMatch(value -> value.outcome() == Outcome.REQUIRES_REVIEW);
        }

        @Override
        public String toString() {
            return "CdsExportHistoryFieldReconciler.Result[protected]";
        }
    }

    /** Binds original preparation, source fields, baseline and exact current revision before proposing any XML changes. */
    public Result reconcile(
            int patient,
            CdsCategory category,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportNoteRevisionPlanner.Result inventory,
            CdsExportNoteAccessDto access,
            CdsExportHistoryCurrentContext context,
            List<CdsExportRetainedFieldDto> retained,
            CdsExportRetainedSectionWriter.Values values,
            CdsExportRunControl control)
            throws IOException {
        return reconcile(
                patient, category, address, index, checked, inventory, access, context, retained,
                values, control, null);
    }

    Result reconcile(
            int patient,
            CdsCategory category,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportNoteRevisionPlanner.Result inventory,
            CdsExportNoteAccessDto access,
            CdsExportHistoryCurrentContext context,
            List<CdsExportRetainedFieldDto> retained,
            CdsExportRetainedSectionWriter.Values values,
            CdsExportRunControl control,
            CdsExportNativeHistoryMapper.Result nativeHistory)
            throws IOException {
        Objects.requireNonNull(control);
        control.checkpoint();
        Objects.requireNonNull(values);
        values.requireActive();
        if (address == null
                || !address.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}:[0-9a-f]{64}")
                || index == null
                || checked == null
                || inventory == null
                || access == null
                || context == null
                || category == null
                || !CdsExportNoteSourceReader.CATEGORIES.contains(category)
                || patient != access.patient()
                || patient != inventory.patient()
                || !index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.sourceManifestSha256().equals(inventory.sourceManifestSha256())
                || !index.appliedUnits().equals(checked.units().keySet())
                || !inventory.qualifiedLineages()
                || context.patient() != patient
                || !context.manifest().equals(index.sourceManifestSha256())
                || !access.equals(context.scope(access.noteId()))) scope();
        Current current = context.note(access.noteId());
        control.authorizeNote(category, access);
        var prepared =
                new CdsExportRetainedPreparationReader().history(index, address, category).value();
        var references = index.references().get(address);
        var compared = checked.units().get(address);
        if (references == null
                || compared == null
                || !references.equals(
                        compared.stream()
                                .map(
                                        CdsExportRetainedReceiptVerificationDto.CheckedReference
                                                ::expected)
                                .toList())) scope();
        var noteRefs =
                references.stream().filter(ref -> ref.table().equals("casemgmt_note")).toList();
        if (noteRefs.size() != 1) scope();
        String importedId = noteRefs.getFirst().id(),
                currentId = current.note().value().fields().get("note_id");
        if (!Long.toString(access.noteId()).equals(currentId)) scope();
        long importedNoteId = parseId(importedId);
        var chains =
                inventory.chains().stream()
                        .filter(
                                chain ->
                                        chain.revisions().contains(importedNoteId)
                                                && chain.currentNoteId() == access.noteId())
                        .toList();
        if (chains.size() != 1) review("CDS_EXPORT_HISTORY_CURRENT_REVISION_REQUIRES_REVIEW");
        var unit =
                index.tables()
                        .getOrDefault("cds_import_unit", Map.of())
                        .get(CdsImportKeys.hash(address.substring(0, 36), address.substring(37)));
        if (unit == null) scope();
        var baseline = new HashMap<String, CdsNativeRowState>();
        if (unit.fields().get("native_baseline") != null) {
            try {
                for (var row :
                        CdsNativeBaselineCodec.decode(
                                        unit.fields().get("native_baseline"),
                                        unit.fields().get("target_references"),
                                        unit.fields().get("target_digest"))
                                .rows())
                    if (baseline.putIfAbsent(key(row.table(), row.id()), row) != null) scope();
            } catch (IllegalArgumentException invalid) {
                scope();
            }
        }
        for (var ref : compared) {
            if (!Set.of(
                            "casemgmt_note",
                            "casemgmt_note_ext",
                            "casemgmt_issue",
                            "casemgmt_issue_notes")
                    .contains(ref.expected().table())) scope();
            Row actual = context.original(ref.expected().table(), ref.expected().id());
            if (!baseline.containsKey(key(ref.expected().table(), ref.expected().id()))
                    && actual != null
                    && ref.reason() == CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW
                    && ref.expected().digest().equals(actual.witness().digest()))
                baseline.put(
                        key(ref.expected().table(), ref.expected().id()), actual.witness().state());
        }
        var oldNote = baseline.get(key("casemgmt_note", importedId));
        if (oldNote == null || !oldNote.digest().equals(noteRefs.getFirst().digest()))
            review("CDS_EXPORT_HISTORY_NATIVE_BASELINE_REQUIRED");
        var originalRow = context.original("casemgmt_note", importedId);
        if (originalRow == null) scope();
        String originalUuid = originalRow.value().fields().get("uuid"),
                currentUuid = current.note().value().fields().get("uuid");
        try {
            if (!UUID.fromString(originalUuid).toString().equals(chains.getFirst().canonicalUuid())
                    || !UUID.fromString(currentUuid)
                            .toString()
                            .equals(chains.getFirst().canonicalUuid())
                    || !CdsExportCaseManagementProjectionEvidence.matches(
                            oldNote, "uuid", originalUuid))
                review("CDS_EXPORT_HISTORY_REVISION_IDENTITY_REQUIRES_REVIEW");
        } catch (IllegalArgumentException | NullPointerException malformed) {
            scope();
        }

        var originalAccess = context.scope(importedNoteId);
        if (originalAccess == null) scope();
        var source =
                new CdsExportHistoryRetainedSource()
                        .read(
                                patient,
                                category,
                                address,
                                index,
                                checked,
                                retained,
                                originalAccess,
                                values,
                                control);
        var fields =
                new CdsExportRetainedOccurrenceFields()
                        .fieldsFor(
                                patient,
                                category,
                                address,
                                index,
                                checked,
                                retained,
                                control::checkpoint);
        var result =
                nativeHistory == null
                        ? choose(
                                patient,
                                category,
                                prepared,
                                oldNote,
                                current,
                                references,
                                baseline,
                                fields,
                                source.changes(),
                                source.sourceFields(),
                                index.sourceManifestSha256(),
                                address,
                                control::checkpoint)
                        : reconcileNative(
                                patient,
                                category,
                                address,
                                index.sourceManifestSha256(),
                                prepared,
                                oldNote,
                                current,
                                references,
                                baseline,
                                fields,
                                source,
                                nativeHistory,
                                values,
                                control::checkpoint);
        if (result.representable())
            new CdsExportRetainedSectionWriter()
                    .prepareChanges(fields, values, result.changes(), control::checkpoint);
        values.requireActive();
        control.authorizeNote(category, access);
        control.checkpoint();
        return result;
    }

    /**
     * Rules the native revision context accounts for on an imported item that was edited in
     * CARLOS. Its {@code CARLOS.NativeHistory.v1} context carries every revision's note, extension,
     * issue and link rows exactly, and the native reconciler writes the current standard values
     * over the retained source:
     *
     * <ul>
     *   <li>archive state, audit columns, authors and signatures, the editing provider's program
     *       team (operational on every native note), and a PersonalHistory edit (which has no
     *       standard narrative element);
     *   <li>a CPP extension added or changed in the CPP editor (Start Date, Age at Onset, ...),
     *       written as its standard element or, when it does not fit, in Notes (B5);
     *   <li>a changed observation date of an encounter note, written as EventDateTime (B5);
     *   <li>an issue assigned to or removed from the item (B5): see {@link #checkIssues}.
     * </ul>
     */
    static final Set<String> NATIVE_CONTEXT_RULES =
            Set.of(
                    "ARCHIVED_CLINICAL_CONTEXT_REQUIRES_REVIEW",
                    "CLINICAL_OR_AUDIT_CARRIER_REQUIRED",
                    "CHANGED_AUTHOR_OR_SIGNATURE_REQUIRES_RECONCILIATION",
                    "STRUCTURED_PERSONAL_HISTORY_EDIT_REQUIRES_REVIEW",
                    "CURRENT_NARRATIVE_NOT_REPRESENTABLE",
                    "CLINICAL_TEAM_RECONCILIATION_REQUIRED",
                    "NEW_OR_CHANGED_CLINICAL_EXTENSION_REQUIRES_RECONCILIATION",
                    "CHANGED_ISSUE_ASSOCIATION_IN_NATIVE_CONTEXT");

    /**
     * An imported item with CARLOS revisions: the strict guard first proves the item is still the
     * imported one (same preparation, baseline, category tag and import date metadata), tolerating
     * only {@link #NATIVE_CONTEXT_RULES}; the native reconciler then writes the current revision
     * over the retained source.
     */
    Result reconcileNative(
            int patient,
            CdsCategory category,
            String address,
            String manifest,
            CdsHistoryNativeDto prepared,
            CdsNativeRowState original,
            Current current,
            List<CdsExportRetainedIndexDto.Reference> references,
            Map<String, CdsNativeRowState> baseline,
            List<CdsExportRetainedFieldDto> fields,
            CdsExportHistoryRetainedSource.Result source,
            CdsExportNativeHistoryMapper.Result nativeHistory,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        // Native revision context qualifies audit, narrative, extension and issue changes, but
        // must not bypass the import's own date precision on a CPP item.
        var guard =
                choose(
                        patient,
                        category,
                        prepared,
                        original,
                        current,
                        references,
                        baseline,
                        fields,
                        source.changes(),
                        source.sourceFields(),
                        manifest,
                        address,
                        checkpoint,
                        true);
        if (guard.fields().values().stream()
                .anyMatch(
                        f ->
                                f.outcome() == Outcome.REQUIRES_REVIEW
                                        && !NATIVE_CONTEXT_RULES.contains(f.rule())
                                        && !(category == CdsCategory.CLINICAL_NOTES
                                                && f.rule()
                                                        .equals(
                                                                "CHANGED_OBSERVATION_AND_PRECISION_REQUIRE_RECONCILIATION"))))
            review("CDS_EXPORT_HISTORY_NATIVE_SOURCE_RECONCILIATION_REQUIRED");
        return new CdsExportHistoryNativeReconciler()
                .reconcile(
                        patient,
                        category,
                        address,
                        manifest,
                        prepared,
                        original,
                        current,
                        fields,
                        source,
                        nativeHistory,
                        values,
                        checkpoint);
    }

    /** The rule that carries a re-filed imported item's original section elements. */
    static final String REFILED_SOURCE_RULE = "REFILED_IMPORTED_ITEM_SOURCE_SECTION_CARRIED";

    /**
     * The retained elements of an imported item that a clinician re-filed into another CPP
     * section (B5), for its native context. Each element keeps its path under the import's own
     * section and its exact logical value, in source order; a date takes the verified import
     * shift the item would have been exported with ({@code dates}, from
     * {@link CdsExportHistoryRetainedSource}). Nothing is dropped or normalized; the receiving
     * system sees the item's current section and text in standard elements, and the original
     * record here.
     */
    static Map<String, Object> refiledSource(
            CdsCategory sourceCategory,
            List<CdsExportRetainedFieldDto> fields,
            Map<String, CdsExportDateDto> dates,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(values);
        if (sourceCategory == null || fields == null || fields.isEmpty() || dates == null)
            scope();
        values.requireActive();
        String root = CdsExportRetainedOccurrencePath.root(fields.getFirst(), sourceCategory);
        var elements = new ArrayList<Map<String, String>>();
        for (var field : fields) {
            checkpoint.run();
            if (!field.sourcePath().equals(root) && !field.sourcePath().startsWith(root + "/"))
                scope();
            String path =
                    field.sourcePath().equals(root)
                            ? ""
                            : field.sourcePath().substring(root.length() + 1);
            String value = null;
            for (var date : dates.entrySet())
                if (path.startsWith(date.getKey() + "/")) value = date.getValue().lexical();
            if (value == null) value = text(values, field, checkpoint);
            var element = new LinkedHashMap<String, String>();
            element.put("path", path);
            element.put("value", value);
            elements.add(element);
        }
        var carried = new LinkedHashMap<String, Object>();
        carried.put("rule", REFILED_SOURCE_RULE);
        carried.put("section", sourceCategory.wireName());
        carried.put("elements", elements);
        return carried;
    }

    private static String text(
            CdsExportRetainedSectionWriter.Values values,
            CdsExportRetainedFieldDto field,
            Runnable checkpoint)
            throws IOException {
        var result = new StringBuilder();
        char[] block = new char[4096];
        try (var input = values.text(field)) {
            for (int count; (count = input.read(block)) != -1; ) {
                checkpoint.run();
                result.append(block, 0, count);
                if (result.length() > 16_777_215)
                    review("CDS_EXPORT_HISTORY_REFILED_SOURCE_CAPACITY");
            }
        } finally {
            Arrays.fill(block, '\0');
        }
        String value = result.toString();
        if (!CdsExportNativeFieldAssessments.legal(value, 16_777_215))
            review("CDS_EXPORT_HISTORY_REFILED_SOURCE_CAPACITY");
        return value;
    }

    Result choose(
            int patient,
            CdsCategory category,
            CdsHistoryNativeDto prepared,
            CdsNativeRowState original,
            Current current,
            List<CdsExportRetainedIndexDto.Reference> references,
            Map<String, CdsNativeRowState> baseline,
            List<CdsExportRetainedFieldDto> sourceFields,
            List<CdsExportRetainedSectionWriter.Change> initialChanges,
            Set<String> completeSourceFields,
            String manifest,
            String address,
            Runnable checkpoint)
            throws IOException {
        return choose(
                patient,
                category,
                prepared,
                original,
                current,
                references,
                baseline,
                sourceFields,
                initialChanges,
                completeSourceFields,
                manifest,
                address,
                checkpoint,
                false);
    }

    private Result choose(
            int patient,
            CdsCategory category,
            CdsHistoryNativeDto prepared,
            CdsNativeRowState original,
            Current current,
            List<CdsExportRetainedIndexDto.Reference> references,
            Map<String, CdsNativeRowState> baseline,
            List<CdsExportRetainedFieldDto> sourceFields,
            List<CdsExportRetainedSectionWriter.Change> initialChanges,
            Set<String> completeSourceFields,
            String manifest,
            String address,
            Runnable checkpoint,
            boolean nativeContext)
            throws IOException {
        checkpoint.run();
        var note = current.note().value();
        CdsExportCaseManagementProjectionEvidence.verify(note, current.note().witness());
        if (!Integer.toString(patient).equals(note.fields().get("demographic_no"))
                || !CdsExportCaseManagementProjectionEvidence.matches(
                        original, "demographic_no", Integer.toString(patient))
                || !CdsExportCaseManagementProjectionEvidence.matches(
                        original, "note", prepared.content())
                || !CdsExportCaseManagementProjectionEvidence.matches(
                        original,
                        "provider_no",
                        prepared.providerNo() == null ? "" : prepared.providerNo())) scope();
        var comparison = compare(original, current.note().witness().state());
        if (comparison.schemaChanged() || !comparison.unqualifiedColumns().isEmpty())
            review("CDS_EXPORT_HISTORY_FIELD_CODEC_REQUIRES_REVIEW");
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var assessed = new CdsExportNativeFieldAssessments(note, decisions);
        for (String column :
                List.of(
                        "note_id",
                        "demographic_no",
                        "uuid",
                        "program_no",
                        "reporter_caisi_role",
                        "position",
                        "include_issue_innote",
                        "update_date"))
            assessed.operational(column, "NATIVE_IDENTITY_ACCESS_AND_LOCAL_PRESENTATION");
        String team = note.fields().get("reporter_program_team");
        if (team == null || team.isEmpty() || team.equals("0"))
            assessed.operational("reporter_program_team", "NO_RECORDED_CLINICAL_TEAM");
        else
            assessed.review(
                    "reporter_program_team",
                    "ParticipatingProviders",
                    "CLINICAL_TEAM_RECONCILIATION_REQUIRED");
        // Credentials are never a clinical wire value or a supplementary source field.
        assessed.operational("password", "SECRET_NEVER_EXPORTED");
        if (note.fields().get("locked") == null || "0".equals(note.fields().get("locked")))
            assessed.operational("locked", "LIVE_UNLOCKED_NOTE_ACCESS_REQUIRED");
        else assessed.review("locked", "", "LOCKED_NOTE_REQUIRES_AUTHORIZATION_REVIEW");
        if (note.fields().get("archived") == null || "0".equals(note.fields().get("archived")))
            assessed.operational("archived", "UNCHANGED_ACTIVE_LOCAL_PRESENTATION");
        else assessed.review("archived", "", "ARCHIVED_CLINICAL_CONTEXT_REQUIRES_REVIEW");
        for (String column :
                List.of(
                        "history",
                        "encounter_type",
                        "billing_code",
                        "appointmentNo",
                        "hourOfEncounterTime",
                        "minuteOfEncounterTime",
                        "hourOfEncTransportationTime",
                        "minuteOfEncTransportationTime")) {
            String value = note.fields().get(column);
            if (value != null && !value.isEmpty()) {
                if (!Set.of("history", "encounter_type", "billing_code").contains(column)
                        && value.equals("0"))
                    assessed.operational(column, "NO_RECORDED_ENCOUNTER_ASSOCIATION_OR_DURATION");
                else assessed.review(column, "", "CLINICAL_OR_AUDIT_CARRIER_REQUIRED");
            }
        }
        for (String column : List.of("provider_no", "signed", "signing_provider_no")) {
            if (comparison.changedColumns().contains(column))
                assessed.review(
                        column,
                        "ParticipatingProviders",
                        "CHANGED_AUTHOR_OR_SIGNATURE_REQUIRES_RECONCILIATION");
            else
                assessed.set(
                        column,
                        column.equals("signed") || column.equals("signing_provider_no")
                                ? Outcome.OPERATIONAL
                                : Outcome.MAPPED,
                        "ParticipatingProviders",
                        "ORIGINAL_RECORDED_AUTHORS_PRESERVED");
        }
        var changes = new ArrayList<>(initialChanges);
        String body = body(category);
        if (!comparison.changedColumns().contains("note"))
            assessed.set(
                    "note",
                    Outcome.MAPPED,
                    category.wireName(),
                    "VERIFIED_UNCHANGED_PREPARED_NARRATIVE_OR_STRUCTURED_SOURCE");
        else if (body == null)
            assessed.review(
                    "note", "ResidualInfo", "STRUCTURED_PERSONAL_HISTORY_EDIT_REQUIRES_REVIEW");
        else {
            String text = note.fields().get("note");
            int maximum = maximum(category);
            if (text == null || !CdsExportNativeFieldAssessments.legal(text, maximum))
                assessed.review("note", body, "CURRENT_NARRATIVE_NOT_REPRESENTABLE");
            else {
                var prior = new HashSet<String>();
                String root =
                        sourceFields.isEmpty()
                                ? ""
                                : CdsExportRetainedOccurrencePath.root(
                                        sourceFields.getFirst(), category);
                for (var field : sourceFields)
                    if (field.sourcePath().equals(root + "/" + body + "[1]")) prior.add(field.id());
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(
                                body + "[1]", prior, xml -> xml.text(text)));
                assessed.set("note", Outcome.MAPPED, body, "EXACT_CURRENT_NATIVE_NARRATIVE");
            }
        }
        if (prepared.observationMillis() == null
                && !CdsExportCaseManagementProjectionEvidence.matches(
                        original, "observation_date", null)) scope();
        boolean dateChanged = comparison.changedColumns().contains("observation_date");
        assessed.set(
                "observation_date",
                dateChanged
                        ? Outcome.REQUIRES_REVIEW
                        : prepared.observationField() == null ? Outcome.ABSENT : Outcome.MAPPED,
                prepared.observationField() == null ? "" : prepared.observationField(),
                dateChanged
                        ? "CHANGED_OBSERVATION_AND_PRECISION_REQUIRE_RECONCILIATION"
                        : "UNCHANGED_ORIGINAL_SOURCE_TEMPORAL_PRECISION_PRESERVED");
        checkExtensions(
                prepared,
                current,
                references,
                baseline,
                dateChanged,
                nativeContext && category == CdsCategory.CLINICAL_NOTES,
                decisions,
                checkpoint);
        checkIssues(
                patient,
                prepared,
                current,
                references,
                baseline,
                nativeContext,
                decisions,
                checkpoint);
        checkpoint.run();
        return new Result(manifest, address, category, decisions, changes, completeSourceFields);
    }

    /**
     * @param nativeDate an encounter note in native context: a changed observation date is
     *     written as EventDateTime from the current revision, and the import's date metadata rows
     *     travel, exactly, in the revision context, so they need not survive unchanged
     */
    private static void checkExtensions(
            CdsHistoryNativeDto prepared,
            Current current,
            List<CdsExportRetainedIndexDto.Reference> references,
            Map<String, CdsNativeRowState> baseline,
            boolean dateChanged,
            boolean nativeDate,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions,
            Runnable checkpoint)
            throws IOException {
        var expected = new HashMap<String, String>();
        if (prepared.observationDate() != null) {
            expected.put("cdsDatePrecision", prepared.observationDate().precision().name());
            if (prepared.observationDate().original() != null)
                expected.put("cdsDateOriginal", prepared.observationDate().original());
            if (prepared.observationField() != null)
                expected.put("cdsDateField", prepared.observationField());
        }
        var originals = new HashMap<String, CdsNativeRowState>();
        String originalNoteId =
                references.stream()
                        .filter(ref -> ref.table().equals("casemgmt_note"))
                        .findFirst()
                        .orElseThrow()
                        .id();
        for (var ref : references)
            if (ref.table().equals("casemgmt_note_ext")) {
                var old = baseline.get(key("casemgmt_note_ext", ref.id()));
                if (old == null
                        || !old.digest().equals(ref.digest())
                        || !CdsExportCaseManagementProjectionEvidence.matches(
                                old, "note_id", originalNoteId))
                    review("CDS_EXPORT_HISTORY_DATE_BASELINE_REQUIRED");
                String matched = null;
                for (String name : expected.keySet())
                    if (CdsExportCaseManagementProjectionEvidence.matches(old, "key_val", name)) {
                        if (matched != null) scope();
                        matched = name;
                    }
                if (matched == null
                        || originals.putIfAbsent(matched, old) != null
                        || !CdsExportCaseManagementProjectionEvidence.matches(
                                old, "value", expected.get(matched))
                        || !CdsExportCaseManagementProjectionEvidence.matches(
                                old, "date_value", null))
                    review("CDS_EXPORT_HISTORY_DATE_BASELINE_REQUIRED");
            }
        if (!originals.keySet().equals(expected.keySet()))
            review("CDS_EXPORT_HISTORY_DATE_BASELINE_REQUIRED");
        var seen = new HashSet<String>();
        var actualIds = new HashSet<String>();
        for (var extension : current.extensions()) {
            checkpoint.run();
            CdsExportCaseManagementProjectionEvidence.verify(
                    extension.value(), extension.witness());
            var row = extension.value();
            String id = row.fields().get("id"), name = row.fields().get("key_val");
            if (!actualIds.add(id)
                    || !Objects.equals(
                            row.fields().get("note_id"),
                            current.note().value().fields().get("note_id"))) scope();
            var fields = new CdsExportNativeFieldAssessments(row, decisions);
            fields.operational("id", "EXACT_NATIVE_EXTENSION_IDENTITY");
            fields.operational("note_id", "EXACT_CURRENT_REVISION_PARENT");
            boolean preserved =
                    expected.containsKey(name)
                            && seen.add(name)
                            && !dateChanged
                            && Objects.equals(expected.get(name), row.fields().get("value"))
                            && row.fields().get("date_value") == null;
            for (String column : List.of("key_val", "value", "date_value"))
                fields.set(
                        column,
                        preserved ? Outcome.OPERATIONAL : Outcome.REQUIRES_REVIEW,
                        "",
                        preserved
                                ? "VERIFIED_ORIGINAL_SOURCE_DATE_METADATA"
                                : "NEW_OR_CHANGED_CLINICAL_EXTENSION_REQUIRES_RECONCILIATION");
        }
        if (!seen.equals(expected.keySet()) && !nativeDate)
            review("CDS_EXPORT_HISTORY_DATE_EXTENSION_DELETION_REQUIRES_REVIEW");
    }

    /**
     * The imported item's category tag (the only issue the CARLOS importer links) must be the
     * unchanged imported one. In native context (B5), an issue a clinician assigned to or removed
     * from the item since the import is accepted: the native revision context carries every
     * revision's links, patient issues and catalogue rows exactly, and the native reconciler
     * writes the current diagnosis codes. A removed or changed category tag is decided by the
     * native mapper's category check.
     */
    private static void checkIssues(
            int patient,
            CdsHistoryNativeDto prepared,
            Current current,
            List<CdsExportRetainedIndexDto.Reference> references,
            Map<String, CdsNativeRowState> baseline,
            boolean nativeContext,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions,
            Runnable checkpoint)
            throws IOException {
        var expected =
                references.stream().filter(ref -> ref.table().equals("casemgmt_issue")).toList();
        var expectedLinks =
                references.stream()
                        .filter(ref -> ref.table().equals("casemgmt_issue_notes"))
                        .toList();
        if (prepared.issueCode() == null
                ? !expected.isEmpty() || !expectedLinks.isEmpty()
                : expected.size() != 1 || expectedLinks.size() != 1)
            review("CDS_EXPORT_HISTORY_ISSUE_ASSOCIATION_REQUIRES_REVIEW");
        var importedLinks =
                current.links().stream()
                        .filter(
                                link ->
                                        !expected.isEmpty()
                                                && expected.getFirst()
                                                        .id()
                                                        .equals(link.fields().get("id")))
                        .toList();
        if (prepared.issueCode() == null
                ? !current.links().isEmpty()
                : importedLinks.size() != 1 || current.links().size() != 1) {
            if (!nativeContext) review("CDS_EXPORT_HISTORY_ISSUE_ASSOCIATION_REQUIRES_REVIEW");
            for (var link : current.links())
                if (!importedLinks.contains(link)) {
                    var fields = new CdsExportNativeFieldAssessments(link, decisions);
                    for (String column : link.fields().keySet())
                        fields.review(
                                column,
                                "ResidualInfo",
                                "CHANGED_ISSUE_ASSOCIATION_IN_NATIVE_CONTEXT");
                }
        }
        for (var link : importedLinks) {
            checkpoint.run();
            if (!link.table().equals("casemgmt_issue_notes")
                    || !link.fields().keySet().equals(Set.of("id", "note_id"))
                    || !link.exactNumbers().isEmpty()
                    || !Objects.equals(
                            link.fields().get("note_id"),
                            current.note().value().fields().get("note_id"))
                    || !expected.getFirst().id().equals(link.fields().get("id"))) scope();
            String importedNote =
                    references.stream()
                            .filter(ref -> ref.table().equals("casemgmt_note"))
                            .findFirst()
                            .orElseThrow()
                            .id();
            var linkRef = expectedLinks.getFirst();
            var oldLink = baseline.get(key("casemgmt_issue_notes", linkRef.id()));
            if (!linkRef.id().equals(importedNote + "_" + link.fields().get("id"))
                    || oldLink == null
                    || !oldLink.digest().equals(linkRef.digest())
                    || !Set.copyOf(
                                    oldLink.columns().stream()
                                            .map(CdsNativeRowDigest.Column::name)
                                            .toList())
                            .equals(Set.of("id", "note_id"))
                    || !CdsExportCaseManagementProjectionEvidence.matches(
                            oldLink, "id", link.fields().get("id"))
                    || !CdsExportCaseManagementProjectionEvidence.matches(
                            oldLink, "note_id", importedNote))
                review("CDS_EXPORT_HISTORY_ISSUE_LINK_BASELINE_REQUIRED");
            var issue = current.patientIssues().get(link.fields().get("id"));
            if (issue == null) scope();
            CdsExportCaseManagementProjectionEvidence.verify(issue.value(), issue.witness());
            var row = issue.value();
            if (!Integer.toString(patient).equals(row.fields().get("demographic_no"))) scope();
            var definition = current.definitions().get(row.fields().get("issue_id"));
            if (definition == null) scope();
            CdsExportCaseManagementProjectionEvidence.verify(
                    definition.value(), definition.witness());
            var old = baseline.get(key("casemgmt_issue", row.fields().get("id")));
            var difference = old == null ? null : compare(old, issue.witness().state());
            if (old == null
                    || !old.digest().equals(expected.getFirst().digest())
                    || difference.schemaChanged()
                    || !difference.unqualifiedColumns().isEmpty()
                    // In native context a changed flag of the patient issue (acute, certain,
                    // major, resolved) travels exactly in the revision context.
                    || !nativeContext
                            && difference.changedColumns().stream()
                                    .anyMatch(column -> !column.equals("update_date"))
                    || !Objects.equals(
                            prepared.issueCode(), definition.value().fields().get("code")))
                review("CDS_EXPORT_HISTORY_ISSUE_EDIT_REQUIRES_RECONCILIATION");
            for (var evidence : List.of(row, definition.value(), link)) {
                var fields = new CdsExportNativeFieldAssessments(evidence, decisions);
                for (String column : evidence.fields().keySet())
                    fields.operational(
                            column, "EXACT_UNCHANGED_PREPARED_HISTORY_CATEGORY_ASSOCIATION");
            }
        }
    }

    private static CdsNativeRowComparison compare(CdsNativeRowState old, CdsNativeRowState actual) {
        return CdsNativeRowComparison.compare(
                old,
                old.id().equals(actual.id())
                        ? actual
                        : new CdsNativeRowState(
                                actual.table(), old.id(), actual.columns(), actual.cells()));
    }

    private static String body(CdsCategory category) {
        return switch (category) {
            case PERSONAL_HISTORY -> null;
            case FAMILY_HISTORY -> "ProblemDiagnosisProcedureDescription";
            case PAST_HEALTH -> "PastHealthProblemDescriptionOrProcedures";
            case PROBLEM_LIST -> "ProblemDiagnosisDescription";
            case RISK_FACTORS -> "RiskFactor";
            case ALERTS_AND_SPECIAL_NEEDS -> "AlertDescription";
            case CLINICAL_NOTES -> "MyClinicalNotesContent";
            default -> throw new IllegalArgumentException("CDS_EXPORT_HISTORY_CATEGORY_INVALID");
        };
    }

    private static int maximum(CdsCategory category) {
        return switch (category) {
            case RISK_FACTORS -> 120;
            case FAMILY_HISTORY, PAST_HEALTH, PROBLEM_LIST -> 250;
            case ALERTS_AND_SPECIAL_NEEDS -> 1024;
            case CLINICAL_NOTES -> 16_777_215;
            default -> 0;
        };
    }

    private static long parseId(String value) throws IOException {
        try {
            long id = Long.parseLong(value);
            if (id < 1 || !Long.toString(id).equals(value)) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException malformed) {
            throw new IOException("CDS_EXPORT_HISTORY_NATIVE_ID_INVALID");
        }
    }

    private static String key(String table, String id) {
        return table + "/" + id;
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_HISTORY_RECONCILIATION_SCOPE_INVALID");
    }

    private static void review(String code) throws MappingReview {
        throw new MappingReview(code);
    }
}
