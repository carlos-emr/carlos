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
package io.github.carlos_emr.carlos.casemgmt.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

@Tag("unit")
class NoteDisplayUnknownSourceUnitTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldKeepUnknownSourceDatesLast_inEitherClinicalUiSortDirection(boolean ascending) {
        var earlier = note(3, 1000L);
        var later = note(2, 2000L);
        var unknownLater = note(20, null);
        var unknownFirst = note(10, null);
        var values =
                new ArrayList<>(Arrays.asList(unknownLater, null, earlier, later, unknownFirst));
        var comparator = NoteDisplay.observationDateComparator(ascending);
        values.sort(comparator);
        assertThat(values)
                .extracting(value -> value == null ? null : value.getNoteId())
                .containsExactly(ascending ? 3 : 2, ascending ? 2 : 3, 10, 20, null);
        assertThat(comparator.compare(later, unknownFirst)).isNegative();
        assertThat(comparator.compare(unknownFirst, later)).isPositive();
        assertThat(comparator.compare(unknownFirst, unknownLater)).isNegative();
    }

    private NoteDisplay note(int id, Long observed) {
        var note = mock(NoteDisplay.class);
        when(note.getNoteId()).thenReturn(id);
        when(note.getObservationDate()).thenReturn(observed == null ? null : new Date(observed));
        when(note.getUuid()).thenReturn("owned-" + id);
        return note;
    }
}
