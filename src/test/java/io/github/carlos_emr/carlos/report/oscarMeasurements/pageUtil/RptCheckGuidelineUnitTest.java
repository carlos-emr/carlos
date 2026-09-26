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

package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins how the CDM "patients met guideline" and "abnormal range" reports judge non-numeric
 * readings: yes/no guidelines behave as before, and categorical guidelines such as the Asthma
 * Action Plan's Provided/Revised/Reviewed (issue #3893) match on the recorded value.
 *
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
class RptCheckGuidelineUnitTest extends CarlosUnitTestBase {

    private final RptCheckGuideline check = new RptCheckGuideline();
    private final MeasurementTypeDao measurementTypeDao = mock(MeasurementTypeDao.class);
    private final ValidationsDao validationsDao = mock(ValidationsDao.class);

    @BeforeEach
    void registerDaos() {
        registerMock(MeasurementTypeDao.class, measurementTypeDao);
        registerMock(ValidationsDao.class, validationsDao);
    }

    @Test
    @DisplayName("should treat a rule with no isNumeric flag as non-numeric instead of throwing")
    void shouldReturnZero_whenValidationHasNoNumericFlag() {
        when(measurementTypeDao.findByType("AACP")).thenReturn(List.of(type("12")));
        Validations rule = new Validations();
        rule.setNumeric(null);
        when(validationsDao.find((Object) Integer.valueOf(12))).thenReturn(rule);

        assertThat(check.getValidation("AACP")).isZero();
    }

    @Test
    @DisplayName("should report a numeric rule as numeric")
    void shouldReturnOne_whenValidationIsNumeric() {
        when(measurementTypeDao.findByType("WT")).thenReturn(List.of(type("3")));
        Validations rule = new Validations();
        rule.setNumeric(Boolean.TRUE);
        when(validationsDao.find((Object) Integer.valueOf(3))).thenReturn(rule);

        assertThat(check.getValidation("WT")).isEqualTo(1);
    }

    private static MeasurementType type(String validation) {
        MeasurementType mt = new MeasurementType();
        mt.setValidation(validation);
        return mt;
    }

    @Test
    @DisplayName("should keep the yes/no matching for legacy Yes/No readings")
    void shouldMatchYesNoReadings_forYesNoGuideline() {
        assertThat(check.isYesNoMetGuideline("Yes", "Yes")).isTrue();
        assertThat(check.isYesNoMetGuideline("Y", "yes")).isTrue();
        assertThat(check.isYesNoMetGuideline("No", "Yes")).isFalse();
        assertThat(check.isYesNoMetGuideline("No", "NO")).isTrue();
        assertThat(check.isYesNoMetGuideline("Yes", "N")).isFalse();
    }

    @Test
    @DisplayName("should count a Provided/Revised/Reviewed reading that equals the guideline")
    void shouldMatchCategoricalReading_forProvidedGuideline() {
        assertThat(check.isYesNoMetGuideline("Provided", "Provided")).isTrue();
        assertThat(check.isYesNoMetGuideline(" Reviewed ", "Reviewed")).isTrue();
    }

    @Test
    @DisplayName("should not count a categorical reading with another value, a legacy Yes, or no value")
    void shouldRejectOtherReadings_forCategoricalGuideline() {
        assertThat(check.isYesNoMetGuideline("Revised", "Provided")).isFalse();
        assertThat(check.isYesNoMetGuideline("Yes", "Provided")).isFalse();
        assertThat(check.isYesNoMetGuideline(null, "Provided")).isFalse();
    }

    @Test
    @DisplayName("should match a legacy NA or NotApplicable reading for either not-applicable guideline")
    void shouldMatchNotApplicableSpellings_forNotApplicableGuideline() {
        // Both spellings come from the seeded Yes/No/NA rule (YES|yes|Yes|Y|NO|no|No|N|NotApplicable|NA).
        assertThat(check.isYesNoMetGuideline("NA", "NA")).isTrue();
        assertThat(check.isYesNoMetGuideline("NotApplicable", "NA")).isTrue();
        assertThat(check.isYesNoMetGuideline("NA", "NotApplicable")).isTrue();
        assertThat(check.isYesNoMetGuideline(" NotApplicable ", "NotApplicable")).isTrue();
        assertThat(check.isYesNoMetGuideline("Yes", "NA")).isFalse();
        assertThat(check.isYesNoMetGuideline("No", "NotApplicable")).isFalse();
        assertThat(check.isYesNoMetGuideline(null, "NA")).isFalse();
        // A legacy NA reading never satisfies a Provided/Revised/Reviewed guideline.
        assertThat(check.isYesNoMetGuideline("NA", "Provided")).isFalse();
        assertThat(check.isYesNoMetGuideline("NotApplicable", "Reviewed")).isFalse();
    }

    @Test
    @DisplayName("should not count blank readings as meeting a blank guideline")
    void shouldRejectBlankReadings_forBlankGuideline() {
        assertThat(check.isYesNoMetGuideline("", "")).isFalse();
        assertThat(check.isYesNoMetGuideline("  ", " ")).isFalse();
        assertThat(check.isYesNoMetGuideline("Provided", "")).isFalse();
    }
}
