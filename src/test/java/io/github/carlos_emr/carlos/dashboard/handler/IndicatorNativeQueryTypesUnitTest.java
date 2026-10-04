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
package io.github.carlos_emr.carlos.dashboard.handler;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class IndicatorNativeQueryTypesUnitTest {
    static Stream<Arguments> scalars() {
        return Stream.of(Arguments.of(1L, 3L), Arguments.of(1, 3), Arguments.of(BigInteger.ONE, BigInteger.valueOf(3)),
                Arguments.of(new BigDecimal("0.25"), new BigDecimal("0.75")), Arguments.of(0.25d, 0.75d),
                Arguments.of("1", "3"), Arguments.of(1L, new BigDecimal("3")),
                Arguments.of(1_000_000_000L, 3_000_000_000L));
    }

    @ParameterizedTest
    @MethodSource("scalars")
    void shouldCalculatePercentages_whenDriversReturnDifferentNumericTypes(Object first, Object second) {
        var row = new LinkedHashMap<String, Object>();
        row.put("Recorded", first);
        row.put("Missing", second);
        var plots = IndicatorQueryHandler.createGraphPlots(List.of(row), false).getFirst();
        assertThat(plots).hasSize(2);
        assertThat(plots[0].getNumerator()).isEqualTo(25d);
        assertThat(plots[1].getNumerator()).isEqualTo(75d);
        assertThat(plots[0].getDenominator()).isEqualTo(100d);
    }

    @Test
    void shouldKeepCounts_whenNumberDisplaySelected() {
        var row = new LinkedHashMap<String, Object>();
        row.put("Patients", 3L);
        var plot = IndicatorQueryHandler.createGraphPlots(List.of(row), true).getFirst()[0];
        assertThat(plot.getNumerator()).isEqualTo(3d);
        assertThat(plot.getDenominator()).isEqualTo(1d);
        assertThat(row.get("Patients")).isEqualTo(3L);
    }

    @Test
    void shouldProduceZero_whenAggregatesAreNullOrZero() {
        var row = new LinkedHashMap<String, Object>();
        row.put("Recorded", 0L);
        row.put("Missing", null);
        var plots = IndicatorQueryHandler.createGraphPlots(List.of(row), false).getFirst();
        assertThat(plots[0].getNumerator()).isZero();
        assertThat(plots[1].getNumerator()).isZero();
    }
}
