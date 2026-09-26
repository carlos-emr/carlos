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

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctValidation;
import io.github.carlos_emr.carlos.util.ConversionUtils;

import org.apache.commons.validator.GenericValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a single {@link HealthTrackerEntry} and, when it passes, writes it to
 * the {@code measurements} table.
 *
 * <p>Validation reuses {@link EctValidation} so the Health Tracker enforces exactly
 * the same {@code Validations} rows (range, length, regex, blood-pressure shape,
 * observation date) as the Add Measurement and flowsheet paths. The browser-side
 * {@code pattern}/{@code min}/{@code max} attributes the page renders are user
 * feedback only — this class is the authoritative check.
 */
public class HealthTrackerMeasurementPersister {

    /**
     * A failed validation, expressed as a resource-bundle key plus its arguments
     * rather than a formatted string.
     *
     * <p>Formatting is deliberately left to the web layer: this class has no
     * {@code ActionSupport} and therefore no {@code getText()}, and keeping it that
     * way is what lets the save path be unit-tested without Struts.
     *
     * @param messageKey the {@code oscarResources} key (e.g. {@code errors.range})
     * @param arguments substitution arguments for that key
     */
    public record ValidationFailure(String messageKey, List<String> arguments) {

        public ValidationFailure {
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * Result of persisting one entry.
     *
     * @param persisted {@code true} only when a new row was actually written.
     *        A valid entry that duplicates an existing row reports {@code false}
     *        with no failures — accepted, but deliberately not stored twice
     * @param failures the validation failures, empty when the entry was accepted
     */
    public record EntryOutcome(boolean persisted, List<ValidationFailure> failures) {

        public EntryOutcome {
            failures = List.copyOf(failures);
        }

        public boolean valid() {
            return failures.isEmpty();
        }
    }

    /**
     * Length of {@code measurements.comments} and {@code measurements.dataField},
     * both {@code varchar(255)}. A longer value is refused here rather than left to
     * the database: under strict SQL modes the insert throws, and because a Health
     * Tracker save is deliberately partial-success, that exception would surface
     * after earlier rows in the same submission had already been committed.
     *
     * <p>A {@code Validations} row may set a shorter maximum, which the length check
     * above already enforces, but it may equally set none at all -- so the column
     * limit is checked unconditionally.
     */
    private static final int MAX_COLUMN_LENGTH = 255;

    private final MeasurementDao measurementDao;
    private final EctValidation validation;

    public HealthTrackerMeasurementPersister(MeasurementDao measurementDao, EctValidation validation) {
        this.measurementDao = measurementDao;
        this.validation = validation;
    }

    public HealthTrackerMeasurementPersister(MeasurementDao measurementDao) {
        this(measurementDao, new EctValidation());
    }

    /**
     * Validates {@code entry} and persists it when valid.
     *
     * @param entry the parsed form row
     * @param demographicNo the patient the Health Tracker page was opened for
     * @param providerNo the logged-in provider, recorded as the measurement's author
     * @param appointmentNo the current appointment, or {@code 0} when the page was
     *        not opened from an appointment
     * @return the outcome; never {@code null}
     */
    public EntryOutcome persist(HealthTrackerEntry entry, int demographicNo, String providerNo, int appointmentNo) {
        List<ValidationFailure> failures = validate(entry);
        if (!failures.isEmpty()) {
            return new EntryOutcome(false, failures);
        }

        if (GenericValidator.isBlankOrNull(entry.value())) {
            return new EntryOutcome(false, List.of());
        }

        Measurement measurement = new Measurement();
        measurement.setDemographicId(demographicNo);
        measurement.setProviderNo(providerNo);
        measurement.setType(entry.measurementType());
        measurement.setDataField(entry.value());
        measurement.setMeasuringInstruction(entry.measuringInstruction());
        // Stored exactly as entered, empty string included. Every other writer on
        // this table (EctMeasurements2Action, WriteNewMeasurements) stores the raw
        // parameter, and MeasurementDao.findMatching compares comments with exact
        // equality -- so normalizing "" to " " here would both introduce a
        // representation nothing else uses and make the duplicate check below miss
        // a row the Add Measurement path had already written.
        measurement.setComments(entry.comment());
        measurement.setDateObserved(ConversionUtils.fromDateString(entry.dateObserved()));
        measurement.setAppointmentNo(appointmentNo);

        // Re-submitting the page (browser refresh, back button, a second Save All
        // after the first returned) must not create a second identical row.
        //
        // This is a read-then-write and therefore covers SEQUENTIAL re-submits, not
        // two saves genuinely in flight at once: measurements has no unique index
        // over (demographic, type, value, date, instruction, comment), so there is
        // nothing to serialize on. Closing that window properly needs a constraint
        // and a migration that first has to reckon with the duplicate rows already
        // in deployed charts. The page meanwhile disables Save All on submit, which
        // removes the double-click that makes the race reachable in practice.
        if (!measurementDao.findMatching(measurement).isEmpty()) {
            return new EntryOutcome(false, List.of());
        }

        measurementDao.persist(measurement);
        return new EntryOutcome(true, List.of());
    }

    private List<ValidationFailure> validate(HealthTrackerEntry entry) {
        List<ValidationFailure> failures = new ArrayList<>();

        Double maxValue = Double.valueOf(0);
        Double minValue = Double.valueOf(0);
        Integer maxLength = 0;
        Integer minLength = 0;
        String regExp = null;
        Boolean numeric = null;

        List<Validations> validations =
                validation.getValidationType(entry.measurementType(), entry.measuringInstruction());
        if (validations != null && !validations.isEmpty()) {
            Validations v = validations.iterator().next();
            maxValue = v.getMaxValue();
            minValue = v.getMinValue();
            maxLength = v.getMaxLength();
            minLength = v.getMinLength();
            regExp = v.getRegularExp();
            numeric = v.isNumeric();
        }

        // EctValidation treats these as primitives; a Validations row may leave any
        // of them null, which would otherwise NPE inside the range/length checks.
        maxValue = maxValue == null ? Double.valueOf(0) : maxValue;
        minValue = minValue == null ? Double.valueOf(0) : minValue;
        maxLength = maxLength == null ? 0 : maxLength;
        minLength = minLength == null ? 0 : minLength;

        String label = entry.displayName();
        String value = entry.value();

        if (!validation.isInRange(maxValue, minValue, value)) {
            failures.add(new ValidationFailure("errors.range",
                    List.of(label, Double.toString(minValue), Double.toString(maxValue))));
        }
        if (!validation.maxLength(maxLength, value)) {
            failures.add(new ValidationFailure("errors.maxlength",
                    List.of(label, Integer.toString(maxLength))));
        }
        if (!validation.minLength(minLength, value)) {
            failures.add(new ValidationFailure("errors.minlength",
                    List.of(label, Integer.toString(minLength))));
        }
        if (!validation.matchRegExp(regExp, value)) {
            failures.add(new ValidationFailure("errors.invalid", List.of(label)));
        }
        if (!validation.isValidBloodPressure(regExp, value)) {
            failures.add(new ValidationFailure("errors.bloodPressure", List.of()));
        }
        if (!validation.isNumeric(numeric, value)) {
            failures.add(new ValidationFailure("errors.numeric", List.of(label)));
        }
        if (!validation.isDate(entry.dateObserved())) {
            failures.add(new ValidationFailure("errors.invalidDate", List.of(label)));
        }
        if (value.length() > MAX_COLUMN_LENGTH) {
            failures.add(new ValidationFailure("errors.maxlength",
                    List.of(label, String.valueOf(MAX_COLUMN_LENGTH))));
        }
        if (entry.comment().length() > MAX_COLUMN_LENGTH) {
            failures.add(new ValidationFailure("errors.maxlength",
                    List.of(label + " comment", String.valueOf(MAX_COLUMN_LENGTH))));
        }
        return failures;
    }
}
