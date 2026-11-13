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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.time.*;
import java.util.*;

/** Native case-management meanings from a closed snapshot, independent of import provenance. */
public final class CdsExportNativeHistoryMapper {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, CdsCategory> TAGS =
            Map.of(
                    "SocHistory",
                    CdsCategory.PERSONAL_HISTORY,
                    "FamHistory",
                    CdsCategory.FAMILY_HISTORY,
                    "MedHistory",
                    CdsCategory.PAST_HEALTH,
                    "Concerns",
                    CdsCategory.PROBLEM_LIST,
                    "RiskFactors",
                    CdsCategory.RISK_FACTORS,
                    "Reminders",
                    CdsCategory.ALERTS_AND_SPECIAL_NEEDS);
    private static final Set<String> OPERATIONAL =
            Set.of(
                    "note_id",
                    "demographic_no",
                    "uuid",
                    "program_no",
                    "reporter_caisi_role",
                    "reporter_program_team",
                    "position",
                    "locked",
                    "password",
                    "include_issue_innote",
                    "appointmentNo");

    /**
     * Leads the text of a ClinicalNotes entry whose current revision is archived. CARLOS archives
     * a note by saving a new revision with {@code archived = 1} (the CPP editor's Archive button,
     * NotesService); the CPP boxes, CPP print and CPP text drop it, but the encounter note list
     * still shows it, so it is part of the record, not deleted. ClinicalNotes has no status or
     * residual field, so the receiver is told in the note itself rather than shown a live note.
     */
    /**
     * Leads the Notes of an archived CPP item (FamilyHistory, PastHealth, ProblemList,
     * RiskFactors, AlertsAndSpecialNeeds). Its archive state also travels in
     * {@code CARLOS.History.Archived.v1}, which only CARLOS reads; without this statement any
     * other receiver would show a removed problem, risk or alert as current.
     */
    static final String ARCHIVED_ITEM_STATEMENT =
            "[CARLOS: this item was archived (removed from the active record). It is kept for the"
                    + " record and is not current.]";

    /** Combines the archived-item statement with an item's other Notes text, if any. */
    static String archivedNotes(String notes) {
        return notes == null || notes.isEmpty()
                ? ARCHIVED_ITEM_STATEMENT
                : ARCHIVED_ITEM_STATEMENT + "\n\n" + notes;
    }

    /** The CDS 5.2 Notes limit of a CPP category (text32K, text64K or text1K). */
    static int notesLimit(CdsCategory category) {
        return switch (category) {
            case FAMILY_HISTORY -> 32768;
            case ALERTS_AND_SPECIAL_NEEDS -> 1024;
            default -> 65536;
        };
    }

    /**
     * Internal text key (never a wire element) holding an item's Notes statements: extension
     * values its standard elements cannot hold, and its coded diagnoses when there are several.
     */
    static final String STATEMENTS = "NativeStatements";

    /**
     * Rule of a patient issue owned by a program the exporting identities do not work in, exported
     * because the notes being exported link it: the chart shows it beside every note a provider
     * can read. Its {@code program_id} is carried exactly.
     */
    static final String FOREIGN_PROGRAM_ISSUE_RULE = "FOREIGN_PROGRAM_ISSUE_FOLLOWS_LINKED_NOTE_ACCESS";

    static final String ARCHIVED_CLINICAL_NOTE_STATEMENT =
            "[CARLOS: this note was archived (removed from the active record) in its latest"
                    + " revision. It is kept for the record and is not a current note.]";

    public record Result(
            CdsExportNativeHistoryDto value,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportCapturedTextRowDto> rows) {
        public Result {
            fields = Map.copyOf(fields);
            rows = List.copyOf(rows);
        }

        @Override
        public String toString() {
            return "CdsExportNativeHistoryMapper.Result[protected]";
        }
    }

    static CdsCategory category(CdsExportHistoryFieldReconciler.Current current)
            throws IOException {
        var categories = EnumSet.noneOf(CdsCategory.class);
        for (var link : current.links()) {
            var patientIssue = current.patientIssues().get(link.fields().get("id"));
            if (patientIssue == null) invalid();
            var definition =
                    current.definitions().get(patientIssue.value().fields().get("issue_id"));
            if (definition == null) invalid();
            String code = definition.value().fields().get("code");
            var category = code == null ? null : TAGS.get(code);
            if (category != null) categories.add(category);
        }
        if (categories.size() > 1) review("CDS_EXPORT_NATIVE_HISTORY_CATEGORY_AMBIGUOUS");
        return categories.isEmpty() ? CdsCategory.CLINICAL_NOTES : categories.iterator().next();
    }

    public Result map(
            int patient,
            CdsCategory category,
            List<CdsExportHistoryFieldReconciler.Current> revisions,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone)
            throws IOException {
        return map(patient, category, revisions, providers, zone, program -> false);
    }

    /**
     * @param readableProgram fresh answer to whether the exporting identities work in a program
     *     that owns a linked patient issue (see {@link #mapIssue}); it is consulted only for an
     *     issue whose program differs from its note's own program
     */
    public Result map(
            int patient,
            CdsCategory category,
            List<CdsExportHistoryFieldReconciler.Current> revisions,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone,
            java.util.function.Predicate<String> readableProgram)
            throws IOException {
        return map(patient, category, revisions, providers, zone, readableProgram, null);
    }

    /**
     * As {@link #map(int, CdsCategory, List, Map, ZoneId, java.util.function.Predicate)}, for an
     * imported item re-filed into another CPP section: {@code refiledSource}
     * ({@link CdsExportHistoryFieldReconciler#refiledSource}) travels in the item's native context
     * under {@code refiledImportedSource}.
     */
    public Result map(
            int patient,
            CdsCategory category,
            List<CdsExportHistoryFieldReconciler.Current> revisions,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone,
            java.util.function.Predicate<String> readableProgram,
            Map<String, Object> refiledSource)
            throws IOException {
        Objects.requireNonNull(readableProgram);
        if (patient < 1
                || revisions.isEmpty()
                || revisions.size() > 8192
                || !CdsExportNoteSourceReader.CATEGORIES.contains(category)) invalid();
        var current = revisions.getLast();
        // An imported note later re-filed under another CPP category is a chart condition for
        // review (this patient is held), not an integrity failure of the whole job.
        if (category(current) != category)
            review("CDS_EXPORT_NATIVE_HISTORY_CATEGORY_CHANGED_REQUIRES_REVIEW");
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var rows = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        var metadata = new ArrayList<Map<String, Object>>();
        var participants = new ArrayList<CdsExportNativeHistoryDto.Participant>();
        var text = new LinkedHashMap<String, String>();
        var dates = new LinkedHashMap<String, CdsExportDateDto>();
        var bodies = new ArrayList<String>();
        // CPP extension values the standard element cannot hold exactly (A25): written in Notes.
        var overflow = new LinkedHashSet<String>();
        long previous = 0;
        String uuid = null;
        List<Map<String, String>> attachedTo = List.of();
        for (var revision : revisions) {
            var note = revision.note().value();
            CdsExportCaseManagementProjectionEvidence.verify(note, revision.note().witness());
            long id = Long.parseLong(note.fields().get("note_id"));
            if (id <= previous
                    || !Integer.toString(patient).equals(note.fields().get("demographic_no")))
                invalid();
            previous = id;
            String revisionUuid;
            try {
                revisionUuid = UUID.fromString(note.fields().get("uuid")).toString();
            } catch (IllegalArgumentException | NullPointerException malformed) {
                invalid();
                return null;
            }
            if (uuid != null && !uuid.equals(revisionUuid)) invalid();
            uuid = revisionUuid;
            add(rows, note);
            var state = new CdsExportNativeFieldAssessments(note, decisions);
            for (String field : OPERATIONAL)
                state.operational(field, "VERIFIED_NATIVE_NOTE_IDENTITY_ACCESS_OR_PRESENTATION");
            String body = state.text("note", bodyPath(category), 1 << 24);
            if (state.raw("note") == null) review("CDS_EXPORT_NATIVE_NOTE_BODY_REQUIRED");
            bodies.add(body == null ? "" : body);
            var detail = new LinkedHashMap<String, Object>();
            detail.put("revision", metadata.size() + 1);
            for (String field :
                    List.of(
                            "archived",
                            "signed",
                            "billing_code",
                            "history",
                            "hourOfEncTransportationTime",
                            "hourOfEncounterTime",
                            "minuteOfEncTransportationTime",
                            "minuteOfEncounterTime")) {
                String value = state.raw(field);
                if (value != null) {
                    if (!CdsExportNativeFieldAssessments.legal(value, 1 << 24))
                        review("CDS_EXPORT_NATIVE_HISTORY_TEXT_INVALID");
                    if ((field.equals("archived") || field.equals("signed"))
                            && !Set.of("0", "1").contains(value))
                        review("CDS_EXPORT_NATIVE_HISTORY_FLAG_INVALID");
                    detail.put(field, value);
                    state.set(
                            field,
                            Outcome.MAPPED,
                            metadataPath(category),
                            "EXACT_NATIVE_NOTE_ATTRIBUTE");
                }
            }
            var author = provider(state, "provider_no", providers, decisions, category, false);
            var signer =
                    provider(state, "signing_provider_no", providers, decisions, category, true);
            if (signer != null) detail.put("signingProvider", publicName(signer));
            String updated = state.raw("update_date"), observed = state.raw("observation_date");
            if (updated != null) {
                detail.put("updated", updated);
                state.set(
                        "update_date",
                        Outcome.MAPPED,
                        metadataPath(category),
                        "EXACT_NATIVE_REVISION_TIME");
            }
            if (observed != null) {
                detail.put("observed", observed);
                state.set(
                        "observation_date",
                        Outcome.MAPPED,
                        metadataPath(category),
                        "EXACT_NATIVE_EVENT_TIME");
            }
            if (author != null) {
                detail.put("author", publicName(author));
                if (category == CdsCategory.CLINICAL_NOTES) {
                    if (updated == null) review("CDS_EXPORT_NATIVE_NOTE_AUTHOR_TIME_REQUIRED");
                    var time = clock(updated, zone);
                    // One participant per distinct author, at that author's first revision. Every
                    // revision's author and time stay in the note details. A repeated entry for
                    // the same author made a receiving CARLOS treat the note as multi-author and
                    // store it with no author at all.
                    if (participants.stream().noneMatch(p -> p.provider().equals(author)))
                        participants.add(new CdsExportNativeHistoryDto.Participant(author, time));
                }
            }
            String encounter = state.raw("encounter_type");
            if (encounter != null && !encounter.isEmpty()) {
                if (!token(encounter, 50)) review("CDS_EXPORT_NATIVE_NOTE_TYPE_REQUIRES_REVIEW");
                detail.put("encounterType", encounter);
                state.set(
                        "encounter_type",
                        Outcome.MAPPED,
                        category == CdsCategory.CLINICAL_NOTES
                                ? "NoteType"
                                : metadataPath(category),
                        "EXACT_NATIVE_ENCOUNTER_TYPE");
                if (revision == current && category == CdsCategory.CLINICAL_NOTES)
                    text.put("NoteType", encounter);
            }
            var extensions = new ArrayList<Map<String, String>>();
            for (var extension : revision.extensions()) {
                CdsExportCaseManagementProjectionEvidence.verify(
                        extension.value(), extension.witness());
                add(rows, extension.value());
                var ext = new CdsExportNativeFieldAssessments(extension.value(), decisions);
                ext.operational("id", "NATIVE_EXTENSION_IDENTITY");
                ext.operational("note_id", "VERIFIED_NATIVE_NOTE_PARENT");
                String key = ext.raw("key_val"),
                        value = ext.raw("value"),
                        date = ext.raw("date_value");
                if (key == null || key.isEmpty())
                    review("CDS_EXPORT_NATIVE_NOTE_EXTENSION_KEY_REQUIRED");
                if (!CdsExportNativeFieldAssessments.legal(key, 255)
                        || value != null && !CdsExportNativeFieldAssessments.legal(value, 1 << 24)
                        || date != null && !CdsExportNativeFieldAssessments.legal(date, 128))
                    review("CDS_EXPORT_NATIVE_NOTE_EXTENSION_TEXT_INVALID");
                var details = new LinkedHashMap<String, String>();
                details.put("key", key);
                if (value != null) details.put("value", value);
                if (date != null) details.put("date", date);
                extensions.add(details);
                for (String field : List.of("key_val", "value", "date_value"))
                    ext.set(
                            field,
                            Outcome.MAPPED,
                            metadataPath(category),
                            "COMPLETE_NATIVE_NOTE_EXTENSION");
                if (revision == current)
                    extension(category, key, value, date, text, dates, overflow);
            }
            if (!extensions.isEmpty()) detail.put("extensions", extensions);
            var issues = new ArrayList<Map<String, String>>();
            for (var link : revision.links()) {
                add(rows, link);
                var linked = revision.patientIssues().get(link.fields().get("id"));
                if (linked == null
                        || !note.fields().get("note_id").equals(link.fields().get("note_id")))
                    invalid();
                var definition =
                        revision.definitions().get(linked.value().fields().get("issue_id"));
                if (definition == null) invalid();
                CdsExportCaseManagementProjectionEvidence.verify(linked.value(), linked.witness());
                String issueProgram = linked.value().fields().get("program_id");
                // One patient issue serves every program's notes (casemgmt_issue is unique per
                // patient and issue), so a note can be filed under an issue another program
                // created. The chart shows that issue beside every note a provider can read, so
                // an issue of a program the exporting identities do not work in follows this
                // note's access (the note is authorized before mapping and again after emission)
                // and its program is carried, instead of holding the patient.
                boolean followsNote =
                        scopedProgram(issueProgram)
                                && !issueProgram.equals(note.fields().get("program_no"))
                                && !readableProgram.test(issueProgram);
                CdsExportCaseManagementProjectionEvidence.verify(
                        definition.value(), definition.witness());
                add(rows, linked.value());
                add(rows, definition.value());
                var linkState = new CdsExportNativeFieldAssessments(link, decisions);
                linkState.operational("id", "VERIFIED_NATIVE_PATIENT_ISSUE_LINK");
                linkState.operational("note_id", "VERIFIED_NATIVE_NOTE_LINK");
                var issueState = new CdsExportNativeFieldAssessments(linked.value(), decisions);
                var definitionState =
                        new CdsExportNativeFieldAssessments(definition.value(), decisions);
                var facts = new LinkedHashMap<String, String>();
                for (var e : linked.value().fields().entrySet()) {
                    if (followsNote && e.getKey().equals("program_id")) {
                        facts.put("program_id", e.getValue());
                        issueState.set(
                                "program_id",
                                Outcome.MAPPED,
                                metadataPath(category),
                                FOREIGN_PROGRAM_ISSUE_RULE);
                    } else if (Set.of("id", "demographic_no", "issue_id", "program_id")
                            .contains(e.getKey()))
                        issueState.operational(
                                e.getKey(), "VERIFIED_NATIVE_ISSUE_IDENTITY_OR_ACCESS");
                    else {
                        if (e.getValue() != null) facts.put(e.getKey(), e.getValue());
                        issueState.set(
                                e.getKey(),
                                Outcome.MAPPED,
                                metadataPath(category),
                                "COMPLETE_NATIVE_ISSUE_ATTRIBUTE");
                    }
                }
                for (var e : definition.value().fields().entrySet()) {
                    if (Set.of("issue_id", "priority", "role", "sortOrderId", "update_date")
                            .contains(e.getKey()))
                        definitionState.operational(e.getKey(), "PUBLIC_ISSUE_CATALOGUE_METADATA");
                    else {
                        if (e.getValue() != null)
                            facts.put("definition." + e.getKey(), e.getValue());
                        definitionState.set(
                                e.getKey(),
                                Outcome.MAPPED,
                                metadataPath(category),
                                "COMPLETE_NATIVE_ISSUE_DEFINITION");
                    }
                }
                issues.add(facts);
            }
            if (!issues.isEmpty()) detail.put("issues", issues);
            var attached = attachments(revision, note, category, rows, decisions);
            if (!attached.isEmpty()) detail.put("attachedTo", attached);
            if (revision == current) attachedTo = attached;
            // ClinicalNotes text already carries every revision's body verbatim under its own
            // "[Revision n]" heading; repeating it in the details only doubled the note.
            if (category != CdsCategory.CLINICAL_NOTES
                    && (revision != current || category == CdsCategory.PERSONAL_HISTORY))
                detail.put("narrative", body == null ? "" : body);
            metadata.add(detail);
        }
        var context = versioned("revisions", metadata);
        if (refiledSource != null) context.put("refiledImportedSource", refiledSource);
        String json = JSON.writeValueAsString(context);
        var codes = codes(category, current);
        // DiagnosisProcedureCode and DiagnosisCode hold one code. An item with several assigned
        // diagnoses (A24) writes none of them there rather than choosing one: every code is
        // listed in Notes, and each issue's complete facts stay in the revision context.
        var coding = codes.size() == 1 ? codes.getFirst() : null;
        if (codes.size() > 1) overflow.add(codedDiagnoses(codes));
        String body = bodies.getLast();
        boolean archived = "1".equals(current.note().value().fields().get("archived"));
        if (category == CdsCategory.CLINICAL_NOTES) {
            var full = new StringBuilder();
            if (archived) {
                full.append(ARCHIVED_CLINICAL_NOTE_STATEMENT).append("\n\n");
                var note = current.note().value();
                decisions.put(
                        new CdsExportNativeFieldKeyDto(note.table(), note.recordKey(), "archived"),
                        new CdsExportFieldAssessmentDto(
                                CdsExportFieldAssessmentDto.Presence.VALUE,
                                Outcome.MAPPED,
                                "MyClinicalNotesContent",
                                "EXPLICIT_ARCHIVED_NOTE_STATEMENT"));
            }
            // Says what the note is attached to before its text; the details below carry every
            // revision's links exactly.
            if (!attachedTo.isEmpty())
                full.append(attachmentStatement(attachedTo)).append("\n\n");
            for (int i = 0; i < bodies.size(); i++) {
                if (bodies.size() > 1)
                    full.append(i == 0 ? "[Original note]\n" : "\n\n[Revision " + (i + 1) + "]\n");
                full.append(bodies.get(i));
            }
            full.append("\n\n[CARLOS source note details]\n").append(json);
            text.put("MyClinicalNotesContent", full.toString());
            String observed = current.note().value().fields().get("observation_date");
            if (observed != null) dates.put("EventDateTime", clock(observed, zone));
        } else if (category == CdsCategory.PERSONAL_HISTORY) {
            text.put("NativePersonalNarrative", body);
        } else {
            String path = bodyPath(category);
            int cap = bodyMaximum(category);
            int notesCap = category == CdsCategory.FAMILY_HISTORY ? 32000 : 64000;
            var notes = new ArrayList<String>();
            if (body.codePointCount(0, body.length()) <= cap) put(text, path, body);
            else if (category != CdsCategory.ALERTS_AND_SPECIAL_NEEDS) {
                if (!CdsExportNativeFieldAssessments.legal(body, notesCap))
                    review("CDS_EXPORT_NATIVE_HISTORY_NARRATIVE_CAPACITY");
                notes.add(body);
                // Notes alone is not the item's text to a CARLOS importer; the versioned carrier
                // lets it restore the narrative instead of a placeholder.
                text.put("NativeNarrative", body);
                var note = current.note().value();
                var key = new CdsExportNativeFieldKeyDto(note.table(), note.recordKey(), "note");
                var prior = decisions.get(key);
                decisions.put(
                        key,
                        new CdsExportFieldAssessmentDto(
                                prior.presence(), prior.outcome(), "Notes", prior.rule()));
            } else review("CDS_EXPORT_NATIVE_HISTORY_NARRATIVE_CAPACITY");
            notes.addAll(overflow);
            // The statements alone, for an imported item whose narrative is unchanged: the native
            // reconciler adds them to the imported Notes (CdsExportHistoryNativeReconciler).
            if (!overflow.isEmpty()) text.put(STATEMENTS, String.join("\n\n", overflow));
            if (!notes.isEmpty()) {
                String joined = String.join("\n\n", notes);
                if (!CdsExportNativeFieldAssessments.legal(
                        joined,
                        category == CdsCategory.ALERTS_AND_SPECIAL_NEEDS ? 1000 : notesCap))
                    review("CDS_EXPORT_NATIVE_HISTORY_NARRATIVE_CAPACITY");
                put(text, "Notes", joined);
            }
        }
        if (decisions.values().stream().anyMatch(v -> v.outcome() == Outcome.REQUIRES_REVIEW))
            review("CDS_EXPORT_NATIVE_HISTORY_UNMAPPED_FIELD");
        for (var provider : providers.values())
            if (decisions.keySet().stream()
                    .anyMatch(
                            key ->
                                    key.table().equals("provider")
                                            && key.recordKey().equals(provider.recordKey())))
                add(rows, provider);
        return new Result(
                new CdsExportNativeHistoryDto(
                        category,
                        text,
                        dates,
                        coding,
                        participants,
                        json,
                        archived),
                decisions,
                new ArrayList<>(rows.values()));
    }

    /**
     * The record kinds a note link ({@code casemgmt_note_link.table_name}) can name, as {@code
     * CaseManagementNoteLink} numbers them. A number CARLOS does not define is still carried
     * exactly; only its label is generic.
     */
    private static final Map<String, String> LINKED_RECORDS =
            Map.ofEntries(
                    Map.entry("1", "clinical note"),
                    Map.entry("2", "prescription"),
                    Map.entry("3", "allergy"),
                    Map.entry("4", "lab result"),
                    Map.entry("5", "document"),
                    Map.entry("6", "eForm"),
                    Map.entry("7", "patient record"),
                    Map.entry("8", "prevention"),
                    Map.entry("9", "lab result"),
                    Map.entry("10", "tickler"),
                    Map.entry("11", "appointment"),
                    Map.entry("12", "e-mail"));

    /**
     * The records one revision is attached to ({@code casemgmt_note_link}), every column of every
     * link row accounted: the link and parent identities are operational, and the record type,
     * record id and secondary id travel exactly in the revision's source context (the note
     * details of a ClinicalNotes entry, the {@code CARLOS.NativeHistory.v1} residual of a CPP
     * item). Nothing of the linked record itself is read here.
     */
    private static List<Map<String, String>> attachments(
            CdsExportHistoryFieldReconciler.Current revision,
            CdsExportCapturedTextRowDto note,
            CdsCategory category,
            Map<String, CdsExportCapturedTextRowDto> rows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions)
            throws IOException {
        var result = new ArrayList<Map<String, String>>();
        for (var link : revision.noteLinks()) {
            CdsExportCaseManagementProjectionEvidence.verify(link.value(), link.witness());
            var row = link.value();
            if (!note.fields().get("note_id").equals(row.fields().get("note_id"))) invalid();
            add(rows, row);
            var state = new CdsExportNativeFieldAssessments(row, decisions);
            state.operational("id", "NATIVE_NOTE_LINK_IDENTITY");
            state.operational("note_id", "VERIFIED_NATIVE_NOTE_LINK_PARENT");
            String type = state.raw("table_name"),
                    record = state.raw("table_id"),
                    part = state.raw("other_id");
            // Both are NOT NULL integer columns; anything else is not a row CARLOS wrote.
            if (type == null
                    || !type.matches("-?[0-9]{1,11}")
                    || record == null
                    || !record.matches("-?[0-9]{1,11}")) invalid();
            if (part != null && !CdsExportNativeFieldAssessments.legal(part, 25))
                review("CDS_EXPORT_NATIVE_NOTE_LINK_TEXT_INVALID");
            var facts = new LinkedHashMap<String, String>();
            facts.put("record", LINKED_RECORDS.getOrDefault(type, "record type " + type));
            facts.put("table_name", type);
            facts.put("table_id", record);
            // SQL NULL stays distinct from an empty secondary id.
            facts.put("other_id", part);
            for (String field : List.of("table_name", "table_id", "other_id"))
                state.set(field, Outcome.MAPPED, metadataPath(category), "EXACT_NATIVE_NOTE_LINK");
            result.add(facts);
        }
        return result;
    }

    /** Names, at the head of a ClinicalNotes text, the records its current revision is attached to. */
    static String attachmentStatement(List<Map<String, String>> attached) {
        var names = new ArrayList<String>();
        for (var facts : attached) {
            String name = facts.get("record") + " " + facts.get("table_id");
            String part = facts.get("other_id");
            if (part != null && !part.isEmpty()) name += " (part " + part + ")";
            names.add(name);
        }
        return attached.size() == 1
                ? "[CARLOS: this note is attached to another record in the sending system: "
                        + names.getFirst()
                        + ".]"
                : "[CARLOS: this note is attached to other records in the sending system: "
                        + String.join("; ", names)
                        + ".]";
    }

    static CdsCategory issueCategory(CdsExportHistoryFieldReconciler.Row definition)
            throws IOException {
        if (definition == null) invalid();
        String code = definition.value().fields().get("code");
        return code == null
                ? CdsCategory.PROBLEM_LIST
                : TAGS.getOrDefault(code, CdsCategory.PROBLEM_LIST);
    }

    static boolean cppDefinition(CdsExportHistoryFieldReconciler.Row definition) {
        String code = definition.value().fields().get("code");
        return code != null && TAGS.containsKey(code);
    }

    /**
     * A catalogue entry that files notes rather than coding a diagnosis: one of the six CPP tags,
     * or any {@code issue.type = 'system'} marker (OMeds, TicklerNote, eyeform*, PatientLog, ...).
     */
    static boolean systemDefinition(CdsExportHistoryFieldReconciler.Row definition) {
        return cppDefinition(definition)
                || "system".equals(definition.value().fields().get("type"));
    }

    /** A patient diagnosis need not have a linked narrative note to be part of the chart. */
    public Result mapIssue(
            int patient,
            CdsExportHistoryFieldReconciler.Row issue,
            CdsExportHistoryFieldReconciler.Row definition)
            throws IOException {
        return mapIssue(patient, issue, definition, List.of(), program -> false);
    }

    /**
     * Maps one standalone patient issue. Its {@code program_id} follows the same rule as an issue
     * linked to a note in {@link #map}: no program, the program of every note revision linked to
     * it (each of those notes is itself authorized under its program), or a program the exporting
     * identities work in ({@code readableProgram}, a fresh check). An issue of another program
     * that exported notes link follows those notes' access, as the chart shows it beside them,
     * and its program is carried ({@link #FOREIGN_PROGRAM_ISSUE_RULE}). Only an issue of a
     * foreign program that no exported note links (one picked in the encounter editor and never
     * saved with a note) is held for review.
     *
     * @param linked every captured note revision that links this patient issue
     */
    public Result mapIssue(
            int patient,
            CdsExportHistoryFieldReconciler.Row issue,
            CdsExportHistoryFieldReconciler.Row definition,
            List<CdsExportHistoryFieldReconciler.Current> linked,
            java.util.function.Predicate<String> readableProgram)
            throws IOException {
        if (issue == null
                || definition == null
                || patient < 1
                || linked == null
                || readableProgram == null) invalid();
        CdsExportCaseManagementProjectionEvidence.verify(issue.value(), issue.witness());
        CdsExportCaseManagementProjectionEvidence.verify(definition.value(), definition.witness());
        if (!Integer.toString(patient).equals(issue.value().fields().get("demographic_no"))
                || !definition
                        .value()
                        .fields()
                        .get("issue_id")
                        .equals(issue.value().fields().get("issue_id"))
                // System markers are carried by their notes, never as a standalone problem.
                || systemDefinition(definition)) invalid();
        String program = issue.value().fields().get("program_id");
        boolean linkedProgram = !linked.isEmpty();
        for (var revision : linked) {
            var note = revision.note().value();
            CdsExportCaseManagementProjectionEvidence.verify(note, revision.note().witness());
            if (!Integer.toString(patient).equals(note.fields().get("demographic_no"))
                    || revision.links().stream()
                            .noneMatch(
                                    link ->
                                            issue.value()
                                                    .fields()
                                                    .get("id")
                                                    .equals(link.fields().get("id")))) invalid();
            if (!Objects.equals(program, note.fields().get("program_no"))) linkedProgram = false;
        }
        boolean followsNotes = false;
        if (scopedProgram(program) && !linkedProgram && !readableProgram.test(program)) {
            if (linked.isEmpty()) review("CDS_EXPORT_NATIVE_ISSUE_PROGRAM_REQUIRES_REVIEW");
            followsNotes = true;
        }
        var category = issueCategory(definition);
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var metadata = new LinkedHashMap<String, Object>();
        for (var row : List.of(issue.value(), definition.value())) {
            var state = new CdsExportNativeFieldAssessments(row, fields);
            var attributes = new LinkedHashMap<String, String>();
            for (var entry : row.fields().entrySet()) {
                if (followsNotes
                        && row.table().equals(issue.value().table())
                        && entry.getKey().equals("program_id")) {
                    attributes.put("program_id", entry.getValue());
                    state.set(
                            "program_id", Outcome.MAPPED, "ResidualInfo", FOREIGN_PROGRAM_ISSUE_RULE);
                } else if (Set.of(
                                "id",
                                "issue_id",
                                "demographic_no",
                                "program_id",
                                "priority",
                                "role",
                                "sortOrderId")
                        .contains(entry.getKey()))
                    state.operational(
                            entry.getKey(),
                            "VERIFIED_NATIVE_ISSUE_IDENTITY_ACCESS_OR_CATALOGUE_ORDER");
                else {
                    if (entry.getValue() != null) {
                        if (!CdsExportNativeFieldAssessments.legal(entry.getValue(), 1 << 24))
                            review("CDS_EXPORT_NATIVE_ISSUE_TEXT_INVALID");
                        attributes.put(entry.getKey(), entry.getValue());
                    }
                    state.set(
                            entry.getKey(),
                            Outcome.MAPPED,
                            "ResidualInfo",
                            "EXACT_NATIVE_PATIENT_ISSUE_ATTRIBUTE");
                }
            }
            metadata.put(row.table(), attributes);
        }
        var text = new LinkedHashMap<String, String>();
        CdsExportNativeHistoryDto.Code coding = null;
        if (category == CdsCategory.PROBLEM_LIST) {
            var source = definition.value();
            String description = source.fields().get("description"),
                    code = source.fields().get("code"),
                    system = source.fields().get("type");
            if (description != null && !description.isEmpty()) {
                String destination =
                        description.codePointCount(0, description.length()) <= 250
                                ? "ProblemDiagnosisDescription"
                                : "Notes";
                if (!CdsExportNativeFieldAssessments.legal(
                        description, destination.equals("Notes") ? 64000 : 250))
                    review("CDS_EXPORT_NATIVE_ISSUE_DESCRIPTION_CAPACITY");
                text.put(destination, description);
                fields.put(
                        new CdsExportNativeFieldKeyDto(
                                source.table(), source.recordKey(), "description"),
                        new CdsExportFieldAssessmentDto(
                                CdsExportFieldAssessmentDto.Presence.VALUE,
                                Outcome.MAPPED,
                                destination,
                                "EXACT_NATIVE_DIAGNOSIS_DESCRIPTION"));
            }
            if (code != null && !code.isEmpty()) {
                if (!token(code, 20)
                        || !CdsExportNativeFieldAssessments.legal(system, 250)
                        || system.isEmpty())
                    review("CDS_EXPORT_NATIVE_HISTORY_CODE_REQUIRES_REVIEW");
                coding =
                        new CdsExportNativeHistoryDto.Code(
                                system, code, codeDescription(source.fields()));
            }
            String resolved = issue.value().fields().get("resolved");
            if (resolved != null) {
                if (!Set.of("0", "1").contains(resolved))
                    review("CDS_EXPORT_NATIVE_ISSUE_STATUS_REQUIRES_REVIEW");
                text.put("ProblemStatus", resolved.equals("1") ? "Resolved" : "Active");
            }
        }
        String json = JSON.writeValueAsString(versioned("patientIssue", metadata));
        return new Result(
                new CdsExportNativeHistoryDto(category, text, Map.of(), coding, List.of(), json),
                fields,
                List.of(issue.value(), definition.value()));
    }

    /** A program value that scopes a record; SQL NULL, empty and {@code 0} carry no program. */
    private static boolean scopedProgram(String program) {
        return program != null && !program.isEmpty() && !program.equals("0");
    }

    private static CdsExportPublicProviderDto provider(
            CdsExportNativeFieldAssessments state,
            String field,
            Map<String, CdsExportCapturedTextRowDto> providers,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions,
            CdsCategory category,
            boolean signing)
            throws IOException {
        String id = state.raw(field);
        if (id == null || id.isEmpty() || id.equals("0")) {
            if (id != null && !id.isEmpty())
                state.operational(field, "NO_NATIVE_PUBLIC_PROVIDER_ASSERTION");
            return null;
        }
        var row = providers.get(id);
        if (row == null) review("CDS_EXPORT_NATIVE_NOTE_PROVIDER_REQUIRED");
        var mapped =
                new CdsExportPublicProviderMapper()
                        .map(
                                row,
                                signing
                                        ? CdsExportPublicProviderMapper.Target.NOTE_SIGNER
                                        : CdsExportPublicProviderMapper.Target.NOTE_PARTICIPANT);
        if (mapped.value() == null) review("CDS_EXPORT_NATIVE_NOTE_PROVIDER_REQUIRES_REVIEW");
        mapped.fields()
                .forEach(
                        (key, value) ->
                                decisions.put(
                                        key,
                                        new CdsExportFieldAssessmentDto(
                                                value.presence(),
                                                value.outcome(),
                                                category == CdsCategory.CLINICAL_NOTES && !signing
                                                        ? "ParticipatingProviders"
                                                        : metadataPath(category),
                                                value.rule())));
        state.set(
                field,
                Outcome.MAPPED,
                category == CdsCategory.CLINICAL_NOTES && !signing
                        ? "ParticipatingProviders"
                        : metadataPath(category),
                "EXACT_CAPTURED_NATIVE_NOTE_PUBLIC_PROVIDER");
        return mapped.value();
    }

    private static Map<String, String> publicName(CdsExportPublicProviderDto p) {
        var result = new LinkedHashMap<String, String>();
        result.put("firstName", p.firstName());
        result.put("lastName", p.lastName());
        if (p.ohipNumber() != null) result.put("ohip", p.ohipNumber());
        return result;
    }

    /**
     * The versioned details object in one fixed key order. Map.of iterates in a per-JVM salted
     * order, which would change the bytes of an unchanged item between runs and workers.
     */
    private static Map<String, Object> versioned(String key, Object value) {
        var context = new LinkedHashMap<String, Object>();
        context.put("version", 1);
        context.put(key, value);
        return context;
    }

    /**
     * The distinct diagnosis codes linked to the current revision of a FamilyHistory, PastHealth
     * or ProblemList item, in link order; empty for any other category.
     */
    private static List<CdsExportNativeHistoryDto.Code> codes(
            CdsCategory category, CdsExportHistoryFieldReconciler.Current current)
            throws IOException {
        if (!Set.of(CdsCategory.FAMILY_HISTORY, CdsCategory.PAST_HEALTH, CdsCategory.PROBLEM_LIST)
                .contains(category)) return List.of();
        var codes = new LinkedHashSet<CdsExportNativeHistoryDto.Code>();
        for (var link : current.links()) {
            var issue = current.patientIssues().get(link.fields().get("id"));
            var definition =
                    current.definitions()
                            .get(issue.value().fields().get("issue_id"))
                            .value()
                            .fields();
            String value = definition.get("code");
            // A system marker (a CPP tag, OMeds, TicklerNote, ...) is not a diagnosis code; its
            // complete facts stay in the revision context.
            if (value == null
                    || value.isEmpty()
                    || TAGS.containsKey(value)
                    || "system".equals(definition.get("type"))) continue;
            String system = definition.get("type");
            if (system == null
                    || !token(value, 20)
                    || !CdsExportNativeFieldAssessments.legal(system, 250))
                review("CDS_EXPORT_NATIVE_HISTORY_CODE_REQUIRES_REVIEW");
            codes.add(
                    new CdsExportNativeHistoryDto.Code(system, value, codeDescription(definition)));
        }
        return List.copyOf(codes);
    }

    /** Names every diagnosis code of an item, one per line, for its Notes. */
    static String codedDiagnoses(List<CdsExportNativeHistoryDto.Code> codes) {
        var lines = new ArrayList<String>();
        for (var code : codes)
            lines.add(
                    code.system()
                            + " "
                            + code.value()
                            + (code.description().isEmpty() ? "" : " " + code.description()));
        return "Coded diagnoses:\n" + String.join("\n", lines);
    }

    /**
     * The issue description for StandardCodeDescription, an xs:token. A description with edge,
     * repeated or line-break spaces (the seeded ICSW420, for one) is written in the form a
     * schema-aware reader keeps anyway: spaces collapsed and trimmed (A26). The exact catalogue
     * description travels in the native context with the issue's other facts.
     */
    private static String codeDescription(Map<String, String> definition) throws IOException {
        String description = definition.get("description");
        if (description == null) return "";
        String normalized = tokenValue(description);
        if (!token(normalized, 250))
            review("CDS_EXPORT_NATIVE_HISTORY_CODE_DESCRIPTION_REQUIRES_REVIEW");
        return normalized;
    }

    /** The xs:token value of a string: tab, line feed and carriage return become spaces, runs collapse, edges trim. */
    static String tokenValue(String value) {
        return value.replaceAll("[\t\n\r ]+", " ").strip();
    }

    private static void extension(
            CdsCategory category,
            String key,
            String value,
            String date,
            Map<String, String> text,
            Map<String, CdsExportDateDto> dates,
            Set<String> overflow)
            throws IOException {
        String datePath =
                switch (key) {
                    case "Start Date" ->
                            switch (category) {
                                case FAMILY_HISTORY, RISK_FACTORS -> "StartDate";
                                case PAST_HEALTH -> "OnsetOrEventDate";
                                case PROBLEM_LIST -> "OnsetDate";
                                case ALERTS_AND_SPECIAL_NEEDS -> "DateActive";
                                default -> null;
                            };
                    case "Resolution Date" ->
                            switch (category) {
                                case PAST_HEALTH -> "ResolvedDate";
                                case PROBLEM_LIST -> "ResolutionDate";
                                case RISK_FACTORS, ALERTS_AND_SPECIAL_NEEDS -> "EndDate";
                                default -> null;
                            };
                    case "Procedure Date" ->
                            category == CdsCategory.PAST_HEALTH ? "ProcedureDate" : null;
                    default -> null;
                };
        if (datePath != null && date != null) {
            var parsed = partialDate(date, value);
            var old = dates.putIfAbsent(datePath, parsed);
            if (old != null && !old.equals(parsed))
                review("CDS_EXPORT_NATIVE_HISTORY_EXTENSION_CONFLICT");
            return;
        }
        if (value == null || value.isEmpty()) return;
        String target =
                switch (key) {
                    case "Age at Onset" ->
                            category == CdsCategory.FAMILY_HISTORY
                                    ? "AgeAtOnset"
                                    : category == CdsCategory.RISK_FACTORS ? "AgeOfOnset" : null;
                    case "Treatment" -> category == CdsCategory.FAMILY_HISTORY ? "Treatment" : null;
                    case "Problem Status" ->
                            Set.of(CdsCategory.PAST_HEALTH, CdsCategory.PROBLEM_LIST)
                                            .contains(category)
                                    ? "ProblemStatus"
                                    : null;
                    case "Exposure Details" ->
                            category == CdsCategory.RISK_FACTORS ? "ExposureDetails" : null;
                    case "Relationship" ->
                            category == CdsCategory.FAMILY_HISTORY ? "Relationship" : null;
                    case "Life Stage" ->
                            Set.of(
                                                    CdsCategory.FAMILY_HISTORY,
                                                    CdsCategory.PAST_HEALTH,
                                                    CdsCategory.PROBLEM_LIST,
                                                    CdsCategory.RISK_FACTORS)
                                            .contains(category)
                                    ? "LifeStage"
                                    : null;
                    case "Problem Description" ->
                            category == CdsCategory.PROBLEM_LIST ? "ProblemDescription" : null;
                    default -> null;
                };
        if (target == null) return;
        int maximum =
                switch (target) {
                    case "Treatment", "ProblemDescription" -> 250;
                    case "ExposureDetails" -> 120;
                    case "LifeStage" -> 1;
                    default -> 50;
                };
        if (target.equals("LifeStage")) {
            // A select (N, I, C, T, A); any other value is not one CARLOS's editor writes.
            if (!Set.of("N", "I", "C", "T", "A").contains(value))
                review("CDS_EXPORT_NATIVE_HISTORY_LIFE_STAGE_REQUIRES_REVIEW");
            put(text, target, value);
            return;
        }
        boolean fits = CdsExportNativeFieldAssessments.legal(value, maximum);
        // AgeAtOnset is an xs:nonNegativeInteger and AgeOfOnset an xs:positiveInteger, but the CPP
        // editor's Age at Onset is free text ("childhood", "40s", a risk factor's "0").
        if (target.equals("AgeAtOnset") || target.equals("AgeOfOnset"))
            fits &=
                    value.matches("[0-9]+")
                            && !(target.equals("AgeOfOnset")
                                    && new java.math.BigInteger(value).signum() == 0);
        if (!fits) {
            // A25: the CPP editor sets no maximum length or format on these boxes. A value the
            // element cannot hold exactly is omitted from it, never truncated; it is written,
            // labelled, in the item's Notes for every receiver, and exactly in the revision's
            // extension facts in CARLOS.NativeHistory.v1.
            overflow.add(key + ":\n" + value);
            return;
        }
        put(text, target, value);
    }

    private static void put(Map<String, String> text, String path, String value)
            throws IOException {
        var old = text.putIfAbsent(path, value);
        if (old != null && !old.equals(value))
            review("CDS_EXPORT_NATIVE_HISTORY_EXTENSION_CONFLICT");
    }

    static CdsExportDateDto clock(String value, ZoneId zone) throws IOException {
        try {
            if (value.length() == 10)
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.DATE, LocalDate.parse(value).toString());
            var local = LocalDateTime.parse(value.replace(' ', 'T'));
            if (zone == null) review("CDS_EXPORT_NATIVE_HISTORY_ZONE_REQUIRED");
            var offsets = zone.getRules().getValidOffsets(local);
            if (offsets.size() != 1) review("CDS_EXPORT_NATIVE_HISTORY_CLOCK_AMBIGUOUS");
            if (offsets.getFirst().getTotalSeconds() % 60 != 0)
                review("CDS_EXPORT_NATIVE_HISTORY_OFFSET_NOT_REPRESENTABLE");
            return new CdsExportDateDto(
                    CdsExportDateDto.Precision.DATE_TIME,
                    java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                            local.atOffset(offsets.getFirst())));
        } catch (DateTimeException invalid) {
            review("CDS_EXPORT_NATIVE_HISTORY_DATE_REQUIRES_REVIEW");
            return null;
        }
    }

    /**
     * A stored instant (whole or fractional seconds since the epoch, as {@code UNIX_TIMESTAMP}
     * renders a TIMESTAMP column) as a date-time in the clinic zone. Unlike a local wall-clock
     * text, an instant is never ambiguous at a daylight-saving change and never depends on the
     * database session zone. The zero TIMESTAMP (no time recorded) requires review.
     */
    static CdsExportDateDto instant(String value, ZoneId zone) throws IOException {
        if (zone == null) review("CDS_EXPORT_NATIVE_HISTORY_ZONE_REQUIRED");
        if (value == null || !value.matches("[0-9]{1,12}(\\.[0-9]{1,6})?"))
            review("CDS_EXPORT_NATIVE_HISTORY_DATE_REQUIRES_REVIEW");
        int dot = value.indexOf('.');
        long seconds = Long.parseLong(dot < 0 ? value : value.substring(0, dot));
        long nanos =
                dot < 0
                        ? 0
                        : Long.parseLong((value.substring(dot + 1) + "000000000").substring(0, 9));
        if (seconds == 0 && nanos == 0) review("CDS_EXPORT_NATIVE_HISTORY_DATE_REQUIRES_REVIEW");
        try {
            var local = java.time.Instant.ofEpochSecond(seconds, nanos).atZone(zone);
            if (local.getOffset().getTotalSeconds() % 60 != 0)
                review("CDS_EXPORT_NATIVE_HISTORY_OFFSET_NOT_REPRESENTABLE");
            return new CdsExportDateDto(
                    CdsExportDateDto.Precision.DATE_TIME,
                    java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                            local.toOffsetDateTime()));
        } catch (DateTimeException invalid) {
            review("CDS_EXPORT_NATIVE_HISTORY_DATE_REQUIRES_REVIEW");
            return null;
        }
    }

    /** The native CPP writer stores a date anchor and an explicit precision marker together. */
    static CdsExportDateDto partialDate(String stored, String marker) throws IOException {
        try {
            var local =
                    stored.length() == 10
                            ? LocalDate.parse(stored).atStartOfDay()
                            : LocalDateTime.parse(stored.replace(' ', 'T'));
            if (!local.toLocalTime().equals(LocalTime.MIDNIGHT))
                review("CDS_EXPORT_NATIVE_HISTORY_DATE_HAS_CLOCK");
            var date = local.toLocalDate();
            if (marker == null || marker.isEmpty())
                return new CdsExportDateDto(CdsExportDateDto.Precision.DATE, date.toString());
            if (marker.equals("YYYY") && date.getDayOfYear() == 1)
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.YEAR, java.time.Year.from(date).toString());
            if (marker.equals("YYYY-MM") && date.getDayOfMonth() == 1)
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.YEAR_MONTH,
                        java.time.YearMonth.from(date).toString());
            review("CDS_EXPORT_NATIVE_HISTORY_DATE_PRECISION_CONFLICT");
        } catch (DateTimeException malformed) {
            review("CDS_EXPORT_NATIVE_HISTORY_DATE_REQUIRES_REVIEW");
        }
        return null;
    }

    private static String bodyPath(CdsCategory c) {
        return switch (c) {
            case PERSONAL_HISTORY -> "ResidualInfo";
            case FAMILY_HISTORY -> "ProblemDiagnosisProcedureDescription";
            case PAST_HEALTH -> "PastHealthProblemDescriptionOrProcedures";
            case PROBLEM_LIST -> "ProblemDiagnosisDescription";
            case RISK_FACTORS -> "RiskFactor";
            case ALERTS_AND_SPECIAL_NEEDS -> "AlertDescription";
            case CLINICAL_NOTES -> "MyClinicalNotesContent";
            default -> throw new IllegalArgumentException();
        };
    }

    private static int bodyMaximum(CdsCategory c) {
        return c == CdsCategory.RISK_FACTORS
                ? 120
                : c == CdsCategory.ALERTS_AND_SPECIAL_NEEDS ? 1000 : 250;
    }

    private static String metadataPath(CdsCategory c) {
        return c == CdsCategory.CLINICAL_NOTES ? "MyClinicalNotesContent" : "ResidualInfo";
    }

    private static boolean token(String value, int max) {
        return CdsExportNativeFieldAssessments.legal(value, max)
                && value.equals(value.strip())
                && !value.contains("  ")
                && value.codePoints().noneMatch(c -> c == '\t' || c == '\r' || c == '\n');
    }

    private static void add(
            Map<String, CdsExportCapturedTextRowDto> rows, CdsExportCapturedTextRowDto row)
            throws IOException {
        var old = rows.putIfAbsent(row.table() + "/" + row.recordKey(), row);
        if (old != null && !old.equals(row)) invalid();
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NATIVE_HISTORY_SCOPE_INVALID");
    }
}
