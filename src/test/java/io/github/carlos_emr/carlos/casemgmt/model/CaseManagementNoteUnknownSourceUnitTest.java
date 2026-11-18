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
package io.github.carlos_emr.carlos.casemgmt.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Tag("unit")
class CaseManagementNoteUnknownSourceUnitTest {
    @Test
    void
            shouldSortKnownDatesDescendingThenUnknownDatesByStableId_withoutInventingEncounterDate() {
        var unknownLater = note(20L, null);
        var earlier = note(3L, new Date(1000));
        var unknownFirst = note(10L, null);
        var later = note(2L, new Date(2000));
        var unknownUnsaved = note(null, null);
        List<CaseManagementNote> values =
                new ArrayList<>(
                        Arrays.asList(
                                unknownLater, null, earlier, unknownUnsaved, later, unknownFirst));
        values.sort(CaseManagementNote.noteObservationDateComparator);
        assertThat(values)
                .extracting(
                        value ->
                                value == null
                                        ? "absent"
                                        : value.getId() == null
                                                ? "unsaved"
                                                : value.getId().toString())
                .containsExactly("2", "3", "10", "20", "unsaved", "absent");
        assertThat(values.get(4)).isSameAs(unknownUnsaved);
        assertThat(unknownFirst.getObservation_date()).isNull();
        assertThat(
                        CaseManagementNote.noteObservationDateComparator.compare(
                                unknownFirst, unknownFirst))
                .isZero();
        values.sort(CaseManagementNote.observationDateComparator(true));
        assertThat(values)
                .extracting(
                        value ->
                                value == null
                                        ? "absent"
                                        : value.getId() == null
                                                ? "unsaved"
                                                : value.getId().toString())
                .containsExactly("3", "2", "10", "20", "unsaved", "absent");
    }

    @Test
    void shouldDistinguishAnUnknownSourceAuthor_fromADeletedLocalProvider() {
        var unknown = note(1L, null);
        unknown.setProviderNo("");
        assertThat(unknown.getProviderName()).isEqualTo("UNKNOWN");
        assertThat(unknown.getProviderNameFirstLast()).isEqualTo("UNKNOWN");
        var deleted = note(2L, null);
        deleted.setProviderNo("77");
        assertThat(deleted.getProviderName()).isEqualTo("DELETED");
        assertThat(deleted.getProviderNameFirstLast()).isEqualTo("DELETED");
    }

    private CaseManagementNote note(Long id, Date date) {
        var note = new CaseManagementNote();
        note.setId(id);
        note.setObservation_date(date);
        return note;
    }
}
