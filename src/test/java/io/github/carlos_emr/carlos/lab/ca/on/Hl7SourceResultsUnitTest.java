/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.on;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;

/** Regression coverage for this contract.
 * @since 2026-11-09
 */
@Tag("unit")
@Tag("lab")
class Hl7SourceResultsUnitTest {
    private MessageHandler handler() {
        var h = mock(MessageHandler.class);
        when(h.getOBRCount()).thenReturn(1);
        when(h.getOBXCount(0)).thenReturn(4);
        for (int obx = 0; obx < 4; obx++) {
            when(h.getOBXIdentifier(0, obx)).thenReturn("3007");
            when(h.getOBXName(0, obx)).thenReturn("GLUCOSE FASTING-SER");
            when(h.getOBXResultStatus(0, obx)).thenReturn("Final");
            when(h.getTimeStamp(0, obx)).thenReturn("2026-03-10 09:00:00");
            when(h.getOBXUnits(0, obx)).thenReturn("mmol/L");
            when(h.getOBXReferenceRange(0, obx)).thenReturn("3-8");
            when(h.getOBXAbnormalFlag(0, obx)).thenReturn("N");
        }
        when(h.getOBXResult(0, 0)).thenReturn("Pending");
        when(h.getOBXResult(0, 1)).thenReturn("Negative");
        when(h.getOBXResult(0, 2)).thenReturn("123");
        when(h.getOBXResultStatus(0, 2)).thenReturn("Pending");
        when(h.getOBXResult(0, 3)).thenReturn("456");
        when(h.isTestResultBlocked(0, 3)).thenReturn(true);
        return h;
    }

    @Test
    void shouldPreserveFinalPendingLiteralAndEveryObservation_whenProjectionFilterExcludesIt() {
        var rows = Hl7SourceResults.read(Set.of("12"), id -> "12", id -> handler());
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).containsEntry("result", "Pending").containsEntry("lab_no", "12")
                .containsEntry("identifier", "3007").containsEntry("units", "mmol/L")
                .containsEntry("range", "3-8").containsEntry("collDate", "2026-03-10 09:00:00")
                .containsEntry("sourceObservation", "0-0");
        assertThat(rows.getLast()).containsEntry("result", "Negative").containsEntry("sourceObservation", "0-1");
    }

    @Test
    void shouldReadOnlyNewestVisibleVersion_whenChainIncludesAnotherPatientsReport() {
        @SuppressWarnings("unchecked") Function<String, MessageHandler> handlers = mock(Function.class);
        MessageHandler current = handler();
        when(handlers.apply("12")).thenReturn(current);
        var rows = Hl7SourceResults.read(new LinkedHashSet<>(List.of("11", "12")), id -> "11,12,13", handlers);
        assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row).containsEntry("lab_no", "12"));
        verify(handlers).apply("12");
        verifyNoMoreInteractions(handlers);
    }

    @Test
    void shouldFailExplicitly_whenSourceHandlerCannotBeRead() {
        assertThatThrownBy(() -> Hl7SourceResults.read(Set.of("12"), id -> "12", id -> null))
                .isInstanceOf(IllegalStateException.class).hasMessage("Source observations are unavailable");
    }

    @Test
    void shouldFailExplicitly_whenVersionLookupDoesNotIncludeSelectedReport() {
        assertThatThrownBy(() -> Hl7SourceResults.read(Set.of("12"), id -> "99", id -> handler()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Source version chain is unavailable");
    }
}
