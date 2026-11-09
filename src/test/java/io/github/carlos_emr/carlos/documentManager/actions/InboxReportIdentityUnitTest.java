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
}
