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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
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
     * The CPP editor's value fields (newEncounterLayout.jsp, CaseManagementEntry2Action
     * {@code issueNoteSave}, NotesService), beside its three dates. "Hide Cpp" is not one: it is
     * a display option of the chart's CPP boxes, as a prescription's hide-from-CPP flag is, and
     * travels only in the item's revision context. Other keys (the importer's {@code cdsDate*}
     * metadata rows) are not clinical values either.
     */
    private static final Set<String> CPP_VALUES =
            Set.of(
                    "Age at Onset",
                    "Treatment",
                    "Problem Status",
                    "Exposure Details",
                    "Relationship",
                    "Life Stage",
                    "Problem Description",
                    "Procedure");

    private static final Set<String> CPP_DATES =
            Set.of("Start Date", "Resolution Date", "Procedure Date");

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

    /**
     * The Notes limit of a CPP category: text32K, text64K or text1K, counted as the migration
     * guide counts them (k = 1,000), which is also what CdsExportXmlValidator enforces on the
     * finished document. The XSD aliases admit 1,024 multiples; a value between the two would
     * pass here and fail there.
     */
    static int notesLimit(CdsCategory category) {
        return switch (category) {
            case FAMILY_HISTORY -> 32000;
            case ALERTS_AND_SPECIAL_NEEDS -> 1000;
            default -> 64000;
        };
    }

    /** Characters a Notes part takes, with the blank line that separates it from the next. */
    private static int length(String part) {
        return part == null ? 0 : part.codePointCount(0, part.length()) + 2;
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

    /**
     * Rule of a diagnosis description written with U+FFFD for each character XML cannot carry;
     * the exact description travels in the item's {@code CARLOS.NativeHistory.v1} details. The
     * same rule name as a vaccine value written that way.
     */
    static final String DESCRIPTION_REPLACED_RULE =
            "CHARACTER_XML_CANNOT_CARRY_REPLACED_EXACT_IN_RESIDUAL";

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
            var category = tag(definition.value());
            if (category != null) categories.add(category);
        }
        return category(categories);
    }

    /** The CPP section a catalogue entry files its notes under, or null for any other entry. */
    static CdsCategory tag(CdsExportCapturedTextRowDto definition) {
        String code = definition.fields().get("code");
        return code == null ? null : TAGS.get(code);
    }

    /**
     * The section of a note whose linked catalogue entries carry these CPP tags: the one tag, or
     * ClinicalNotes for none. A note tagged for two sections has no single wire home.
     */
    static CdsCategory category(Set<CdsCategory> categories) throws IOException {
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
        return map(
                patient,
                category,
                revisions,
                providers,
                zone,
                readableProgram,
                "refiledImportedSource",
                refiledSource);
    }

    /**
     * As {@link #map(int, CdsCategory, List, Map, ZoneId, java.util.function.Predicate)}, for an
     * imported item exported from its native revisions because its import unit kept no baseline
     * for a row CARLOS has changed in place ({@link CdsExportHistoryFieldReconciler#UNPROVEN_BASELINE}):
     * {@code importedSource} ({@link CdsExportHistoryFieldReconciler#importedSource}) travels in
     * the item's native context under {@code importedSource}.
     */
    Result mapCarryingImportedSource(
            int patient,
            CdsCategory category,
            List<CdsExportHistoryFieldReconciler.Current> revisions,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone,
            java.util.function.Predicate<String> readableProgram,
            Map<String, Object> importedSource)
            throws IOException {
        return map(
                patient,
                category,
                revisions,
                providers,
                zone,
                readableProgram,
                "importedSource",
                Objects.requireNonNull(importedSource));
    }

    private Result map(
            int patient,
            CdsCategory category,
            List<CdsExportHistoryFieldReconciler.Current> revisions,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone,
            java.util.function.Predicate<String> readableProgram,
            String sourceKey,
            Map<String, Object> importedSource)
            throws IOException {
        Objects.requireNonNull(readableProgram);
        if (patient < 1
                || revisions.isEmpty()
                || !CdsExportNoteSourceReader.CATEGORIES.contains(category)) invalid();
        var current = revisions.getLast();
        // The caller decides the category from the same captured links of the current revision
        // (CdsExportHistoryCurrentContext.category, by this class's own rule), and an imported
        // item re-filed into another section is exported under that current section
        // (CdsExportHistoryOccurrenceService.refiled). Another category is an integrity fault,
        // never a chart condition (it was CDS_EXPORT_NATIVE_HISTORY_CATEGORY_CHANGED_REQUIRES_REVIEW).
        if (category(current) != category) invalid();
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
        // The current revision's extension rows, placed once every row is read (extensions()).
        var currentExtensions = new ArrayList<Extension>();
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
            // The revisions the planner joined by their stored uuid (CdsExportNoteRevisionPlanner
            // .lineage): every one carries the same, and a note without one is alone in its chain.
            String revisionUuid = CdsExportNoteRevisionPlanner.lineage(note.fields().get("uuid"));
            if (revisionUuid == null
                    ? revisions.size() != 1
                    : uuid != null && !uuid.equals(revisionUuid)) invalid();
            uuid = revisionUuid;
            add(rows, note);
            var state = new CdsExportNativeFieldAssessments(note, decisions);
            for (String field : OPERATIONAL)
                state.operational(field, "VERIFIED_NATIVE_NOTE_IDENTITY_ACCESS_OR_PRESENTATION");
            String exactBody = state.raw("note");
            if (exactBody == null) review("CDS_EXPORT_NATIVE_NOTE_BODY_REQUIRED");
            // A note is typed or pasted, and a paste can hold a character XML cannot carry. It
            // holds nobody: the text is written with U+FFFD in that character's place
            // (CdsExportXmlText) and the exact text travels in this revision's details, as JSON.
            String body = CdsExportXmlText.element(exactBody);
            if (!exactBody.isEmpty())
                state.set(
                        "note",
                        Outcome.MAPPED,
                        bodyPath(category),
                        body.equals(exactBody)
                                ? "EXACT_NATIVE_TEXT"
                                : "NOTE_TEXT_WITH_U_FFFD_EXACT_IN_REVISION_CONTEXT");
            bodies.add(body);
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
                    // These travel only in the revision details (JSON): no character and no
                    // length is refused.
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
            else if (recordedProvider(state.raw("signing_provider_no")))
                detail.put("signingProviderNo", state.raw("signing_provider_no"));
            // A provider number with no provider record (one of migrated data whose provider was
            // deleted there; CARLOS deletes no provider) names no public author: the stored
            // number travels exactly here, as an appointment's does in its source record.
            if (author == null && recordedProvider(state.raw("provider_no")))
                detail.put("providerNo", state.raw("provider_no"));
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
                    // DateTimeNoteCreated is required: a revision time that is not a calendar
                    // time gives no participant (the author and time stay in the details).
                    var time = storedClock(updated, zone);
                    // One participant per distinct author, at that author's first revision. Every
                    // revision's author and time stay in the note details. A repeated entry for
                    // the same author made a receiving CARLOS treat the note as multi-author and
                    // store it with no author at all.
                    if (time != null
                            && participants.stream().noneMatch(p -> p.provider().equals(author)))
                        participants.add(new CdsExportNativeHistoryDto.Participant(author, time));
                }
            }
            String encounter = state.raw("encounter_type");
            if (encounter != null && !encounter.isEmpty()) {
                // NoteType is an xs:token of at most 50 characters. The encounter screen and the
                // REST API store the type as sent (varchar(100)), so one that is not such a token
                // (longer, or with a tab, line break or doubled space) is left out of NoteType;
                // the exact type is in the note's details either way.
                boolean noteType = category == CdsCategory.CLINICAL_NOTES && token(encounter, 50);
                detail.put("encounterType", encounter);
                state.set(
                        "encounter_type",
                        Outcome.MAPPED,
                        noteType ? "NoteType" : metadataPath(category),
                        "EXACT_NATIVE_ENCOUNTER_TYPE");
                if (revision == current && noteType) text.put("NoteType", encounter);
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
                // A value's characters and length are never refused: it is exact in the details below
                // (JSON) and extension() writes it for a person with U+FFFD where XML cannot
                // carry one. The key is one of the editor's own field names.
                if (!CdsExportNativeFieldAssessments.legal(key, 255)
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
                if (revision == current) currentExtensions.add(new Extension(key, value, date));
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
                detail.put("narrative", exactBody);
            else if (!body.equals(exactBody)) detail.put("exactNarrative", exactBody);
            metadata.add(detail);
        }
        // An encounter note that carries a body's exact text states every body's length as
        // written, so a CARLOS importer places each body exactly (a body can quote a heading)
        // before it restores the carried text (CdsHistoryPersister.exactNoteText).
        if (category == CdsCategory.CLINICAL_NOTES
                && metadata.stream().anyMatch(detail -> detail.containsKey("exactNarrative")))
            for (int i = 0; i < bodies.size(); i++)
                metadata.get(i)
                        .put(CdsNativeExtensionContract.NOTE_BODY_LENGTH, bodies.get(i).length());
        var context = versioned("revisions", metadata);
        if (importedSource != null) context.put(sourceKey, importedSource);
        // A character XML cannot carry is its JSON escape; the decoded details are unchanged.
        String json = CdsExportResidualJson.text(JSON.writeValueAsString(context));
        extensions(category, currentExtensions, text, dates, overflow);
        var codes = codes(category, current);
        // DiagnosisProcedureCode and DiagnosisCode hold one code. An item with several assigned
        // diagnoses (A24) writes none of them there rather than choosing one: every code is
        // listed in Notes, and each issue's complete facts stay in the revision context. So is
        // a code the element cannot carry exactly (an issue the Issue admin screen created has
        // no coding system), which held the patient (CDS_EXPORT_NATIVE_HISTORY_CODE_REQUIRES_REVIEW).
        var coding =
                codes.size() == 1 && standardCode(codes.getFirst().system(), codes.getFirst().value())
                        ? codes.getFirst()
                        : null;
        if (coding == null && !codes.isEmpty()) overflow.add(codedDiagnoses(codes));
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
                // One rule for both sides: the importer finds a revision's body by its heading
                // when it restores the exact text of a body written with U+FFFD.
                if (bodies.size() > 1)
                    full.append(CdsNativeExtensionContract.noteRevisionHeading(i));
                full.append(bodies.get(i));
            }
            full.append(CdsNativeExtensionContract.NOTE_DETAILS_HEADING).append(json);
            text.put("MyClinicalNotesContent", full.toString());
            // An observation time that is not a calendar time (MariaDB's zero date, which the
            // packaged non-strict sql_mode stores for a time the column cannot hold) gives no
            // EventDateTime; the stored value is in the details. It held the patient
            // (CDS_EXPORT_NATIVE_HISTORY_DATE_REQUIRES_REVIEW).
            var event =
                    storedClock(current.note().value().fields().get("observation_date"), zone);
            if (event != null) dates.put("EventDateTime", event);
        } else if (category == CdsCategory.PERSONAL_HISTORY) {
            text.put("NativePersonalNarrative", body);
            // PersonalHistory has no element but ResidualInfo, so no Notes either: its CPP values
            // follow the narrative in the plain-text copy (CdsExportNativeHistoryXmlWriter).
            if (!overflow.isEmpty()) text.put(STATEMENTS, String.join("\n\n", overflow));
        } else {
            String path = bodyPath(category);
            int cap = bodyMaximum(category);
            // The statements alone, for an imported item whose narrative is unchanged: the native
            // reconciler adds them to the imported Notes (CdsExportHistoryNativeReconciler).
            String statements = overflow.isEmpty() ? null : String.join("\n\n", overflow);
            if (statements != null) text.put(STATEMENTS, statements);
            // Notes holds whole parts only, never a cut one. Its limit, less the statement that
            // leads an archived item's Notes (CdsExportNativeHistoryXmlWriter), goes first to
            // the item's statements: values of its own fields (Treatment, Exposure Details...)
            // that their elements cannot hold. Statements that do not fit together are left
            // out whole, last first; every one is exact in the revision's extension facts in
            // CARLOS.NativeHistory.v1. Whatever room remains is for the narrative below.
            int limit = notesLimit(category);
            if (archived) limit -= length(ARCHIVED_ITEM_STATEMENT);
            var kept = new ArrayList<>(overflow);
            while (!kept.isEmpty() && length(String.join("\n\n", kept)) - 2 > limit)
                kept.removeLast();
            int room = limit - (kept.isEmpty() ? 0 : length(String.join("\n\n", kept)));
            var notes = new ArrayList<String>();
            if (body.codePointCount(0, body.length()) <= cap) put(text, path, body);
            else {
                // The description element cannot hold the text, so the item has none and its
                // complete narrative travels in CARLOS.History.Narrative.v1, which a CARLOS
                // importer restores. The same text is written in Notes for every other receiver
                // when Notes has room for it. When it has not (an alert, whose Notes are no
                // longer than its description, or a narrative past the Notes limit), the carrier
                // alone holds it: no length holds the patient and nothing is cut.
                text.put("NativeNarrative", body);
                boolean readable =
                        category != CdsCategory.ALERTS_AND_SPECIAL_NEEDS
                                && body.codePointCount(0, body.length()) <= room;
                if (readable) notes.add(body);
                var note = current.note().value();
                var key = new CdsExportNativeFieldKeyDto(note.table(), note.recordKey(), "note");
                var prior = decisions.get(key);
                decisions.put(
                        key,
                        new CdsExportFieldAssessmentDto(
                                prior.presence(),
                                prior.outcome(),
                                readable ? "Notes" : "ResidualInfo",
                                prior.rule()));
            }
            notes.addAll(kept);
            if (!notes.isEmpty()) put(text, "Notes", String.join("\n\n", notes));
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
            // other_id (varchar(25)) is carried exactly in the details, where a character XML
            // cannot carry is its JSON escape; the statement naming the record shows U+FFFD
            // (attachmentStatement). It held the patient (CDS_EXPORT_NATIVE_NOTE_LINK_TEXT_INVALID).
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
            if (part != null && !part.isEmpty())
                name += " (part " + CdsExportXmlText.element(part) + ")";
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
        return tag(definition.value()) != null;
    }

    /**
     * A catalogue entry that files notes rather than coding a diagnosis: one of the six CPP tags,
     * or any {@code issue.type = 'system'} marker (OMeds, TicklerNote, eyeform*, PatientLog, ...).
     */
    static boolean systemDefinition(CdsExportHistoryFieldReconciler.Row definition) {
        return systemDefinition(definition.value());
    }

    /** {@link #systemDefinition(CdsExportHistoryFieldReconciler.Row)} of the entry's captured row. */
    static boolean systemDefinition(CdsExportCapturedTextRowDto definition) {
        return tag(definition) != null || "system".equals(definition.fields().get("type"));
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
        var programs = new ArrayList<String>();
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
            programs.add(note.fields().get("program_no"));
        }
        return mapLinkedIssue(patient, issue, definition, programs, readableProgram);
    }

    /**
     * {@link #mapIssue(int, CdsExportHistoryFieldReconciler.Row,
     * CdsExportHistoryFieldReconciler.Row, List, java.util.function.Predicate)} for a caller that
     * has already verified the linked note revisions and holds only what the rule reads of them:
     * the program of each. An issue that thousands of encounter notes link is then mapped without
     * loading those notes.
     *
     * @param linkedPrograms {@code program_no} of every captured note revision that links this
     *     patient issue, one entry a revision (an entry may be null)
     */
    Result mapLinkedIssue(
            int patient,
            CdsExportHistoryFieldReconciler.Row issue,
            CdsExportHistoryFieldReconciler.Row definition,
            Collection<String> linkedPrograms,
            java.util.function.Predicate<String> readableProgram)
            throws IOException {
        if (issue == null
                || definition == null
                || patient < 1
                || linkedPrograms == null
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
        boolean linkedProgram = !linkedPrograms.isEmpty();
        for (String noteProgram : linkedPrograms)
            if (!Objects.equals(program, noteProgram)) linkedProgram = false;
        boolean followsNotes = false;
        if (scopedProgram(program) && !linkedProgram && !readableProgram.test(program)) {
            if (linkedPrograms.isEmpty()) review("CDS_EXPORT_NATIVE_ISSUE_PROGRAM_REQUIRES_REVIEW");
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
                    // Carried in the residual JSON: no character and no length is refused.
                    if (entry.getValue() != null) attributes.put(entry.getKey(), entry.getValue());
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
                // The Issue admin screen stores the description as typed or pasted, and a paste
                // can hold a character XML cannot carry. It holds nobody: the description is
                // written with U+FFFD in that character's place (one for one, so its length is
                // unchanged) and travels exactly in this item's CARLOS.NativeHistory.v1 details
                // (patientIssue.issue.description), from which a CARLOS importer restores it once
                // the element proves to be its U+FFFD form.
                String shown = CdsExportXmlText.element(description);
                boolean fits = shown.codePointCount(0, shown.length()) <= 250;
                String destination = fits ? "ProblemDiagnosisDescription" : "Notes,ResidualInfo";
                if (fits) text.put("ProblemDiagnosisDescription", shown);
                else {
                    // issue.description is varchar(255), so it always fits Notes. A receiving
                    // CARLOS files a ProblemList item without a description from its narrative
                    // carrier (CARLOS.History.Narrative.v1), as it does a long CPP problem.
                    text.put("Notes", shown);
                    text.put("NativeNarrative", shown);
                }
                fields.put(
                        new CdsExportNativeFieldKeyDto(
                                source.table(), source.recordKey(), "description"),
                        new CdsExportFieldAssessmentDto(
                                CdsExportFieldAssessmentDto.Presence.VALUE,
                                Outcome.MAPPED,
                                destination,
                                shown.equals(description)
                                        ? "EXACT_NATIVE_DIAGNOSIS_DESCRIPTION"
                                        : DESCRIPTION_REPLACED_RULE));
            }
            // A code or coding system no DiagnosisCode carries exactly (an issue the Issue admin
            // screen created, which stores no coding system; a code with irregular spacing or a
            // character XML cannot carry) is left out of DiagnosisCode, as a Disease Registry
            // code is: it travels exactly in the item's details with the rest of the catalogue
            // row (issue.code, issue.type).
            if (code != null && !code.isEmpty() && standardCode(system, code))
                coding =
                        new CdsExportNativeHistoryDto.Code(
                                system, code, codeDescription(source.fields()));
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
        if (row == null) {
            // No provider record for the number (data migrated from a system that deleted the
            // provider; CARLOS deletes none): no public author or signer, as an appointment's
            // provider without a record has no Provider. The caller carries the stored number
            // in the revision details. It held the patient (CDS_EXPORT_NATIVE_NOTE_PROVIDER_REQUIRED).
            state.set(
                    field,
                    Outcome.MAPPED,
                    metadataPath(category),
                    "NATIVE_PROVIDER_NUMBER_WITHOUT_PROVIDER_RECORD_CARRIED");
            return null;
        }
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
     * or ProblemList item, in link order; empty for any other category. A code no DiagnosisCode
     * carries exactly ({@link #standardCode}) is listed too, with an empty system when the
     * catalogue row stores none: the item then names it in Notes instead of coding it.
     */
    private static List<CdsExportNativeHistoryDto.Code> codes(
            CdsCategory category, CdsExportHistoryFieldReconciler.Current current) {
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
            codes.add(
                    new CdsExportNativeHistoryDto.Code(
                            system == null ? "" : system, value, codeDescription(definition)));
        }
        return List.copyOf(codes);
    }

    /**
     * Whether DiagnosisCode carries this coding system and code exactly: a coding system that is
     * not blank (StandardCodingSystem, 250 characters, keeps its spaces) and a code that is an
     * {@code xs:token} of at most 20 characters, so no schema-aware reader changes it. The Issue
     * admin screen stores no coding system (it has no box for one) and takes any code.
     */
    static boolean standardCode(String system, String code) {
        return system != null
                && !system.isBlank()
                && CdsExportNativeFieldAssessments.legal(system, 250)
                && code != null
                && token(code, 20);
    }

    /**
     * Names every diagnosis code of an item, one per line, for its Notes: the coding system (none
     * when the catalogue row stores none), the code and its description, each with U+FFFD for a
     * character XML cannot carry. The exact catalogue rows stay in the item's details.
     */
    static String codedDiagnoses(List<CdsExportNativeHistoryDto.Code> codes) {
        var lines = new ArrayList<String>();
        for (var code : codes)
            lines.add(
                    (code.system().isBlank() ? "" : CdsExportXmlText.element(code.system()) + " ")
                            + CdsExportXmlText.element(code.value())
                            + (code.description().isEmpty() ? "" : " " + code.description()));
        return "Coded diagnoses:\n" + String.join("\n", lines);
    }

    /**
     * The issue description for StandardCodeDescription, an xs:token of at most 250 characters.
     * A description with edge, repeated or line-break spaces (the seeded ICSW420, for one) is
     * written in the form a schema-aware reader keeps anyway: spaces collapsed and trimmed (A26).
     * A character XML cannot carry is U+FFFD in its place. A description longer than the element
     * (issue.description holds 255 characters) is written empty, as a Disease Registry code's
     * is: the element is required. The exact catalogue description travels in the native
     * context with the issue's other facts in every case.
     */
    private static String codeDescription(Map<String, String> definition) {
        String description = definition.get("description");
        if (description == null) return "";
        String normalized = tokenValue(CdsExportXmlText.element(description));
        return token(normalized, 250) ? normalized : "";
    }

    /** The xs:token value of a string: tab, line feed and carriage return become spaces, runs collapse, edges trim. */
    static String tokenValue(String value) {
        return value.replaceAll("[\t\n\r ]+", " ").strip();
    }

    /**
     * The plain-text copy of a PersonalHistory item ({@code CARLOS.History.NarrativeText.v1}):
     * its narrative, then its {@link #STATEMENTS}, as the xs:token value residual Content keeps.
     * Other receivers show {@code CARLOS.History.Narrative.v1} as an escaped JSON string. Empty
     * when there is neither.
     *
     * @param narrative the item's exact narrative, or null when none is written
     * @param statements the item's labelled CPP values, or null
     */
    static String readableText(String narrative, String statements) {
        var parts = new ArrayList<String>();
        if (narrative != null) parts.add(narrative);
        if (statements != null) parts.add(statements);
        return tokenValue(String.join("\n\n", parts));
    }

    /** One extension row of the current revision: its key, value and date, as stored. */
    private record Extension(String key, String value, String date) {
        @Override
        public String toString() {
            return "CdsExportNativeHistoryMapper.Extension[protected]";
        }
    }

    /**
     * Places the current revision's CPP extension values, each key decided from all its rows: in
     * the section's standard element when it has one and the key's rows give it one value that
     * fits, otherwise labelled in {@code overflow} (the item's Notes, or the plain-text copy of a
     * PersonalHistory item, which has no Notes). The editor shows fields a section has no element
     * for (Medical History's Treatment and Procedure, Family History's Resolution Date, Social
     * History's dates), and a re-filed item keeps the values of its former section; none is left
     * only in the CARLOS revision context.
     *
     * <p>A key whose rows hold different values (two Relationship rows of one revision, which a
     * note kept from older OSCAR data can carry: {@code copyRevisionMetadata} copies duplicate
     * legacy keys, and the chart's screens read different rows of them) writes none of them in
     * the element rather than choosing one, as several assigned diagnoses do (A24): each value
     * is written, labelled, in Notes, and every row is exact in the revision's extension facts.
     * It held the patient ({@code CDS_EXPORT_NATIVE_HISTORY_EXTENSION_CONFLICT}).
     */
    private static void extensions(
            CdsCategory category,
            List<Extension> rows,
            Map<String, String> text,
            Map<String, CdsExportDateDto> dates,
            Set<String> overflow)
            throws IOException {
        // An encounter note carries every extension in its own text (the revision details).
        if (category == CdsCategory.CLINICAL_NOTES) return;
        var byKey = new LinkedHashMap<String, List<Extension>>();
        for (var row : rows) byKey.computeIfAbsent(row.key(), ignored -> new ArrayList<>()).add(row);
        for (var entry : byKey.entrySet()) {
            String key = entry.getKey();
            if (CPP_DATES.contains(key)) dateExtension(category, key, entry.getValue(), dates, overflow);
            else valueExtension(category, key, entry.getValue(), text, overflow);
        }
    }

    /**
     * A CPP date key: its rows as the chart shows them ({@link #partialDate}). A row with no date
     * names nothing; one whose stored date is not a calendar day gives nothing either and stays
     * exact in the extension facts.
     */
    private static void dateExtension(
            CdsCategory category,
            String key,
            List<Extension> rows,
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
        var shown = new LinkedHashSet<CdsExportDateDto>();
        for (var row : rows) {
            // A date row stores the date and its precision marker; with no date it names nothing.
            if (row.date() == null) continue;
            var parsed = partialDate(row.date(), row.value());
            if (parsed != null) shown.add(parsed);
        }
        if (shown.isEmpty()) return;
        if (datePath != null && shown.size() == 1) {
            if (dates.putIfAbsent(datePath, shown.iterator().next()) != null) invalid();
            return;
        }
        // No date element in this section (a family history's Resolution Date, a social
        // history's dates), or rows of one key with different dates: each written in its
        // precision, labelled, like any other value.
        for (var date : shown) overflow.add(key + ":\n" + date.lexical());
    }

    /** A CPP value key (Age at Onset, Treatment, ...): see {@link #extensions}. */
    private static void valueExtension(
            CdsCategory category,
            String key,
            List<Extension> rows,
            Map<String, String> text,
            Set<String> overflow)
            throws IOException {
        var values = new LinkedHashSet<String>();
        for (var row : rows)
            if (row.value() != null && !row.value().isEmpty()) values.add(row.value());
        if (values.isEmpty()) return;
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
        if (target == null) {
            // A CPP field with no element in this section (Medical History's Treatment and
            // Procedure, ...) is written, labelled, as a value too long for its element is.
            // Other keys (Hide Cpp, the importer's cdsDate* rows) are not clinical values.
            if (CPP_VALUES.contains(key))
                for (String value : values)
                    overflow.add(key + ":\n" + CdsExportXmlText.element(value));
            return;
        }
        String value = values.size() == 1 ? values.iterator().next() : null;
        if (value != null && fits(target, value)) {
            put(text, target, value);
            return;
        }
        // A25: the CPP editor sets no maximum length or format on these boxes. A value the
        // element cannot hold exactly, and the different values of one key's rows, are omitted
        // from it, never truncated or chosen between; each is written, labelled, in the item's
        // Notes for every receiver, and exactly in the revision's extension facts in
        // CARLOS.NativeHistory.v1.
        for (String each : values) overflow.add(key + ":\n" + CdsExportXmlText.element(each));
    }

    /** Whether the CPP element {@code target} holds {@code value} exactly. */
    private static boolean fits(String target, String value) {
        // The editor's select (N, I, C, T, A) is LifeStage's list. The REST notes service stores
        // the value it is sent (NotesService), so any other value is written, labelled, in the
        // item's Notes, as a value too long for its element is.
        if (target.equals("LifeStage")) return Set.of("N", "I", "C", "T", "A").contains(value);
        int maximum =
                switch (target) {
                    case "Treatment", "ProblemDescription" -> 250;
                    case "ExposureDetails" -> 120;
                    default -> 50;
                };
        boolean fits = CdsExportNativeFieldAssessments.legal(value, maximum);
        // AgeAtOnset is an xs:nonNegativeInteger and AgeOfOnset an xs:positiveInteger, but the CPP
        // editor's Age at Onset is free text ("childhood", "40s", a risk factor's "0").
        if (target.equals("AgeAtOnset") || target.equals("AgeOfOnset"))
            fits &=
                    value.matches("[0-9]+")
                            && !(target.equals("AgeOfOnset")
                                    && new java.math.BigInteger(value).signum() == 0);
        return fits;
    }

    /** One element's text; each element has one source, so a second value is an integrity fault. */
    private static void put(Map<String, String> text, String path, String value)
            throws IOException {
        var old = text.putIfAbsent(path, value);
        if (old != null && !old.equals(value)) invalid();
    }

    /**
     * A stored wall-clock time (a DATETIME column, or a lab time with no offset) as a date-time in
     * the clinic zone, with that zone's offset at the time.
     *
     * <p>A local time with no single whole-minute offset in the zone is written as the wall-clock
     * time with no UTC offset, the rule an appointment's time follows
     * ({@code LOCAL_WALL_CLOCK_TIME_AT_DAYLIGHT_SAVING_TRANSITION}): a time inside the
     * spring-forward gap, one inside the fall-back hour that occurs twice, and one before the
     * zone's standard time (a local mean time offset with seconds). A note written in the repeated
     * hour, or an observation time a clinician typed there, held the patient
     * ({@code CDS_EXPORT_NATIVE_HISTORY_CLOCK_AMBIGUOUS}). An xs:dateTime without a zone is valid,
     * a receiver reads it as local time, and no offset is ever invented; the stored value still
     * travels exactly in the record's native details.
     */
    static CdsExportDateDto clock(String value, ZoneId zone) throws IOException {
        try {
            if (value.length() == 10)
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.DATE, LocalDate.parse(value).toString());
            var local = LocalDateTime.parse(value.replace(' ', 'T'));
            if (zone == null) review("CDS_EXPORT_NATIVE_HISTORY_ZONE_REQUIRED");
            var offsets = zone.getRules().getValidOffsets(local);
            if (offsets.size() != 1 || offsets.getFirst().getTotalSeconds() % 60 != 0)
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.DATE_TIME,
                        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(local));
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

    /**
     * A CPP date row as the chart shows it ({@code viewNotes.jsp} {@code readPartialDate}): the
     * marker {@code YYYY} keeps the year and {@code YYYY-MM} the year-month of the stored date,
     * and any other marker or none is the stored full date. The CPP editor stores a marker with
     * the first day of its year or month ({@code CaseManagementEntry2Action.writePartialDate});
     * a date that is not that day, or a marker the editor does not write, held the patient
     * ({@code CDS_EXPORT_NATIVE_HISTORY_DATE_PRECISION_CONFLICT}) and is now what the chart shows.
     * The row stays exact in the revision's extension facts either way.
     *
     * @return null when the stored value is not a calendar day (MariaDB's zero date, which the
     *     packaged non-strict {@code sql_mode} stores for a date the column cannot hold, as the
     *     REST notes service can save for a year it is sent); it held the patient
     *     ({@code CDS_EXPORT_NATIVE_HISTORY_DATE_REQUIRES_REVIEW})
     */
    static CdsExportDateDto partialDate(String stored, String marker) {
        var date = calendarDay(stored);
        if (date == null) return null;
        if ("YYYY".equals(marker))
            return new CdsExportDateDto(
                    CdsExportDateDto.Precision.YEAR,
                    String.format(Locale.ROOT, "%04d", date.getYear()));
        if ("YYYY-MM".equals(marker))
            return new CdsExportDateDto(
                    CdsExportDateDto.Precision.YEAR_MONTH,
                    String.format(
                            Locale.ROOT, "%04d-%02d", date.getYear(), date.getMonthValue()));
        return new CdsExportDateDto(CdsExportDateDto.Precision.DATE, date.toString());
    }

    /**
     * The calendar day of a stored DATE or DATETIME value (the chart shows a CPP date's day, so
     * a clock, which a DATE column never holds, is not part of it), or null when the value is not
     * a calendar day of the Common Era: MariaDB's zero date, a zero month or day, year 0.
     */
    private static LocalDate calendarDay(String stored) {
        if (stored == null) return null;
        try {
            var date =
                    stored.length() == 10
                            ? LocalDate.parse(stored)
                            : LocalDateTime.parse(stored.replace(' ', 'T')).toLocalDate();
            return date.getYear() < 1 ? null : date;
        } catch (DateTimeException notACalendarDay) {
            return null;
        }
    }

    /**
     * {@link #clock} of a stored DATETIME, or null when the value is not a calendar time of the
     * Common Era (MariaDB's zero date, which the packaged non-strict {@code sql_mode} stores for
     * a time the column cannot hold). The stored value travels exactly in the revision details
     * either way, so no element is written for it rather than holding the patient.
     */
    static CdsExportDateDto storedClock(String value, ZoneId zone) throws IOException {
        return calendarDay(value) == null ? null : clock(value, zone);
    }

    /** A provider column that names a provider: not SQL NULL, empty or {@code 0}. */
    private static boolean recordedProvider(String id) {
        return id != null && !id.isEmpty() && !id.equals("0");
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
