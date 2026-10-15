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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HealthTrackerNoteComposer}.
 *
 * <p>The composer decides whether a Health Tracker save also writes an encounter
 * note, so "nothing was flagged" has to stay distinguishable from "a note with
 * no content".
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
class HealthTrackerNoteComposerUnitTest {

    private static final Date NOTE_DATE = Date.from(
            LocalDate.of(2026, 9, 20).atStartOfDay(ZoneId.systemDefault()).toInstant());

    private final HealthTrackerNoteComposer composer = new HealthTrackerNoteComposer();

    @Test
    @DisplayName("should return empty text when no entry was flagged for the note")
    void shouldReturnEmptyText_whenNoEntryFlagged() {
        assertThat(composer.compose(List.of(entry("82.5", "", false)), NOTE_DATE)).isEmpty();
    }

    @Test
    @DisplayName("should return empty text when there are no entries at all")
    void shouldReturnEmptyText_whenNoEntries() {
        assertThat(composer.compose(List.of(), NOTE_DATE)).isEmpty();
        assertThat(composer.compose(null, NOTE_DATE)).isEmpty();
    }

    @Test
    @DisplayName("should write the label, value, units and observation date for a flagged entry")
    void shouldComposeNote_whenEntryFlagged() {
        String note = composer.compose(List.of(entry("82.5", "", true)), NOTE_DATE);

        assertThat(note).startsWith("[20-Sep-2026 .: ]");
        assertThat(note).contains("Weight (kg): 82.5 kg");
        assertThat(note).contains("Date Observed: 2026-09-01");
    }

    @Test
    @DisplayName("should include the comment when one was entered")
    void shouldIncludeComment_whenCommentPresent() {
        String note = composer.compose(List.of(entry("82.5", "post-op", true)), NOTE_DATE);

        assertThat(note).contains("comment: post-op");
    }

    @Test
    @DisplayName("should leave out the comment line when no comment was entered")
    void shouldOmitCommentLine_whenCommentEmpty() {
        assertThat(composer.compose(List.of(entry("82.5", "", true)), NOTE_DATE)).doesNotContain("comment:");
    }

    @Test
    @DisplayName("should include only the flagged entries")
    void shouldIncludeOnlyFlaggedEntries_whenMixed() {
        HealthTrackerEntry flagged = new HealthTrackerEntry(
                "BP", "BP", "Blood Pressure", "systolic/diastolic", "120/80", "", "2026-09-01", true);

        String note = composer.compose(List.of(entry("82.5", "", false), flagged), NOTE_DATE);

        assertThat(note).contains("Blood Pressure: 120/80 systolic/diastolic");
        assertThat(note).doesNotContain("Weight (kg)");
    }

    private static HealthTrackerEntry entry(String value, String comment, boolean addToNote) {
        return new HealthTrackerEntry("WT", "Weightkg", "Weight (kg)", "kg", value, comment, "2026-09-01", addToNote);
    }
}
