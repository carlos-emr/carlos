/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Column order of the cumulative lab grid, including source-only columns whose time is partial or unknown.
 * @since 2026-11-10
 */
@Tag("unit")
@Tag("lab")
class CumulativeLabValuesComparatorUnitTest {
    private static Hashtable<String, String> column(String date) {
        Hashtable<String, String> column = new Hashtable<>();
        column.put("date", date);
        return column;
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSortNewestFirstAndUndatedLast_withoutThrowing() {
        List<Hashtable<String, String>> columns = new ArrayList<>(List.of(column(""), column("2004-07-13"),
                column("2026-01-03 09:15:42.0"), column("not a date"), column("2026-01-03 09:15"), column("2026-02-01 08:00:00")));

        columns.sort(new CumulativeLabValuesComparator());

        assertThat(columns).extracting(column -> column.get("date")).startsWith(
                "2026-02-01 08:00:00", "2026-01-03 09:15:42.0", "2026-01-03 09:15", "2004-07-13");
        assertThat(columns.subList(4, 6)).extracting(column -> column.get("date"))
                .containsExactlyInAnyOrder("", "not a date");
    }
}
