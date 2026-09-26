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
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerMeasurementPersister.EntryOutcome;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctValidation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HealthTrackerMeasurementPersister}.
 *
 * <p>Covers the three outcomes the save path has to tell apart: rejected by
 * validation, accepted but a duplicate of an existing row, and actually written.
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
class HealthTrackerMeasurementPersisterUnitTest {

    private MeasurementDao measurementDao;
    private EctValidation validation;
    private HealthTrackerMeasurementPersister persister;

    @BeforeEach
    void setUp() {
        measurementDao = mock(MeasurementDao.class);
        validation = mock(EctValidation.class);
        persister = new HealthTrackerMeasurementPersister(measurementDao, validation);

        // Default: every check passes and there is no Validations row.
        lenient().when(validation.getValidationType(anyString(), anyString())).thenReturn(List.of());
        lenient().when(validation.isInRange(any(), any(), anyString())).thenReturn(true);
        lenient().when(validation.maxLength(any(), anyString())).thenReturn(true);
        lenient().when(validation.minLength(any(), anyString())).thenReturn(true);
        lenient().when(validation.matchRegExp(any(), anyString())).thenReturn(true);
        lenient().when(validation.isValidBloodPressure(any(), anyString())).thenReturn(true);
        lenient().when(validation.isNumeric(any(), anyString())).thenReturn(true);
        lenient().when(validation.isDate(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("should persist the measurement when every validation passes")
    void shouldPersistMeasurement_whenEntryValid() {
        when(measurementDao.findMatching(any(Measurement.class))).thenReturn(List.of());

        EntryOutcome outcome = persister.persist(entry("82.5", "post-op", "2026-09-01"), 111, "999998", 42);

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.persisted()).isTrue();

        ArgumentCaptor<Measurement> saved = ArgumentCaptor.forClass(Measurement.class);
        verify(measurementDao).persist(saved.capture());
        Measurement measurement = saved.getValue();
        assertThat(measurement.getDemographicId()).isEqualTo(111);
        assertThat(measurement.getProviderNo()).isEqualTo("999998");
        assertThat(measurement.getType()).isEqualTo("WT");
        assertThat(measurement.getDataField()).isEqualTo("82.5");
        assertThat(measurement.getMeasuringInstruction()).isEqualTo("kg");
        assertThat(measurement.getComments()).isEqualTo("post-op");
        assertThat(measurement.getAppointmentNo()).isEqualTo(42);
    }

    @Test
    @DisplayName("should store an empty comment the way every other writer stores it")
    void shouldStoreEmptyComment_whenCommentEmpty() {
        // Not " ": EctMeasurements2Action and WriteNewMeasurements both store the
        // raw parameter, and findMatching compares comments with exact equality, so
        // a private representation here would miss a row the Add Measurement path
        // wrote and duplicate it.
        when(measurementDao.findMatching(any(Measurement.class))).thenReturn(List.of());

        persister.persist(entry("82.5", "", "2026-09-01"), 111, "999998", 0);

        ArgumentCaptor<Measurement> saved = ArgumentCaptor.forClass(Measurement.class);
        verify(measurementDao).persist(saved.capture());
        assertThat(saved.getValue().getComments()).isEmpty();
    }

    @Test
    @DisplayName("should reject a value longer than the measurements column")
    void shouldRejectEntry_whenValueExceedsColumnLength() {
        // measurements.dataField is varchar(255) too, and a Validations row is free
        // to set no maximum length at all -- so the column limit is checked even
        // when validation had nothing to say about this measurement.
        String tooLong = "x".repeat(256);

        EntryOutcome outcome = persister.persist(entry(tooLong, "", "2026-09-01"), 111, "999998", 0);

        assertThat(outcome.valid()).isFalse();
        assertThat(outcome.failures()).extracting(
                HealthTrackerMeasurementPersister.ValidationFailure::messageKey).contains("errors.maxlength");
        verify(measurementDao, never()).persist(any(Measurement.class));
    }

    @Test
    @DisplayName("should not write a second row when an identical measurement already exists")
    void shouldSkipPersist_whenDuplicateExists() {
        when(measurementDao.findMatching(any(Measurement.class))).thenReturn(List.of(new Measurement()));

        EntryOutcome outcome = persister.persist(entry("82.5", "", "2026-09-01"), 111, "999998", 0);

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.persisted()).isFalse();
        verify(measurementDao, never()).persist(any(Measurement.class));
    }

    @Test
    @DisplayName("should reject the entry and persist nothing when the value is out of range")
    void shouldRejectEntry_whenValueOutOfRange() {
        when(validation.isInRange(any(), any(), anyString())).thenReturn(false);

        EntryOutcome outcome = persister.persist(entry("999", "", "2026-09-01"), 111, "999998", 0);

        assertThat(outcome.valid()).isFalse();
        assertThat(outcome.persisted()).isFalse();
        assertThat(outcome.failures()).extracting(
                HealthTrackerMeasurementPersister.ValidationFailure::messageKey).contains("errors.range");
        verify(measurementDao, never()).persist(any(Measurement.class));
    }

    @Test
    @DisplayName("should reject the entry when the observation date is not a date")
    void shouldRejectEntry_whenDateInvalid() {
        when(validation.isDate(anyString())).thenReturn(false);

        EntryOutcome outcome = persister.persist(entry("82.5", "", "not-a-date"), 111, "999998", 0);

        assertThat(outcome.valid()).isFalse();
        assertThat(outcome.failures()).extracting(
                HealthTrackerMeasurementPersister.ValidationFailure::messageKey).contains("errors.invalidDate");
        verify(measurementDao, never()).persist(any(Measurement.class));
    }

    @Test
    @DisplayName("should reject a malformed blood pressure under the key the bundles define")
    void shouldRejectEntry_whenBloodPressureInvalid() {
        // The key must be errors.bloodPressure, which every bundle carries. The earlier
        // error.bloodPressure existed in none of them, so Struts rendered the key itself.
        when(validation.isValidBloodPressure(any(), anyString())).thenReturn(false);

        EntryOutcome outcome = persister.persist(bloodPressure("120-80", "2026-09-01"), 111, "999998", 0);

        assertThat(outcome.valid()).isFalse();
        assertThat(outcome.persisted()).isFalse();
        assertThat(outcome.failures()).extracting(
                HealthTrackerMeasurementPersister.ValidationFailure::messageKey).contains("errors.bloodPressure");
        assertThat(outcome.failures()).extracting(
                HealthTrackerMeasurementPersister.ValidationFailure::messageKey).doesNotContain("error.bloodPressure");
        verify(measurementDao, never()).persist(any(Measurement.class));
    }

    @Test
    @DisplayName("should collect every failure rather than stopping at the first")
    void shouldCollectAllFailures_whenSeveralChecksFail() {
        when(validation.isInRange(any(), any(), anyString())).thenReturn(false);
        when(validation.matchRegExp(any(), anyString())).thenReturn(false);

        EntryOutcome outcome = persister.persist(entry("??", "", "2026-09-01"), 111, "999998", 0);

        assertThat(outcome.failures()).extracting(
                HealthTrackerMeasurementPersister.ValidationFailure::messageKey)
                .contains("errors.range", "errors.invalid");
    }

    @Test
    @DisplayName("should reject a comment longer than the measurements column")
    void shouldRejectEntry_whenCommentExceedsColumnLength() {
        // measurements.comments is varchar(255). Left to the database this throws
        // under strict SQL modes, and because a Health Tracker save is deliberately
        // partial-success that would land after earlier rows were already committed.
        String tooLong = "x".repeat(256);

        EntryOutcome outcome = persister.persist(entry("82.5", tooLong, "2026-09-01"), 111, "999998", 0);

        assertThat(outcome.valid()).isFalse();
        assertThat(outcome.failures()).extracting(
                HealthTrackerMeasurementPersister.ValidationFailure::messageKey).contains("errors.maxlength");
        verify(measurementDao, never()).persist(any(Measurement.class));
    }

    @Test
    @DisplayName("should accept a comment exactly at the column length")
    void shouldPersistMeasurement_whenCommentAtColumnLength() {
        when(measurementDao.findMatching(any(Measurement.class))).thenReturn(List.of());
        String atLimit = "x".repeat(255);

        EntryOutcome outcome = persister.persist(entry("82.5", atLimit, "2026-09-01"), 111, "999998", 0);

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.persisted()).isTrue();
    }

    @Test
    @DisplayName("should tolerate a Validations row whose numeric bounds are null")
    void shouldNotThrow_whenValidationBoundsNull() {
        Validations sparse = new Validations();
        sparse.setName("No Validations");
        when(validation.getValidationType(anyString(), anyString())).thenReturn(List.of(sparse));
        when(measurementDao.findMatching(any(Measurement.class))).thenReturn(List.of());

        EntryOutcome outcome = persister.persist(entry("82.5", "", "2026-09-01"), 111, "999998", 0);

        assertThat(outcome.valid()).isTrue();
        assertThat(outcome.persisted()).isTrue();
    }

    private static HealthTrackerEntry entry(String value, String comment, String date) {
        return new HealthTrackerEntry("WT", "Weightkg", "Weight (kg)", "kg", value, comment, date, false);
    }

    private static HealthTrackerEntry bloodPressure(String value, String date) {
        return new HealthTrackerEntry("BP", "BloodPressure", "Blood Pressure", "mmHg", value, "", date, false);
    }
}
