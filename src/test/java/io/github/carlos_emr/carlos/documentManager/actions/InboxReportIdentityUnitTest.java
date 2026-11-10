/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class InboxReportIdentityUnitTest {
    private LabResultData row(String type, String id, String accession) {
        var result = mock(LabResultData.class);
        result.labType = type;
        result.accessionNumber = accession;
        when(result.getSegmentID()).thenReturn(id);
        return result;
    }

    @Test
    void shouldPreserveEachVersion_whenAccessionIsShared() {
        var original = row("HL7", "10", "shared");
        var correction = row("HL7", "20", "shared");
        assertThat(DmsInboxManage2Action.preserveSelectedReports(List.of(original, correction)))
                .containsExactly(original, correction);
    }

    @Test
    void shouldKeepSourceIdentities_whenNumericIdsOverlap() {
        var hl7 = row("HL7", "10", "shared");
        var legacy = row("CML", "10", "shared");
        assertThat(DmsInboxManage2Action.preserveSelectedReports(List.of(hl7, legacy)))
                .containsExactly(hl7, legacy);
    }

    @Test
    void shouldDeduplicateRoutingCopies_withoutCollapsingOtherVersions() {
        var original = row("HL7", "10", "shared");
        var correction = row("HL7", "20", "shared");
        assertThat(DmsInboxManage2Action.preserveSelectedReports(List.of(original, original, correction)))
                .containsExactly(original, correction);
    }

    @Test
    void shouldKeepMissingAccessionsSeparate_acrossLegacySources() {
        var first = row("CML", "10", "");
        var second = row("CML", "20", null);
        var document = row("DOC", "10", "null");
        assertThat(DmsInboxManage2Action.preserveSelectedReports(List.of(first, second, document)))
                .containsExactly(first, second, document);
    }

    @Test
    void shouldPreserveLegacyGrouping_andKeepReusedAccessionsAndUnknownDates() {
        var first = row("CML", "10", "shared");
        var duplicate = row("CML", "20", "shared");
        var reused = row("CML", "30", "shared");
        var undated = row("CML", "40", "shared");
        when(first.getDateObj()).thenReturn(java.sql.Date.valueOf("2026-01-01"));
        when(duplicate.getDateObj()).thenReturn(java.sql.Date.valueOf("2026-02-01"));
        when(reused.getDateObj()).thenReturn(java.sql.Date.valueOf("2026-08-01"));
        assertThat(DmsInboxManage2Action.preserveSelectedReports(List.of(first, duplicate, reused, undated)))
                .containsExactly(undated, reused, first);
    }
}
