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

/**
 * Save-side domain logic for the CARLOS Health Tracker
 * ({@code /encounter/oscarMeasurements/ViewHealthTracker}).
 *
 * <p><b>Ownership boundary.</b> Everything in this package is servlet-free and
 * Struts-free. The only web-layer entry point is
 * {@code io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.HealthTrackerUpdate2Action},
 * which performs the HTTP-method and {@code _measurement w} privilege checks,
 * copies request parameters into a plain map, and then hands off to
 * {@link io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerSubmissionService}.
 * Keeping the servlet API out of this package is what makes the save path
 * unit-testable without a container.
 *
 * <p><b>Layering.</b> Suffixes follow {@code docs/architecture/layer-names.md}:
 * <ul>
 *   <li>{@code HealthTrackerSubmissionParser} — turns the flat form-parameter map
 *       into typed {@code HealthTrackerEntry} values using the flowsheet definition.</li>
 *   <li>{@code HealthTrackerMeasurementPersister} — validates one entry against the
 *       measurement's {@code Validations} row and writes it to {@code measurements}.</li>
 *   <li>{@code HealthTrackerNoteComposer} — builds the encounter progress-note text
 *       for the entries the clinician flagged with "add to progress note".</li>
 *   <li>{@code HealthTrackerSubmissionService} — orchestrates the three above and
 *       returns a {@code HealthTrackerSubmissionResult}.</li>
 * </ul>
 *
 * <p><b>History.</b> This package replaces the GPL2-only
 * {@code FormUpdate2Action} that the Health Tracker shared with the retired
 * Indivica DiabFlowSheet page. That action was deleted in PR #579; the Health
 * Tracker restore re-implements the same behaviour as GPL2+ CARLOS code with
 * validation, persistence and note composition split apart so each is testable.
 *
 * @since 2026-09-20
 */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker;
