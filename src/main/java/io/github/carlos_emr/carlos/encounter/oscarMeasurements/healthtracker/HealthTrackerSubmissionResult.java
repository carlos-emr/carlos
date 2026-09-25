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

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerMeasurementPersister.ValidationFailure;

import java.util.List;

/**
 * Outcome of one Health Tracker save.
 *
 * <p>The page's contract is "save what is valid, tell the clinician what is not".
 * A submission is therefore never all-or-nothing: {@link #persistedCount()} and
 * {@link #rejected()} can both be non-zero for the same POST.
 *
 * @param persistedCount how many measurement rows were actually written. An entry
 *        that duplicates an existing row (same demographic, type, value, date,
 *        instruction and comment) does not count — it is accepted but not stored
 *        again, which is what stops a browser refresh from doubling values
 * @param rejected {@code "<label>: <value>"} strings for entries that failed
 *        validation, in submission order. These are echoed back into the page's
 *        validation alert, so they must stay limited to the label and the value
 *        the clinician just typed
 * @param failures the underlying validation failures behind {@link #rejected()},
 *        as resource-bundle keys plus arguments, for the web layer to turn into
 *        localized action errors
 * @param noteText the progress-note body composed from entries flagged "add to
 *        progress note", or {@code ""} when none were flagged. Empty means no note
 *        is written at all
 */
public record HealthTrackerSubmissionResult(
        int persistedCount,
        List<String> rejected,
        List<ValidationFailure> failures,
        String noteText) {

    /** Defensive copies so callers cannot mutate the result after the fact. */
    public HealthTrackerSubmissionResult {
        rejected = List.copyOf(rejected);
        failures = List.copyOf(failures);
    }

    /**
     * @return {@code true} when at least one entry failed validation, which is what
     *         drives the page's error result rather than a normal reload
     */
    public boolean hasRejections() {
        return !rejected.isEmpty();
    }
}
