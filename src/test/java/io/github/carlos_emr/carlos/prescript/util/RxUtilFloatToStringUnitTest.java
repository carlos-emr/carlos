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
}
