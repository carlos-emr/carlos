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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
class RptCheckGuidelineUnitTest {

    private final RptCheckGuideline check = new RptCheckGuideline();

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
}
