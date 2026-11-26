/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.prescript.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * {@link RxUtil#FloatToString(float)} renders the structured dose that the Rx page and the
 * {@code updateQty} action parse back with {@code Double.parseDouble}. Grouped digits ("1,000")
 * made that parse throw for a 1000-unit dose, so the AJAX answer was lost.
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("RxUtil.FloatToString unit tests")
class RxUtilFloatToStringUnitTest extends CarlosUnitTestBase {

    @ParameterizedTest(name = "{0} renders as {1}")
    @CsvSource({
        "1000, 1000",
        "30000, 30000",
        "0.5, 0.5",
        "1.5, 1.5",
        "2, 2",
        "0.25, 0.25"
    })
    @DisplayName("should render doses without digit grouping so callers can parse them back")
    void shouldRenderWithoutDigitGrouping_forDoseValues(float value, String expected) {
        assertThat(RxUtil.FloatToString(value)).isEqualTo(expected);
    }

    /**
     * The text was rounded to three decimal places (HALF_EVEN), so a stored 0.0625 printed as
     * "0.062" and 0.0004 as "0". The Rx page posts this text back as the dose, which made the
     * rounding permanent on the next save (#6185).
     */
    @ParameterizedTest(name = "{0} renders as {1}")
    @CsvSource({
        "0.0625, 0.0625",
        "0.3125, 0.3125",
        "0.1875, 0.1875",
        "1.0625, 1.0625",
        "0.0004, 0.0004",
        "0.0005, 0.0005"
    })
    @DisplayName("should render every decimal place of a dose finer than a thousandth")
    void shouldRenderAllDecimalPlaces_forDoseFinerThanThousandths(float value, String expected) {
        assertThat(RxUtil.FloatToString(value)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} reads back unchanged")
    @ValueSource(floats = {0.0625f, 0.3125f, 0.0004f, 0.33333334f, 0.6666667f, 1.0E7f, 2.5f, 1f})
    @DisplayName("should render text that parses back to the same stored dose")
    void shouldReadBackUnchanged_whenRenderedDoseIsPostedBack(float value) {
        assertThat(Float.parseFloat(RxUtil.FloatToString(value))).isEqualTo(value);
    }

    @ParameterizedTest(name = "{0} renders as {1}")
    @CsvSource({"1.0, 1", "500.0, 500", "1.0E7, 10000000", "0.5, 0.5"})
    @DisplayName("should render no trailing zero, no exponent and a leading zero")
    void shouldRenderPlainDecimal_forWholeAndFractionalDoses(float value, String expected) {
        assertThat(RxUtil.FloatToString(value)).isEqualTo(expected);
    }
}
