/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportSectionWindowPlanner.Limits;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A section's rows are read whole when they fit one window and window by window otherwise; no
 * size refuses them, every owner is visited exactly once and in capture order.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportSectionWindowPlannerUnitTest {
    private static List<String> ids(int count) {
        var ids = new ArrayList<String>();
        for (int id = 1; id <= count; id++) ids.add(Integer.toString(id * 7));
        return ids;
    }

    /** Runs the planner over {@code ids}; each owner costs {@code weight} bytes when loaded. */
    private static List<List<String>> windows(List<String> ids, Limits limits, long weight)
            throws IOException {
        var visited = new ArrayList<List<String>>();
        int[] scans = {0};
        CdsExportSectionWindowPlanner.forEachWindow(
                ids.size(),
                limits,
                owners -> {
                    scans[0]++;
                    for (String id : ids) owners.add(id);
                },
                (window, budget) -> {
                    var held = new ArrayList<String>();
                    for (String id : ids)
                        if (window.contains(id) && budget.owner(weight)) held.add(id);
                    if (budget.exceeded()) return false;
                    assertThat((long) held.size()).isEqualTo(window.size());
                    visited.add(held);
                    return true;
                });
        assertThat(scans[0]).isLessThanOrEqualTo(1);
        return visited;
    }

    @Test
    void shouldReadTheChartWhole_whenItFitsOneWindow() throws Exception {
        var ids = ids(5);
        int[] scans = {0};
        long windows =
                CdsExportSectionWindowPlanner.forEachWindow(
                        5,
                        new Limits(5, 500),
                        owners -> scans[0]++,
                        (window, budget) -> {
                            assertThat(window.whole()).isTrue();
                            assertThat(window.size()).isEqualTo(5);
                            assertThat(window.contains("anything")).isTrue();
                            assertThat(window.contains(null)).isFalse();
                            for (String ignored : ids) assertThat(budget.owner(100)).isTrue();
                            return true;
                        });
        assertThat(windows).isEqualTo(1);
        // No extra pass over the capture for a chart that was always read whole.
        assertThat(scans[0]).isZero();
    }

    @Test
    void shouldVisitEveryOwnerOnceInCaptureOrder_whenTheChartHasMoreRowsThanOneWindow()
            throws Exception {
        var ids = ids(23);
        var visited = windows(ids, new Limits(4, 1L << 20), 10);
        assertThat(visited).hasSize(6);
        assertThat(visited.stream().flatMap(List::stream).toList()).isEqualTo(ids);
        assertThat(visited.getFirst()).hasSize(4);
        assertThat(visited.getLast()).hasSize(3);
    }

    @Test
    void shouldReadSmallerWindows_whenTheLoadPassesTheByteLimit() throws Exception {
        var ids = ids(40);
        // 40 owners fit the row limit but not the byte limit: 7 owners of 100 bytes fit 700.
        var visited = windows(ids, new Limits(100, 700), 100);
        assertThat(visited.stream().flatMap(List::stream).toList()).isEqualTo(ids);
        assertThat(visited).allSatisfy(window -> assertThat(window.size()).isLessThanOrEqualTo(7));
        assertThat(visited.getFirst()).hasSize(7);
    }

    @Test
    void shouldNeverRefuseOneOwner_whenItAloneIsLargerThanTheWindow() throws Exception {
        var ids = ids(3);
        var visited = windows(ids, new Limits(8, 10), 1_000_000);
        assertThat(visited).hasSize(3);
        assertThat(visited.stream().flatMap(List::stream).toList()).isEqualTo(ids);
    }

    @Test
    void shouldHalveTheWindow_whenTheLoadDoesNotSayHowManyOwnersFitted() throws Exception {
        var ids = ids(16);
        var sizes = new ArrayList<Long>();
        CdsExportSectionWindowPlanner.forEachWindow(
                16,
                new Limits(16, 100),
                owners -> {
                    for (String id : ids) owners.add(id);
                },
                (window, budget) -> {
                    // Child rows, not owners, pass the limit: three bytes for each owner held.
                    if (!budget.add(1, window.size() * 30)) return false;
                    sizes.add(window.size());
                    return true;
                });
        assertThat(sizes).containsExactly(2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L);
    }

    @Test
    void shouldCountEachKindOfStateApart_againstTheSameByteLimit() throws Exception {
        long windows =
                CdsExportSectionWindowPlanner.forEachWindow(
                        2,
                        new Limits(2, 100),
                        owners -> {
                            throw new IOException("NOT_EXPECTED");
                        },
                        (window, budget) ->
                                budget.owner(50)
                                        && budget.owner(50)
                                        && budget.add(1, 100)
                                        && budget.add(2, 100)
                                        && budget.enforced());
        assertThat(windows).isEqualTo(1);
    }

    @Test
    void shouldRefuseAnOwnerPassWithAnotherRowCount_orAWindowAbandonedWithoutAReason() {
        assertThatThrownBy(
                        () ->
                                CdsExportSectionWindowPlanner.forEachWindow(
                                        9,
                                        new Limits(2, 100),
                                        owners -> owners.add("1"),
                                        (window, budget) -> true))
                .hasMessage("CDS_EXPORT_SECTION_WINDOW_INVALID");
        assertThatThrownBy(
                        () ->
                                CdsExportSectionWindowPlanner.forEachWindow(
                                        1,
                                        new Limits(2, 100),
                                        owners -> owners.add("1"),
                                        (window, budget) -> false))
                .hasMessage("CDS_EXPORT_SECTION_WINDOW_INVALID");
        assertThatThrownBy(() -> new Limits(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldKeepOnlyEachOwnersId_forAChartOfAMillionRows() throws Exception {
        var owners = new CdsExportSectionWindowPlanner.Owners();
        for (int id = 1; id <= 1_000_000; id++) owners.add(Integer.toString(10_000_000 + id));
        assertThat(owners.size()).isEqualTo(1_000_000);
    }
}
