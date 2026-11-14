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
}
