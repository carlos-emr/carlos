/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A patient document as the exporter writes it for one CPP item or encounter note, for tests on
 * the import side that prove a transfer between two CARLOS installations.
 */
public final class CdsExportNativeHistoryFixture {
    private CdsExportNativeHistoryFixture() {}

    /**
     * The composed, schema-valid patient document holding the item's occurrence.
     *
     * @param tag the CPP section's issue code ({@code FamHistory}, {@code SocHistory}, ...), or
     *     null for an encounter note
     * @param bodies the stored text of each revision, oldest first
     */
    public static byte[] document(CdsCategory category, String tag, String... bodies)
            throws Exception {
        var revisions = new ArrayList<CdsExportHistoryFieldReconciler.Current>();
        long id = 91;
        for (String body : bodies)
            revisions.add(CdsExportNativeHistoryMapperUnitTest.note(id++, body, tag, List.of()));
        var mapped =
                new CdsExportNativeHistoryMapper()
                        .map(
                                27,
                                category,
                                revisions,
                                CdsExportNativeHistoryMapperUnitTest.providers(),
                                ZoneId.of("America/Toronto"));
        return CdsExportNativeHistoryMapperUnitTest.render(mapped.value());
    }

    /**
     * The composed, schema-valid patient document holding a CPP item its chart files under
     * several CPP sections, exported once, under the first of them.
     *
     * @param tags the section tags (issue codes) the item's links carry, in link order
     */
    public static byte[] filedUnderDocument(String body, String... tags) throws Exception {
        var item = CdsExportNativeHistoryMapperUnitTest.filedUnder(91, body, tags);
        var mapped =
                new CdsExportNativeHistoryMapper()
                        .map(
                                27,
                                CdsExportNativeHistoryMapper.category(item),
                                List.of(item),
                                CdsExportNativeHistoryMapperUnitTest.providers(),
                                ZoneId.of("America/Toronto"));
        return CdsExportNativeHistoryMapperUnitTest.render(mapped.value());
    }

    /**
     * The composed, schema-valid patient document holding one diagnosis exported on its own (a
     * patient issue no CPP item files): a ProblemList entry whose text is the catalogue
     * description.
     *
     * @param system the catalogue row's coding system ({@code issue.type}), or null
     */
    public static byte[] diagnosisDocument(String code, String system, String description)
            throws Exception {
        var issue =
                CdsExportNativeHistoryMapperUnitTest.row(
                        Plan.CASE_ISSUES,
                        41,
                        Map.of("demographic_no", "27", "issue_id", "51", "resolved", "0"));
        var catalogue = new LinkedHashMap<String, String>();
        catalogue.put("code", code);
        catalogue.put("description", description);
        if (system != null) catalogue.put("type", system);
        var definition =
                CdsExportNativeHistoryMapperUnitTest.row(Plan.ISSUE_CATALOGUE, 51, catalogue);
        var mapped = new CdsExportNativeHistoryMapper().mapIssue(27, issue, definition);
        return CdsExportNativeHistoryMapperUnitTest.render(mapped.value());
    }
    /** A complete native system marker, with every non-identity value supplied as stored. */
    public static CdsExportIssueMarkerMapper.Marker marker(long id, String program) {
        var patient = new LinkedHashMap<String, String>();
        patient.put("demographic_no", "27");
        patient.put("issue_id", "51");
        patient.put("acute", "1");
        patient.put("certain", "0");
        patient.put("major", "1");
        patient.put("resolved", "0");
        patient.put("type", "general  ");
        patient.put("program_id", program);
        patient.put("update_date", "2026-09-01 12:34:56.0");
        var catalogue = new LinkedHashMap<String, String>();
        catalogue.put("code", "OMeds");
        catalogue.put("description", "Medication  marker\t");
        catalogue.put("role", "all");
        catalogue.put("type", "system");
        catalogue.put("priority", null);
        catalogue.put("sortOrderId", "0");
        catalogue.put("update_date", "2020-01-02 00:00:00.0");
        return new CdsExportIssueMarkerMapper.Marker(
                CdsExportNativeHistoryMapperUnitTest.row(Plan.CASE_ISSUES, id, patient),
                CdsExportNativeHistoryMapperUnitTest.row(Plan.ISSUE_CATALOGUE, 51, catalogue));
    }

    /** The schema-validated carrier produced by the actual marker mapper and XML writer. */
    public static byte[] markerDocument(long id, String program) throws Exception {
        var mapped = CdsExportIssueMarkerMapper.map(27, marker(id, program), ignored -> true, () -> {});
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(mapped.rows(), mapped.fields(), () -> {});
        return CdsExportNativeHistoryMapperUnitTest.render(CdsCategory.NEW_CATEGORY,
                xml -> CdsExportIssueMarkerMapper.write(mapped.json(), xml));
    }

    /** Emits rows read back from the database, for physical native import round trips. */
    public static byte[] markerDocument(Map<String, String> patient, Map<String, String> catalogue)
            throws Exception {
        var marker = new CdsExportIssueMarkerMapper.Marker(
                CdsExportNativeHistoryMapperUnitTest.row(Plan.CASE_ISSUES,
                        Long.parseLong(patient.get("id")), patient),
                CdsExportNativeHistoryMapperUnitTest.row(Plan.ISSUE_CATALOGUE,
                        Long.parseLong(catalogue.get("issue_id")), catalogue));
        var mapped = CdsExportIssueMarkerMapper.map(Integer.parseInt(patient.get("demographic_no")),
                marker, ignored -> true, () -> {});
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(mapped.rows(), mapped.fields(), () -> {});
        return CdsExportNativeHistoryMapperUnitTest.render(CdsCategory.NEW_CATEGORY,
                xml -> CdsExportIssueMarkerMapper.write(mapped.json(), xml));
    }

    /** A real native note projected through the mapper and schema-valid XML writer. */
    public static byte[] encounterDocument(String encounter) throws Exception {
        var original = CdsExportNativeHistoryMapperUnitTest.note(91, "Seen today.", null, List.of());
        var values = new LinkedHashMap<>(original.note().value().fields());
        values.put("encounter_type", encounter);
        var current = new CdsExportHistoryFieldReconciler.Current(
                CdsExportNativeHistoryMapperUnitTest.row(Plan.CASE_NOTES, 91, values),
                original.extensions(), original.links(), original.patientIssues(), original.definitions());
        var mapped = new CdsExportNativeHistoryMapper().map(27, CdsCategory.CLINICAL_NOTES,
                List.of(current), CdsExportNativeHistoryMapperUnitTest.providers(),
                ZoneId.of("America/Toronto"));
        return CdsExportNativeHistoryMapperUnitTest.render(mapped.value());
    }

    /** Current native extension rows, including duplicate keys, through the actual XML projection. */
    public static byte[] extensionDocument(CdsCategory category, String tag, List<Map<String, String>> rows)
            throws Exception {
        var extensions = new ArrayList<CdsExportHistoryFieldReconciler.Row>();
        long id = 101;
        for (var supplied : rows) {
            var values = new LinkedHashMap<>(supplied);
            values.put("note_id", "91");
            extensions.add(CdsExportNativeHistoryMapperUnitTest.row(Plan.CASE_NOTE_EXT, id++, values));
        }
        var current = CdsExportNativeHistoryMapperUnitTest.note(91, "Synthetic CPP item", tag, extensions);
        var mapped = new CdsExportNativeHistoryMapper().map(27, category, List.of(current),
                CdsExportNativeHistoryMapperUnitTest.providers(), ZoneId.of("America/Toronto"));
        return CdsExportNativeHistoryMapperUnitTest.render(mapped.value());
    }

}
