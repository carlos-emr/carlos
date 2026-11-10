/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.common;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Unknown event times must not break native chart lists or become invented chronological dates. */
@Tag("unit")
@Tag("casemgmt")
class EChartNoteEntryDateComparatorUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldKeepUnknownDatesLast_whenChronologicalDirectionChanges(boolean ascending) {
        EChartNoteEntry oldest = entry(1L, new Date(1000));
        EChartNoteEntry middle = entry(2L, new Date(2000));
        EChartNoteEntry newest = entry(3L, new Date(3000));
        EChartNoteEntry unknownFirst = entry("unknown-first", null);
        EChartNoteEntry unknownSecond = entry(4, null);
        List<EChartNoteEntry> entries = new ArrayList<>(
                Arrays.asList(unknownFirst, newest, null, oldest, unknownSecond, middle));

        entries.sort(comparator(ascending));

        if (ascending) {
            assertThat(entries).containsExactly(oldest, middle, newest, unknownFirst, unknownSecond, null);
        } else {
            assertThat(entries).containsExactly(newest, middle, oldest, unknownFirst, unknownSecond, null);
        }
        assertThat(unknownFirst.getDate()).isNull();
        assertThat(unknownSecond.getDate()).isNull();
        assertThat(oldest.getDate()).isEqualTo(new Date(1000));
        assertThat(newest.getDate()).isEqualTo(new Date(3000));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRetainTieOrder_whenEntriesHaveDifferentIdentifierTypes(boolean ascending) {
        EChartNoteEntry local = entry(20L, new Date(2000));
        EChartNoteEntry grouped = entry("group-1", new Date(2000));
        EChartNoteEntry billed = entry(30, new Date(2000));
        EChartNoteEntry unknownFirst = entry("source-date-only", null);
        EChartNoteEntry unknownSecond = entry(40L, null);
        List<EChartNoteEntry> entries = new ArrayList<>(
                List.of(unknownFirst, local, unknownSecond, grouped, billed));

        entries.sort(comparator(ascending));

        assertThat(entries).containsExactly(local, grouped, billed, unknownFirst, unknownSecond);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPreserveComparatorContract_whenKnownAndUnknownEntriesMix(boolean ascending) {
        List<EChartNoteEntry> entries = Arrays.asList(null, entry(1L, null), entry("date-only", null),
                entry(2L, new Date(1000)), entry("same-time", new Date(1000)), entry(3L, new Date(3000)));
        Comparator<EChartNoteEntry> order = comparator(ascending);

        for (EChartNoteEntry left : entries) {
            for (EChartNoteEntry right : entries) {
                int compared = Integer.signum(order.compare(left, right));
                assertThat(compared).isEqualTo(-Integer.signum(order.compare(right, left)));
                for (EChartNoteEntry other : entries) {
                    if (compared > 0 && order.compare(right, other) > 0) {
                        assertThat(order.compare(left, other)).isPositive();
                    }
                    if (compared == 0) {
                        assertThat(Integer.signum(order.compare(left, other)))
                                .isEqualTo(Integer.signum(order.compare(right, other)));
                    }
                }
            }
        }
    }

    private Comparator<EChartNoteEntry> comparator(boolean ascending) {
        return ascending ? EChartNoteEntry.getDateComparator() : EChartNoteEntry.getDateComparatorDesc();
    }

    private EChartNoteEntry entry(Object id, Date date) {
        EChartNoteEntry entry = new EChartNoteEntry();
        entry.setId(id);
        entry.setDate(date);
        return entry;
    }
}
