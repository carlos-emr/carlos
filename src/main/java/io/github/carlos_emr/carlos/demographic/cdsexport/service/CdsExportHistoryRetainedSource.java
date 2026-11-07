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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsHistoryNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Reconciles authenticated history source fields with their sealed native preparation.
 * Every known historical date, including non-projected resolution and reviewer dates, uses
 * the original import shift. Retained narrative and extensions stay in their proper wire
 * fields. Current native note/issue/revision reconciliation and fresh program authorization
 * remain separate prerequisites; this component cannot authorize emission by itself.
 */
public final class CdsExportHistoryRetainedSource {
    private static final Set<CdsCategory> CATEGORIES = Set.of(CdsCategory.PERSONAL_HISTORY, CdsCategory.FAMILY_HISTORY,
            CdsCategory.PAST_HEALTH, CdsCategory.PROBLEM_LIST, CdsCategory.RISK_FACTORS,
            CdsCategory.ALERTS_AND_SPECIAL_NEEDS, CdsCategory.CLINICAL_NOTES);
    private static final Set<String> CHOICES = Set.of("YearOnly[1]", "YearMonth[1]", "FullDate[1]", "FullDateTime[1]");
    public record Result(Set<String> sourceFields, Map<String, CdsExportDateDto> dates,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        public Result { sourceFields = Set.copyOf(sourceFields); dates = Map.copyOf(dates); changes = List.copyOf(changes); }
        @Override public String toString() { return "CdsExportHistoryRetainedSource.Result[protected]"; }
    }

    /** Binds the complete source-field set, original options and preparation to the same authenticated parent. */
    public Result read(int patient, CdsCategory category, String address, CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> supplied,
            CdsExportNoteAccessDto noteScope, CdsExportRetainedSectionWriter.Values values,
            io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl control) throws IOException {
        Objects.requireNonNull(control); control.checkpoint();
        var prepared = new CdsExportRetainedPreparationReader().history(index, address, category).value();
        var references = index.references().getOrDefault(address, List.of()).stream().filter(ref -> ref.table().equals("casemgmt_note")).toList();
        if (noteScope == null || noteScope.patient() != patient || references.size() != 1
                || !Long.toString(noteScope.noteId()).equals(references.getFirst().id())) invalid();
        control.authorizeNote(category, noteScope);
        var fields = new CdsExportRetainedOccurrenceFields().fieldsFor(patient, category, address, index, checked, supplied, control::checkpoint);
        var options = new CdsExportRetainedImportOptionsReader().fromCaptured(index, address.substring(0, 36));
        var result = readVerified(category, fields, options, prepared, values, control::checkpoint);
        control.authorizeNote(category, noteScope); control.checkpoint(); return result;
    }

    Result readVerified(CdsCategory category, List<CdsExportRetainedFieldDto> fields,
            CdsExportRetainedImportOptionsReader.Options options, CdsHistoryNativeDto prepared,
            CdsExportRetainedSectionWriter.Values values, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(values); Objects.requireNonNull(checkpoint); values.requireActive(); checkpoint.run();
        if (category == null || !CATEGORIES.contains(category) || options == null || prepared == null || fields == null || fields.isEmpty()) invalid();
        // Qualifies every path, namespace, scalar type, cardinality and occurrence before proposing changes.
        new CdsExportRetainedSectionWriter().prepare(fields, values, checkpoint);
        String root = CdsExportRetainedOccurrencePath.root(fields.getFirst(), category);
        String patientKey = fields.getFirst().sourcePatientKey();
        var relative = new LinkedHashMap<String, CdsExportRetainedFieldDto>(); var ids = new HashSet<String>();
        for (var field : fields) {
            checkpoint.run();
            if (!options.importRunId().equals(field.runId()) || !patientKey.equals(field.sourcePatientKey())
                    || !root.equals(CdsExportRetainedOccurrencePath.root(field, category)) || !ids.add(field.id())) invalid();
            String path = field.sourcePath().length() == root.length() ? "" : field.sourcePath().substring(root.length() + 1);
            if (relative.putIfAbsent(path, field) != null) invalid();
        }
        String body = body(category); var descriptor = body == null ? null : relative.get(body + "[1]");
        verifyBody(category, prepared, descriptor, values, checkpoint);
        Pattern datePath = datePattern(category); var temporal = new LinkedHashMap<String, CdsTemporalDto>();
        var dates = new LinkedHashMap<String, CdsExportDateDto>(); var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        for (var entry : relative.entrySet()) {
            checkpoint.run(); var match = datePath.matcher(entry.getKey()); if (!match.matches()) continue;
            String parent = match.group(1), choice = match.group(2);
            if (choice == null || !CHOICES.contains(choice)) invalid();
            String lexical = text(values, entry.getValue(), 128, checkpoint); CdsTemporalDto effective;
            try {
                var original = CdsTemporal.parse(choice.equals("YearOnly[1]") ? lexical : null, choice.equals("YearMonth[1]") ? lexical : null,
                        choice.equals("FullDate[1]") ? lexical : null, choice.equals("FullDateTime[1]") ? lexical : null, parent);
                effective = CdsTemporal.shift(original, options.timeshiftDays(), parent);
            } catch (RuntimeException invalid) { throw new IOException("CDS_EXPORT_HISTORY_SOURCE_DATE_REQUIRES_REVIEW"); }
            if (temporal.putIfAbsent(parent, effective) != null) invalid();
            var wire = new CdsExportPreparedTemporalMapper().map(effective, category == CdsCategory.CLINICAL_NOTES); dates.put(parent, wire);
            if (!lexical.equals(wire.lexical())) {
                String replacement = wire.lexical();
                changes.add(new CdsExportRetainedSectionWriter.Change(parent + "/" + choice, Set.of(entry.getValue().id()), xml -> xml.text(replacement)));
            }
        }
        String selected = switch (category) {
            case PERSONAL_HISTORY -> null;
            case FAMILY_HISTORY, RISK_FACTORS -> "StartDate";
            case PAST_HEALTH -> temporal.containsKey("OnsetOrEventDate[1]") ? "OnsetOrEventDate" : "ProcedureDate";
            case PROBLEM_LIST -> "OnsetDate";
            case ALERTS_AND_SPECIAL_NEEDS -> "DateActive";
            case CLINICAL_NOTES -> "EventDateTime";
            default -> throw new IOException("CDS_EXPORT_HISTORY_SOURCE_INVALID");
        };
        var observation = selected == null ? null : temporal.getOrDefault(selected + "[1]", CdsTemporalDto.unknown());
        if (!Objects.equals(selected, prepared.observationField()) || !Objects.equals(observation, prepared.observationDate()))
            throw new IOException("CDS_EXPORT_HISTORY_SOURCE_PREPARATION_MISMATCH");
        // Changes cannot overlap an unknown subtree or discard its retained scalar fields.
        new CdsExportRetainedSectionWriter().prepareChanges(fields, values, changes, checkpoint);
        values.requireActive(); checkpoint.run(); return new Result(ids, dates, changes);
    }

    private static Pattern datePattern(CdsCategory category) {
        String dates = switch (category) {
            case PERSONAL_HISTORY -> "(?!)";
            case FAMILY_HISTORY -> "StartDate";
            case PAST_HEALTH -> "(?:OnsetOrEventDate|ResolvedDate|ProcedureDate)";
            case PROBLEM_LIST -> "(?:OnsetDate|ResolutionDate)";
            case RISK_FACTORS -> "(?:StartDate|EndDate)";
            case ALERTS_AND_SPECIAL_NEEDS -> "(?:DateActive|EndDate)";
            case CLINICAL_NOTES -> "(?:EventDateTime|ParticipatingProviders\\[[1-9][0-9]{0,17}\\]/DateTimeNoteCreated|NoteReviewer\\[[1-9][0-9]{0,17}\\]/DateTimeNoteReviewed)";
            default -> throw new IllegalArgumentException("CDS_EXPORT_HISTORY_SOURCE_INVALID");
        };
        return Pattern.compile("(" + dates + "\\[1\\])(?:/(.*))?");
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
            default -> throw new IllegalArgumentException("CDS_EXPORT_HISTORY_SOURCE_INVALID");
        };
    }
    private static void verifyBody(CdsCategory category, CdsHistoryNativeDto prepared, CdsExportRetainedFieldDto body,
            CdsExportRetainedSectionWriter.Values values, Runnable checkpoint) throws IOException {
        String section = category.wireName();
        boolean structured = prepared.diagnostics().stream().anyMatch(value -> value.code().equals("CDS_HISTORY_STRUCTURED_SOURCE_FIELDS") && value.field().equals(section));
        boolean oversized = prepared.diagnostics().stream().anyMatch(value -> value.code().equals("CDS_NOTE_COMPLETE_TEXT_SOURCE_VIEW") && value.field().equals(body(category)));
        if (body == null) {
            if (!structured || oversized || !prepared.content().equals("[CDS " + section + ": no narrative body supplied. Separate clinical source fields retain this occurrence's complete values.]")) invalid();
        } else if (oversized) {
            if (structured || !prepared.content().equals("[CDS " + section + ": narrative exceeds the bounded native projection. Read the complete original and normalized values in this clinical category's CDS source fields. No narrative excerpt was substituted.]")) invalid();
        } else {
            if (structured) invalid(); int offset = 0; char[] buffer = new char[4096];
            try (Reader input = values.text(body)) {
                for (int count; (count = input.read(buffer)) != -1; ) {
                    checkpoint.run(); if (count == 0 || offset > prepared.content().length() - count) invalid();
                    for (int i = 0; i < count; i++) if (buffer[i] != prepared.content().charAt(offset++)) invalid();
                }
                if (offset != prepared.content().length()) invalid();
            } finally { Arrays.fill(buffer, '\0'); }
        }
    }
    private static String text(CdsExportRetainedSectionWriter.Values values, CdsExportRetainedFieldDto field, int maximum, Runnable checkpoint) throws IOException {
        var text = new StringBuilder(); char[] buffer = new char[128];
        try (Reader input = values.text(field)) {
            for (int count; (count = input.read(buffer)) != -1; ) {
                checkpoint.run(); if (count == 0 || text.length() > maximum - count) invalid(); text.append(buffer, 0, count);
            }
        } finally { Arrays.fill(buffer, '\0'); }
        return text.toString();
    }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_HISTORY_SOURCE_INVALID"); }
}
