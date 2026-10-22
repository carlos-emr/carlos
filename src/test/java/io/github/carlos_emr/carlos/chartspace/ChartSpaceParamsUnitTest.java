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
package io.github.carlos_emr.carlos.chartspace;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChartSpaceParams}.
 *
 * @since 2026-10-08
 */
@Tag("unit")
class ChartSpaceParamsUnitTest {

    @Test
    void shouldReturnValue_forPlainDigits() {
        assertThat(ChartSpaceParams.parseDemographicNo("2")).isEqualTo(OptionalInt.of(2));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "-1", "0", "1 2", " 2", "2.0", "99999999999", "2147483648"})
    void shouldReturnEmpty_forInvalidInput(String raw) {
        assertThat(ChartSpaceParams.parseDemographicNo(raw)).isEmpty();
    }

    @Test
    void shouldReturnValue_forIntMax() {
        assertThat(ChartSpaceParams.parseDemographicNo("2147483647")).isEqualTo(OptionalInt.of(Integer.MAX_VALUE));
    }
}
