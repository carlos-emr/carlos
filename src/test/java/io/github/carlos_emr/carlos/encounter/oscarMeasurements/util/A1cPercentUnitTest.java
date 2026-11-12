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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link A1cPercent}, the fraction-to-percent normalisation applied to
 * A1C values before flowsheet flagging and graphing (issue #4614).
 */
@Tag("unit")
@DisplayName("A1cPercent")
class A1cPercentUnitTest {

    @Test
    void shouldScaleFractionToPercent_forFractionValue() {
        assertThat(A1cPercent.toPercent(0.095)).isEqualTo(9.5);
        assertThat(A1cPercent.toPercent(0.071)).isEqualTo(7.1);
        assertThat(A1cPercent.toPercent(0.04)).isEqualTo(4.0);
    }

    @Test
    void shouldLeavePercentUnchanged_forValueOfOneOrMore() {
        assertThat(A1cPercent.toPercent(1.0)).isEqualTo(1.0);
        assertThat(A1cPercent.toPercent(7.1)).isEqualTo(7.1);
        assertThat(A1cPercent.toPercent(8.4)).isEqualTo(8.4);
    }

    @Test
    void shouldLeaveValueUnchanged_forZeroOrNegative() {
        assertThat(A1cPercent.toPercent(0.0)).isEqualTo(0.0);
        assertThat(A1cPercent.toPercent(-1.0)).isEqualTo(-1.0);
    }

    @Test
    void shouldParseAndScaleText_forFractionString() {
        assertThat(A1cPercent.normalize("0.095")).isEqualTo("9.5");
        assertThat(A1cPercent.normalize("7.1")).isEqualTo("7.1");
        assertThat(A1cPercent.normalize("abc")).isEqualTo("abc");
        assertThat(A1cPercent.normalize(null)).isNull();
    }

    @Test
    void shouldRecogniseA1cType_onlyForA1cCode() {
        assertThat(A1cPercent.appliesTo("A1C")).isTrue();
        assertThat(A1cPercent.appliesTo("BP")).isFalse();
        assertThat(A1cPercent.appliesTo(null)).isFalse();
    }
}
