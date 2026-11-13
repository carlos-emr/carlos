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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportAllergyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportAllergyMappingDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact native-only allergy interpretation. This never reads the database, infers a drug
 * authority from a lookup ID, calls severe life-threatening, or replaces recorded date with
 * a modification/import clock. Unresolved native fields and precision conflicts are explicit
 * blockers. Retained imported source and lookup/provider definitions require reconciliation
 * before a complete clinical section can be asserted.
 */
public final class CdsExportAllergyMapper {
    private static final String ARCHIVE =
            "ResidualInfo/" + CdsNativeExtensionContract.ALLERGY_ARCHIVED;

    public CdsExportAllergyMappingDto map(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> precision)
            throws IOException {
        var candidate = candidate(patient, row, precision);
        boolean blocked =
                candidate.fields().values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        return new CdsExportAllergyMappingDto(
                blocked ? null : candidate.value(), candidate.fields());
    }

    /**
     * CDS 5.2 allergies have no status element, and receivers ignore CARLOS residuals, so an
     * archived allergy (removed, replaced by an edit, or marked inactive; CARLOS does not
     * distinguish them) would otherwise reach another EMR as an active allergy. The standard
     * Notes says so in words; the CARLOS archive residual still carries the exact state.
     */
    static final String ARCHIVED_NOTE =
            "Inactive in the sending EMR: this allergy record was archived (removed, replaced by"
                    + " an edit, or marked inactive) and is not a current allergy.";

    /** Partial native values stay package-private; callers must retain every unresolved field disposition. */
    record Candidate(
            CdsExportAllergyDto value,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        Candidate {
            fields = Map.copyOf(fields);
        }

        @Override
        public String toString() {
            return "CdsExportAllergyMapper.Candidate[protected]";
        }
    }

    Candidate candidate(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> precision)
            throws IOException {
        shape(row, "allergies", CdsExportPatientTableReadSupport.Plan.ALLERGIES);
        long id = integer(row.fields().get("allergyid"));
        if (patient < 1
                || !Integer.toString(patient).equals(row.fields().get("demographic_no"))
                || !CdsExportPatientTableSourceReader.key(id).equals(row.recordKey())
                || precision.size() > 256) scope();
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var source = new State(row, decisions);
        source.operational("allergyid", "NATIVE_OCCURRENCE_IDENTITY");
        source.operational("demographic_no", "SEALED_PATIENT_IDENTITY");
        source.operational("lastUpdateDate", "CAPTURED_MODIFICATION_CLOCK_NOT_RECORDED_DATE");
        source.operational("position", "CAPTURED_DISPLAY_ORDER");
        String description = source.text("DESCRIPTION", "OffendingAgentDescription", 120);
        String reaction = source.text("reaction", "Reaction", 120);
        String property;
        String nonDrug = source.raw("nonDrug");
        if (nonDrug == null) {
            property = "UK";
            source.set(
                    "nonDrug",
                    Outcome.MAPPED,
                    "PropertyOfOffendingAgent",
                    "NATIVE_AGENT_PROPERTY_UNSPECIFIED");
        } else {
            property =
                    source.code(
                            "nonDrug", "PropertyOfOffendingAgent", Map.of("0", "DR", "1", "ND"));
            if (property == null)
                source.review(
                        "nonDrug", "PropertyOfOffendingAgent", "NATIVE_BOOLEAN_REQUIRES_REVIEW");
        }
        String archived = source.code("archived", ARCHIVE, Map.of("0", "false", "1", "true"));
        if (archived == null) source.review("archived", ARCHIVE, "ARCHIVE_STATE_REQUIRED");
        String reactionType =
                source.code("reaction_type", "ReactionType", Map.of("AL", "AL", "AR", "AR"));
        String life =
                source.code(
                        "life_stage",
                        "LifeStage",
                        Map.of("N", "N", "I", "I", "C", "C", "T", "T", "A", "A"));
        String severity =
                source.code(
                        "severity_of_reaction",
                        "Severity",
                        Map.of("1", "MI", "2", "MO", "5", "NO"));
        if (Set.of("0", "4")
                .contains(java.util.Objects.toString(source.raw("severity_of_reaction"), "")))
            source.set(
                    "severity_of_reaction",
                    Outcome.OPERATIONAL,
                    "Severity",
                    "NATIVE_SEVERITY_UNKNOWN_NO_ASSERTION");
        String nativeSeverity = null;
        if ("3".equals(source.raw("severity_of_reaction"))) {
            // "Severe" (AddReaction2.jsp) has no CDS 5.2 value and is not evidence of a
            // life-threatening (LT) reaction: the standard Severity stays absent and the exact
            // native label travels in a CARLOS residual, instead of refusing the patient.
            nativeSeverity = "3";
            source.set(
                    "severity_of_reaction",
                    Outcome.MAPPED,
                    "ResidualInfo/" + CdsNativeExtensionContract.ALLERGY_SEVERITY,
                    "NATIVE_SEVERE_HAS_NO_CDS_SEVERITY_VALUE");
        }
        String atcPath = "ResidualInfo/" + CdsNativeExtensionContract.ALLERGY_ATC;
        String type = null, code = source.token("atc", atcPath, 7);
        if (code != null) {
            if (CdsNativeExtensionContract.validAllergyAtc(code)) type = "ATC";
            else source.review("atc", atcPath, "SINGLE_ATC_CODE_REQUIRES_REVIEW");
        }
        if ("0".equals(source.raw("TYPECODE")))
            source.operational("TYPECODE", "NATIVE_CATALOGUE_TYPE_UNSPECIFIED");
        else if ("8".equals(source.raw("TYPECODE")) && type != null)
            source.set("TYPECODE", Outcome.MAPPED, atcPath, "EXPLICIT_ATC_CLASS_TYPE");
        String age =
                source.token(
                        "age_of_onset",
                        "ResidualInfo/" + CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET,
                        4);
        String onset =
                source.code(
                        "onset_of_reaction",
                        "ResidualInfo/" + CdsNativeExtensionContract.ALLERGY_ONSET_OF_REACTION,
                        CdsNativeExtensionContract.ALLERGY_ONSET_VALUES);
        if (onset != null) onset = source.raw("onset_of_reaction");
        if ("4".equals(source.raw("onset_of_reaction")))
            source.set(
                    "onset_of_reaction",
                    Outcome.OPERATIONAL,
                    "ResidualInfo/" + CdsNativeExtensionContract.ALLERGY_ONSET_OF_REACTION,
                    "NATIVE_ONSET_UNKNOWN_NO_ASSERTION");
        // All other populated coding/author fields retain their initial explicit
        // review disposition. Values or IDs alone do not establish the missing namespace,
        // lookup definition, recording clinician or additional clinical representation.
        if ((description == null || description.isBlank()) && code == null)
            source.review("DESCRIPTION", "OffendingAgentDescription", "ALLERGY_AGENT_REQUIRED");
        var dates = new LinkedHashMap<Integer, List<State>>();
        dates.put(1, new ArrayList<>());
        dates.put(2, new ArrayList<>());
        var seen = new java.util.HashSet<String>();
        for (var entry : precision) {
            shape(
                    entry,
                    "partial_date",
                    CdsExportPatientTableReadSupport.Plan.PARTIAL_DATE_ALLERGIES);
            if (!seen.add(entry.recordKey())
                    || !CdsExportPatientTableSourceReader.key(integer(entry.fields().get("id")))
                            .equals(entry.recordKey())
                    || !"1".equals(entry.fields().get("table_name"))
                    || integer(entry.fields().get("table_id")) != id) scope();
            var state = new State(entry, decisions);
            state.operational("id", "CAPTURED_PRECISION_ROW_IDENTITY");
            state.operational("table_name", "ALLERGY_PRECISION_OWNER_TYPE");
            state.operational("table_id", "VERIFIED_ALLERGY_PARENT_IDENTITY");
            String field = state.raw("field_name");
            if ("1".equals(field) || "2".equals(field)) {
                state.operational("field_name", "EXPLICIT_NATIVE_DATE_FIELD");
                dates.get(Integer.parseInt(field)).add(state);
            } else {
                state.review("field_name", "", "UNKNOWN_ALLERGY_PRECISION_FIELD");
                state.review("format", "", "UNKNOWN_ALLERGY_PRECISION_FIELD");
            }
        }
        CdsExportDateDto start = date(source, "start_date", "StartDate", dates.get(1));
        CdsExportDateDto recorded = date(source, "entry_date", "RecordedDate", dates.get(2));
        var absent = new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);
        var output =
                new CdsExportAllergyDto(
                        description,
                        property,
                        type,
                        type == null ? null : code,
                        reactionType,
                        start == null ? absent : start,
                        life,
                        severity,
                        reaction,
                        recorded == null ? absent : recorded,
                        Boolean.parseBoolean(archived) ? ARCHIVED_NOTE : null,
                        Boolean.parseBoolean(archived),
                        age,
                        onset,
                        nativeSeverity);
        return new Candidate(output, decisions);
    }

    private static CdsExportDateDto date(
            State source, String column, String path, List<State> metadata) {
        var formats = new ArrayList<String>();
        metadata.forEach(row -> formats.add(row.raw("format")));
        try {
            var value = new CdsExportNativeDateMapper().map(source.raw(column), formats);
            source.set(
                    column,
                    value.precision() == CdsExportDateDto.Precision.ABSENT
                            ? Outcome.ABSENT
                            : Outcome.MAPPED,
                    path,
                    "EXPLICIT_CAPTURED_DATE_PRECISION");
            metadata.forEach(
                    row ->
                            row.set(
                                    "format",
                                    Outcome.MAPPED,
                                    path,
                                    "EXPLICIT_CAPTURED_DATE_PRECISION"));
            return value;
        } catch (IOException review) {
            source.review(column, path, "DATE_PRECISION_REQUIRES_REVIEW");
            metadata.forEach(row -> row.review("format", path, "DATE_PRECISION_REQUIRES_REVIEW"));
            return null;
        }
    }

    private static void shape(
            CdsExportCapturedTextRowDto row,
            String table,
            CdsExportPatientTableReadSupport.Plan plan)
            throws IOException {
        if (row == null
                || !table.equals(row.table())
                || !row.fields().keySet().equals(Set.copyOf(plan.columns()))) scope();
    }

    private static long integer(String value) throws IOException {
        try {
            int number = Integer.parseInt(value);
            if (!Integer.toString(number).equals(value)) scope();
            return number;
        } catch (NumberFormatException invalid) {
            scope();
            return 0;
        }
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_ALLERGY_CAPTURE_SCOPE_INVALID");
    }

    private static final class State {
        private final CdsExportCapturedTextRowDto row;
        private final Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions;

        private State(
                CdsExportCapturedTextRowDto row,
                Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions) {
            this.row = row;
            this.decisions = decisions;
            row.fields()
                    .forEach(
                            (column, value) ->
                                    set(
                                            column,
                                            value == null || value.isEmpty()
                                                    ? Outcome.ABSENT
                                                    : Outcome.REQUIRES_REVIEW,
                                            "",
                                            "UNMAPPED_NATIVE_VALUE"));
        }

        private String raw(String column) {
            if (!row.fields().containsKey(column))
                throw new IllegalArgumentException("CDS_EXPORT_NATIVE_COLUMN_UNKNOWN");
            return row.fields().get(column);
        }

        private void set(String column, Outcome outcome, String destination, String rule) {
            String value = raw(column);
            var presence =
                    value == null
                            ? Presence.SQL_NULL
                            : value.isEmpty() ? Presence.EMPTY : Presence.VALUE;
            decisions.put(
                    new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column),
                    new CdsExportFieldAssessmentDto(presence, outcome, destination, rule));
        }

        private void operational(String column, String rule) {
            set(column, Outcome.OPERATIONAL, "", rule);
        }

        private void review(String column, String destination, String rule) {
            set(column, Outcome.REQUIRES_REVIEW, destination, rule);
        }

        private String text(String column, String destination, int maximum) {
            String value = raw(column);
            if (value == null || value.isEmpty()) return null;
            if (value.codePointCount(0, value.length()) > maximum
                    || value.codePoints()
                            .anyMatch(
                                    code ->
                                            code < 0x20
                                                            && code != '\t'
                                                            && code != '\r'
                                                            && code != '\n'
                                                    || code >= 0xd800 && code <= 0xdfff
                                                    || code == 0xfffe
                                                    || code == 0xffff)) {
                review(column, destination, "WIRE_TEXT_REQUIRES_REVIEW");
                return null;
            }
            set(column, Outcome.MAPPED, destination, "EXACT_NATIVE_TEXT");
            return value;
        }

        private String token(String column, String destination, int maximum) {
            String value = raw(column);
            if (value == null || value.isEmpty()) return null;
            if (value.startsWith(" ")
                    || value.endsWith(" ")
                    || value.contains("  ")
                    || value.indexOf('\t') >= 0
                    || value.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0) {
                review(column, destination, "TOKEN_NORMALIZATION_REQUIRES_REVIEW");
                return null;
            }
            return text(column, destination, maximum);
        }

        private String code(String column, String destination, Map<String, String> values) {
            String value = raw(column);
            if (value == null || value.isEmpty()) return null;
            String mapped = values.get(value);
            if (mapped == null) review(column, destination, "CODE_REQUIRES_REVIEW");
            else set(column, Outcome.MAPPED, destination, "REVIEWED_NATIVE_CODE");
            return mapped;
        }
    }
}
