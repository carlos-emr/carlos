/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.appointment.service;

import io.github.carlos_emr.carlos.commn.dao.AppointmentDaySheetRow;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
@Tag("appointment")
class DaySheetSelectionUnitTest {
    @Test
    void shouldRetainOnlyUnchangedRenderedVersions_inFreshReportOrder() {
        var selection = DaySheetSelection.fromParameters(new String[]{"1:1000", "2:1000", "3:1000"}, "3");
        var rows = List.of(row(4, "1000"), row(3, "1000"), row(2, "2000"), row(1, "1000"));
        assertThat(selection.matching(rows)).containsExactly(rows.get(1), rows.get(3));
        assertThat(selection.size()).isEqualTo(3);
        // New rows and edits remain unacknowledged, even on a repeated submission.
        assertThat(selection.matching(List.of(row(4, "1000"), row(2, "2000")))).isEmpty();
    }

    @Test
    void shouldKeepLegacyNullTimestampRows_onlyWhileStillUnversioned() {
        var selection = DaySheetSelection.fromParameters(new String[]{"1:unversioned", "2:-123"}, "2");
        var rows = List.of(row(1, "unversioned"), row(2, "-123"), row(3, null));
        assertThat(selection.matching(rows)).containsExactly(rows.get(0), rows.get(1));
        assertThat(selection.matching(List.of(row(1, "0"), row(2, null)))).isEmpty();
    }

    @Test
    void shouldAcknowledgeNothing_whenSelectionIsExplicitlyEmpty() {
        for (String[] parameters : new String[][]{null, new String[0]}) {
            var selection = DaySheetSelection.fromParameters(parameters, "0");
            assertThat(selection.size()).isZero();
            assertThat(selection.matching(List.of(row(1, "1000")))).isEmpty();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0:1", "-1:1", "1:", "1:null", "1:1:2", "01:1", "1:+2", "1:1,2:2",
            "2147483648:1", "1:9223372036854775808", "1:111111111111111111111111111111111111111111111"})
    void shouldRejectMalformedSelection_beforeAnyAcknowledgment(String parameter) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                DaySheetSelection.fromParameters(new String[]{parameter}, "1"));
    }

    @Test
    void shouldRejectDuplicateIds_evenWhenTheirVersionsDiffer() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                DaySheetSelection.fromParameters(new String[]{"1:1000", "1:2000"}, "2"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "2", "01", "untrusted"})
    void shouldRejectTruncatedOrMissingSelections_whenRenderedCountDoesNotMatch(String count) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                DaySheetSelection.fromParameters(new String[]{"1:1000"}, count));
    }

    @Test
    void shouldBoundTheSelection_withoutSilentlyDroppingExtraRows() {
        String[] rows = IntStream.rangeClosed(1, 10000).mapToObj(id -> id + ":1000").toArray(String[]::new);
        assertThat(DaySheetSelection.fromParameters(rows, "10000").size()).isEqualTo(10000);
        String[] oversized = IntStream.rangeClosed(1, 10001).mapToObj(id -> id + ":1000").toArray(String[]::new);
        assertThatIllegalArgumentException().isThrownBy(() -> DaySheetSelection.fromParameters(oversized, "10001"));
    }

    private static AppointmentDaySheetRow row(int id, String version) {
        return new AppointmentDaySheetRow(null, null, "2026-11-18", "P1", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, id, version);
    }
}
