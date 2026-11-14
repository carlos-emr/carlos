/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The chart's native issue record: its patient issues that no CDS item carries.
 *
 * <p>A system marker (a CPP section tag, OMeds, TicklerNote, PatientLog, ...) files notes; it is
 * not a diagnosis. One that files no note has no item to travel with: Administration's default
 * encounter issues create the patient issue of every listed code when an encounter opens ({@code
 * EctIncomingEncounter2Action.assignDefaultIssues}), and a marker code can be listed. Such a
 * marker held the patient ({@code CDS_EXPORT_NATIVE_SYSTEM_ISSUE_WITHOUT_NOTE_REQUIRES_REVIEW});
 * owner decision 2026-11-14: carry and restore. It is written as no CDS item, so another receiver
 * gets nothing for it. Its patient row and catalogue row travel, every column but their
 * identities and exactly as stored, in one NewCategory record of the patient ({@link
 * CdsNativeExtensionContract#NATIVE_ISSUES_CATEGORY}), from which a CARLOS importer restores the
 * marker. The record is the chart's own, written after any retained copy of an earlier sender's
 * (an empty one when the chart has no marker), so the last such record of a patient file is
 * always the sending chart's.
 *
 * <p>A marker of a program the exporting identities do not work in is held, as an unlinked
 * diagnosis of such a program is: the chart those identities see lacks it
 * ({@code CDS_EXPORT_NATIVE_ISSUE_PROGRAM_REQUIRES_REVIEW}).
 */
public final class CdsExportIssueMarkerMapper {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** NewCategory {@code CategoryName} of the native issue record. */
    public static final String CATEGORY_NAME = CdsNativeExtensionContract.NATIVE_ISSUES_CATEGORY;

    /** Residual {@code DataElement} name whose Content is the versioned JSON. */
    public static final String ELEMENT_NAME = CdsNativeExtensionContract.NATIVE_ISSUES;

    /** Destination of every value the record carries. */
    public static final String DESTINATION = "NewCategory/ResidualInfo";

    /** The rule of every carried column. */
    static final String CARRIED = "EXACT_NATIVE_ISSUE_MARKER_WITHOUT_NOTE";

    /** The row identities, which are local to the sending chart and never travel. */
    private static final Set<String> IDENTITIES = Set.of("id", "issue_id", "demographic_no");

    private CdsExportIssueMarkerMapper() {}

    /** One marker: its patient issue and the catalogue entry it names, with their witnesses. */
    public record Marker(
            CdsExportHistoryFieldReconciler.Row issue,
            CdsExportHistoryFieldReconciler.Row definition) {
        public Marker {
            Objects.requireNonNull(issue);
            Objects.requireNonNull(definition);
        }

        @Override
        public String toString() {
            return "CdsExportIssueMarkerMapper.Marker[protected]";
        }
    }

    /** The record's JSON, the assessment of every carried column and the rows it carries. */
    public record Mapped(
            String json,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportCapturedTextRowDto> rows) {
        public Mapped {
            Objects.requireNonNull(json);
            fields = Map.copyOf(fields);
            rows = List.copyOf(rows);
        }

        @Override
        public String toString() {
            return "CdsExportIssueMarkerMapper.Mapped[protected]";
        }
    }

    /**
     * The record of these markers, in the order given; an empty list gives the record that
     * carries nothing, written when a retained copy of an earlier sender's record must be
     * superseded.
     *
     * @param readableProgram a fresh answer to whether the exporting identities work in a program
     */
    public static Mapped map(
            int patient,
            List<Marker> markers,
            Predicate<String> readableProgram,
            Runnable checkpoint)
            throws IOException {
        if (patient < 1 || markers == null || readableProgram == null || checkpoint == null)
            invalid();
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        var issues = new ArrayList<Map<String, Object>>();
        for (var marker : markers) {
            checkpoint.run();
            var issue = marker.issue().value();
            var definition = marker.definition().value();
            CdsExportCaseManagementProjectionEvidence.verify(issue, marker.issue().witness());
            CdsExportCaseManagementProjectionEvidence.verify(
                    definition, marker.definition().witness());
            if (!"casemgmt_issue".equals(issue.table())
                    || !"issue".equals(definition.table())
                    || !Integer.toString(patient).equals(issue.fields().get("demographic_no"))
                    || !Objects.equals(
                            issue.fields().get("issue_id"), definition.fields().get("issue_id"))
                    || !CdsExportNativeHistoryMapper.systemDefinition(definition)) invalid();
            String program = issue.fields().get("program_id");
            if (CdsExportNativeHistoryMapper.scopedProgram(program)
                    && !readableProgram.test(program))
                throw new CdsExportMappingReviewException(
                        "CDS_EXPORT_NATIVE_ISSUE_PROGRAM_REQUIRES_REVIEW");
            var facts = new LinkedHashMap<String, Object>();
            for (var row : List.of(issue, definition)) {
                var state = new CdsExportNativeFieldAssessments(row, fields);
                var attributes = new LinkedHashMap<String, String>();
                for (var entry : row.fields().entrySet()) {
                    if (IDENTITIES.contains(entry.getKey())) {
                        state.operational(entry.getKey(), "VERIFIED_NATIVE_ISSUE_IDENTITY");
                        continue;
                    }
                    // Carried in the residual JSON: no character and no length is refused.
                    if (entry.getValue() != null) attributes.put(entry.getKey(), entry.getValue());
                    state.set(entry.getKey(), Outcome.MAPPED, DESTINATION, CARRIED);
                }
                facts.put(row.table(), attributes);
                rows.add(row);
            }
            issues.add(facts);
        }
        // One fixed key order, so an unchanged chart writes the same bytes on every run.
        var document = new LinkedHashMap<String, Object>();
        document.put("version", 1);
        document.put("issues", issues);
        return new Mapped(JSON.writeValueAsString(document), fields, rows);
    }

    /** Writes the record's NewCategory content. */
    public static void write(String json, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "CategoryName", CATEGORY_NAME);
        xml.element("cds", "CategoryDescription", "Patient issues no CDS 5.2 item carries");
        xml.start("cds", "ResidualInfo");
        // A character XML cannot carry, and whitespace a token would collapse, is its JSON
        // escape; the decoded values are unchanged.
        CdsExportNativeHistoryXmlWriter.residualElement(
                xml, ELEMENT_NAME, "string", CdsExportResidualJson.token(json));
        xml.end();
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_ISSUE_MARKER_SCOPE_INVALID");
    }
}
