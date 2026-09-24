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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Builds the encounter progress-note body for the Health Tracker rows the
 * clinician ticked "add to progress note" on.
 *
 * <p>Only opted-in rows appear. A Health Tracker save that touches ten
 * measurements but flags none produces no note at all — that is the behaviour
 * clinicians expect, and it is why {@link #compose} returns {@code ""} rather
 * than a header-only note.
 */
public class HealthTrackerNoteComposer {

    /**
     * Composes the note body.
     *
     * @param entries the entries that were accepted and persisted, in submission
     *        order. Entries with {@link HealthTrackerEntry#addToNote()} false are
     *        ignored
     * @param noteDate the note's timestamp, used for the {@code [dd-MMM-yyyy .: ]}
     *        header line that CARLOS progress notes carry
     * @return the note body, or {@code ""} when no entry was flagged for the note
     */
    public String compose(List<HealthTrackerEntry> entries, Date noteDate) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }

        StringBuilder body = new StringBuilder();
        for (HealthTrackerEntry entry : entries) {
            if (!entry.addToNote()) {
                continue;
            }
            body.append(entry.displayName()).append(": ").append(entry.value());
            // For BP the value alone ("120/80") is ambiguous without the measuring
            // instruction, which carries the systolic/diastolic convention.
            if (entry.measuringInstruction() != null && !entry.measuringInstruction().isBlank()) {
                body.append(' ').append(entry.measuringInstruction());
            }
            body.append("\n Date Observed: ").append(entry.dateObserved());
            if (!entry.comment().isEmpty()) {
                body.append("\n comment: ").append(entry.comment());
            }
            body.append("\n\n ");
        }

        if (body.isEmpty()) {
            return "";
        }

        // Locale.CANADA keeps the month abbreviation stable regardless of the
        // server default locale; these headers are parsed by downstream note tooling.
        SimpleDateFormat headerFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.CANADA);
        return "[" + headerFormat.format(noteDate) + " .: ]\n" + body;
    }
}
