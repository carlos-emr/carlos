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

/**
 * One measurement the clinician typed into the Health Tracker form.
 *
 * <p>The Health Tracker posts a flat form whose field names are derived from the
 * flowsheet item's <em>measurement type</em> by
 * {@link HealthTrackerSubmissionParser#fieldNameFor(String)}, which escapes it
 * injectively so two types can never share a field. Display names cannot be used
 * for this: a clinician may rename two items to the same label. The derived name
 * is {@link #fieldName()}, kept alongside the type so nothing downstream has to
 * re-derive the mapping.
 *
 * @param measurementType the {@code measurementType.type} code the row is stored
 *        under (e.g. {@code BP}, {@code HT}) — the value written to
 *        {@code measurements.type}
 * @param fieldName the escaped form-field name the browser submitted under
 * @param displayName the flowsheet item's human label, used in progress-note text
 *        and in the "these values were rejected" feedback
 * @param measuringInstruction the measurement type's measuring instruction (units
 *        or, for BP, the systolic/diastolic hint); persisted alongside the value
 *        and also used to select the right {@code Validations} row
 * @param value the raw value as typed; never blank (blank entries are dropped
 *        during parsing rather than represented here)
 * @param comment the optional free-text comment, or {@code ""} when none was given
 * @param dateObserved the observation date in {@code yyyy-MM-dd} form; falls back
 *        to the form's hidden default date when the per-row date box was cleared
 * @param addToNote whether the clinician ticked "add to progress note" for this row
 */
public record HealthTrackerEntry(
        String measurementType,
        String fieldName,
        String displayName,
        String measuringInstruction,
        String value,
        String comment,
        String dateObserved,
        boolean addToNote) {
}
