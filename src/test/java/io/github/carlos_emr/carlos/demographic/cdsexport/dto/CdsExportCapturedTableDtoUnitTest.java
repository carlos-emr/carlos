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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A captured note table carries one access scope for each of its notes, however many the chart
 * has, and still refuses scopes that are not exactly its rows.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportCapturedTableDtoUnitTest {
    private static final Set<String> COLUMNS =
            Set.of("note_id", "demographic_no", "program_no", "reporter_caisi_role", "locked", "note");

    private static List<CdsExportNoteAccessDto> scopes(int count) {
        var scopes = new ArrayList<CdsExportNoteAccessDto>(count);
        for (int note = 1; note <= count; note++)
            scopes.add(new CdsExportNoteAccessDto(note, 27, "10", "3"));
        return scopes;
    }

    private static CdsExportCapturedTableDto table(long rows, List<CdsExportNoteAccessDto> scopes) {
        return new CdsExportCapturedTableDto(COLUMNS, rows, "a".repeat(64), List.of(), scopes);
    }

    @Test
    void shouldCarryEveryNoteScope_whenAChartHasMoreThan8192Notes() {
        // 8,192 was the most a chart could have; one more, and a chart ten times as long.
        for (int notes : new int[] {8_192, 8_193, 81_920}) {
            var table = table(notes, scopes(notes));

            assertThat(table.rows()).isEqualTo(notes);
            assertThat(table.noteAccess()).hasSize(notes);
            assertThat(table.noteAccess().getLast().noteId()).isEqualTo(notes);
        }
    }

    @Test
    void shouldRefuseScopes_thatAreNotExactlyTheTablesNotes() {
        var scopes = scopes(8_193);
        // One scope fewer or more than the rows, and one note scoped twice.
        assertThatThrownBy(() -> table(8_192, scopes))
                .hasMessage("CDS_EXPORT_CAPTURE_NOTE_ACCESS_INVALID");
        assertThatThrownBy(() -> table(8_194, scopes))
                .hasMessage("CDS_EXPORT_CAPTURE_NOTE_ACCESS_INVALID");
        var twice = new ArrayList<>(scopes);
        twice.set(8_192, scopes.getFirst());
        assertThatThrownBy(() -> table(8_193, twice))
                .hasMessage("CDS_EXPORT_CAPTURE_NOTE_ACCESS_INVALID");
        // And a table that lacks a column its scopes depend on.
        assertThatThrownBy(
                        () ->
                                new CdsExportCapturedTableDto(
                                        Set.of("note_id", "note"),
                                        8_193,
                                        "a".repeat(64),
                                        List.of(),
                                        scopes))
                .hasMessage("CDS_EXPORT_CAPTURE_NOTE_ACCESS_INVALID");
    }
}
