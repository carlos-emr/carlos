/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

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
}
