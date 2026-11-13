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
        long previous = 0;
        String uuid = null;
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
                    participants.add(
                            new CdsExportNativeHistoryDto.Participant(
                                    author, clock(updated, zone)));
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
                if (revision == current) extension(category, key, value, date, text, dates);
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
                if (issueProgram != null
                        && !issueProgram.isEmpty()
                        && !issueProgram.equals("0")
                        && !issueProgram.equals(note.fields().get("program_no")))
                    review("CDS_EXPORT_NATIVE_ISSUE_PROGRAM_REQUIRES_REVIEW");
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
                    if (Set.of("id", "demographic_no", "issue_id", "program_id")
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
            if (revision != current || category == CdsCategory.PERSONAL_HISTORY)
                detail.put("narrative", body == null ? "" : body);
            metadata.add(detail);
        }
        String json = JSON.writeValueAsString(Map.of("version", 1, "revisions", metadata));
        var coding = code(category, current);
        String body = bodies.getLast();
        if (category == CdsCategory.CLINICAL_NOTES) {
            var full = new StringBuilder();
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
            if (body.codePointCount(0, body.length()) <= cap) put(text, path, body);
            else if (category != CdsCategory.ALERTS_AND_SPECIAL_NEEDS) {
                int notesCap = category == CdsCategory.FAMILY_HISTORY ? 32000 : 64000;
                if (!CdsExportNativeFieldAssessments.legal(body, notesCap))
                    review("CDS_EXPORT_NATIVE_HISTORY_NARRATIVE_CAPACITY");
                put(text, "Notes", body);
                var note = current.note().value();
                var key = new CdsExportNativeFieldKeyDto(note.table(), note.recordKey(), "note");
                var prior = decisions.get(key);
                decisions.put(
                        key,
                        new CdsExportFieldAssessmentDto(
                                prior.presence(), prior.outcome(), "Notes", prior.rule()));
            } else review("CDS_EXPORT_NATIVE_HISTORY_NARRATIVE_CAPACITY");
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
                        "1".equals(current.note().value().fields().get("archived"))),
                decisions,
                new ArrayList<>(rows.values()));
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

    /** A patient diagnosis need not have a linked narrative note to be part of the chart. */
    public Result mapIssue(
            int patient,
            CdsExportHistoryFieldReconciler.Row issue,
            CdsExportHistoryFieldReconciler.Row definition)
            throws IOException {
        if (issue == null || definition == null || patient < 1) invalid();
        CdsExportCaseManagementProjectionEvidence.verify(issue.value(), issue.witness());
        CdsExportCaseManagementProjectionEvidence.verify(definition.value(), definition.witness());
        if (!Integer.toString(patient).equals(issue.value().fields().get("demographic_no"))
                || !definition
                        .value()
                        .fields()
                        .get("issue_id")
                        .equals(issue.value().fields().get("issue_id"))) invalid();
        String program = issue.value().fields().get("program_id");
        if (program != null && !program.isEmpty() && !program.equals("0"))
            review("CDS_EXPORT_NATIVE_ISSUE_PROGRAM_REQUIRES_REVIEW");
        var category = issueCategory(definition);
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var metadata = new LinkedHashMap<String, Object>();
        for (var row : List.of(issue.value(), definition.value())) {
            var state = new CdsExportNativeFieldAssessments(row, fields);
            var attributes = new LinkedHashMap<String, String>();
            for (var entry : row.fields().entrySet()) {
                if (Set.of(
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
        String json = JSON.writeValueAsString(Map.of("version", 1, "patientIssue", metadata));
        return new Result(
                new CdsExportNativeHistoryDto(category, text, Map.of(), coding, List.of(), json),
                fields,
                List.of(issue.value(), definition.value()));
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

    private static CdsExportNativeHistoryDto.Code code(
            CdsCategory category, CdsExportHistoryFieldReconciler.Current current)
            throws IOException {
        if (!Set.of(CdsCategory.FAMILY_HISTORY, CdsCategory.PAST_HEALTH, CdsCategory.PROBLEM_LIST)
                .contains(category)) return null;
        var codes = new LinkedHashSet<CdsExportNativeHistoryDto.Code>();
        for (var link : current.links()) {
            var issue = current.patientIssues().get(link.fields().get("id"));
            var definition =
                    current.definitions()
                            .get(issue.value().fields().get("issue_id"))
                            .value()
                            .fields();
            String value = definition.get("code");
            if (value == null || value.isEmpty() || TAGS.containsKey(value)) continue;
            String system = definition.get("type");
            if (system == null
                    || !token(value, 20)
                    || !CdsExportNativeFieldAssessments.legal(system, 250))
                review("CDS_EXPORT_NATIVE_HISTORY_CODE_REQUIRES_REVIEW");
            codes.add(
                    new CdsExportNativeHistoryDto.Code(system, value, codeDescription(definition)));
        }
        if (codes.size() > 1) review("CDS_EXPORT_NATIVE_HISTORY_MULTIPLE_DIAGNOSES_REQUIRE_REVIEW");
        return codes.isEmpty() ? null : codes.iterator().next();
    }

    private static String codeDescription(Map<String, String> definition) throws IOException {
        String description = definition.get("description");
        if (description == null) return "";
        if (!token(description, 250))
            review("CDS_EXPORT_NATIVE_HISTORY_CODE_DESCRIPTION_REQUIRES_REVIEW");
        return description;
    }

    private static void extension(
            CdsCategory category,
            String key,
            String value,
            String date,
            Map<String, String> text,
            Map<String, CdsExportDateDto> dates)
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
        if (!CdsExportNativeFieldAssessments.legal(value, maximum))
            review("CDS_EXPORT_NATIVE_HISTORY_EXTENSION_CAPACITY");
        if (target.equals("LifeStage") && !Set.of("N", "I", "C", "T", "A").contains(value))
            review("CDS_EXPORT_NATIVE_HISTORY_LIFE_STAGE_REQUIRES_REVIEW");
        if ((target.equals("AgeAtOnset") || target.equals("AgeOfOnset"))
                && (!value.matches("[0-9]+")
                        || target.equals("AgeOfOnset")
                                && new java.math.BigInteger(value).signum() == 0))
            review("CDS_EXPORT_NATIVE_HISTORY_AGE_REQUIRES_REVIEW");
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
