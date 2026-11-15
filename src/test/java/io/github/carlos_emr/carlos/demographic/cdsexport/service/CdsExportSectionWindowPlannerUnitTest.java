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
    void shouldHalveTheWindow_whenEveryOwnerFittedAndOtherStatePassedTheLimit() throws Exception {
        // The owners are counted first and all fit; what the window holds for them (here a
        // later pass's witnesses, a hundred bytes an owner) then passes the limit. That says
        // nothing about how many owners fit, so the window halves: it used to shrink by one
        // owner a load, reading a 64-owner window fifty-four times before ten fitted.
        var ids = ids(64);
        var sizes = new ArrayList<Long>();
        int[] loads = {0};
        CdsExportSectionWindowPlanner.forEachWindow(
                64,
                new Limits(64, 1_000),
                owners -> {
                    for (String id : ids) owners.add(id);
                },
                (window, budget) -> {
                    loads[0]++;
                    for (String id : ids) if (window.contains(id)) budget.owner(1);
                    if (!budget.add(1, window.size() * 100)) return false;
                    sizes.add(window.size());
                    return true;
                });
        assertThat(sizes).hasSize(8).containsOnly(8L);
        // The whole chart, 32 and 16 owners abandoned, then eight windows of eight.
        assertThat(loads[0]).isEqualTo(11);
    }

    @Test
    void shouldLoadAPulledWindowAgainSmaller_whenItPassesTheByteLimit() throws Exception {
        // A section that asks for its windows one at a time: forty owners of a hundred bytes,
        // seven of which fit. A window of one owner is never abandoned, whatever it holds.
        var sizes = new CdsExportSectionWindowPlanner.Sizes(new Limits(16, 700));
        var lengths = new ArrayList<Integer>();
        int[] loads = {0};
        int remaining = 40;
        while (remaining > 0) {
            var loaded =
                    CdsExportSectionWindowPlanner.loadNext(
                            sizes,
                            remaining,
                            (length, budget) -> {
                                loads[0]++;
                                for (int i = 0; i < length; i++)
                                    if (!budget.owner(100)) return null;
                                return length;
                            });
            assertThat(loaded.value()).isEqualTo(loaded.length());
            lengths.add(loaded.length());
            remaining -= loaded.length();
        }
        assertThat(lengths).containsExactly(7, 7, 7, 7, 7, 5);
        // Sixteen owners were too large once; no other window was read twice.
        assertThat(loads[0]).isEqualTo(7);
        var one =
                CdsExportSectionWindowPlanner.loadNext(
                        new CdsExportSectionWindowPlanner.Sizes(new Limits(1, 10)),
                        3,
                        (length, budget) -> budget.owner(1_000_000) ? "held" : null);
        assertThat(one.value()).isEqualTo("held");
        assertThat(one.length()).isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                CdsExportSectionWindowPlanner.loadNext(
                                        new CdsExportSectionWindowPlanner.Sizes(
                                                new Limits(4, 10)),
                                        0,
                                        (length, budget) -> "never"))
                .hasMessage("CDS_EXPORT_SECTION_WINDOW_INVALID");
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
    void shouldRefuseAWindowAbandonedWithoutAReason_orAnOwnerWithoutAnId() {
        assertThatThrownBy(
                        () ->
                                CdsExportSectionWindowPlanner.forEachWindow(
                                        9,
                                        new Limits(2, 100),
                                        owners -> owners.add(null),
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
    void shouldWindowTheOwnersTheCaptureDelivers_whenItsStatedCountDiffers() throws Exception {
        // The section compares what its windows emitted with the stated count, under its own
        // code; the planner only visits what the pass reports.
        var visited = new ArrayList<Long>();
        long windows =
                CdsExportSectionWindowPlanner.forEachWindow(
                        9,
                        new Limits(2, 100),
                        owners -> {
                            for (String id : ids(3)) owners.add(id);
                        },
                        (window, budget) -> visited.add(window.size()));
        assertThat(windows).isEqualTo(2);
        assertThat(visited).containsExactly(2L, 1L);
    }

    @Test
    void shouldStartFromWhatFitted_whenTheWholeChartPassedTheByteLimit() throws Exception {
        var ids = ids(10);
        int[] loads = {0};
        var visited = new ArrayList<Long>();
        CdsExportSectionWindowPlanner.forEachWindow(
                10,
                new Limits(100, 400),
                owners -> {
                    for (String id : ids) owners.add(id);
                },
                (window, budget) -> {
                    loads[0]++;
                    for (String id : ids) if (window.contains(id)) budget.owner(100);
                    if (budget.exceeded()) return false;
                    visited.add(window.size());
                    return true;
                });
        assertThat(visited).containsExactly(4L, 4L, 2L);
        // The whole chart once, then each window once: no window is read twice.
        assertThat(loads[0]).isEqualTo(4);
    }

    @Test
    void shouldKeepOnlyEachOwnersId_forAChartOfAMillionRows() throws Exception {
        var owners = new CdsExportSectionWindowPlanner.Owners();
        for (int id = 1; id <= 1_000_000; id++) owners.add(Integer.toString(10_000_000 + id));
        assertThat(owners.size()).isEqualTo(1_000_000);
    }

    @Test
    void shouldGrowTheWindowAgain_afterOneLargeOwnerMadeItSmall() throws Exception {
        // Sixty-four owners of ten bytes, and one of ten thousand in their middle.
        var ids = ids(64);
        var sizes = new ArrayList<Long>();
        var visited = new ArrayList<String>();
        CdsExportSectionWindowPlanner.forEachWindow(
                64,
                new Limits(16, 200),
                owners -> {
                    for (String id : ids) owners.add(id);
                },
                (window, budget) -> {
                    var held = new ArrayList<String>();
                    for (int i = 0; i < ids.size(); i++)
                        if (window.contains(ids.get(i)) && budget.owner(i == 20 ? 10_000 : 10))
                            held.add(ids.get(i));
                    if (budget.exceeded()) return false;
                    sizes.add(window.size());
                    visited.addAll(held);
                    return true;
                });
        assertThat(visited).isEqualTo(ids);
        // The large owner is read alone; the windows after it double back to sixteen owners
        // instead of staying at one owner for the rest of the chart.
        assertThat(sizes).containsExactly(16L, 4L, 1L, 1L, 2L, 4L, 8L, 16L, 12L);
    }

    @Test
    void shouldRunOnlyTheWindows_whenTheSectionMadeItsOwnWholeAttempt() throws Exception {
        var budget = CdsExportSectionWindowPlanner.wholeBudget(3, new Limits(8, 100));
        assertThat(budget.enforced()).isTrue();
        assertThat(budget.add(1, 101)).isFalse();
        assertThat(CdsExportSectionWindowPlanner.wholeBudget(1, new Limits(8, 100)).enforced())
                .isFalse();
        var whole = CdsExportSectionWindowPlanner.whole(3);
        assertThat(whole.whole()).isTrue();
        assertThat(whole.size()).isEqualTo(3);
        var sizes = new ArrayList<Long>();
        long windows =
                CdsExportSectionWindowPlanner.forEachWindowOf(
                        new Limits(8, 100),
                        2,
                        owners -> {
                            for (String id : ids(5)) owners.add(id);
                        },
                        (window, used) -> {
                            assertThat(window.whole()).isFalse();
                            return used.add(1, 60) && sizes.add(window.size());
                        });
        assertThat(windows).isEqualTo(3);
        assertThat(sizes).containsExactly(2L, 2L, 1L);
    }
}
