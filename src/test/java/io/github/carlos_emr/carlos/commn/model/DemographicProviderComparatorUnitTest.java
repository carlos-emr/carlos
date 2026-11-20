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
package io.github.carlos_emr.carlos.commn.model;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Doctor sorting for patients whose responsible provider is missing.
 *
 * @since 2026-10-03
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("Demographic provider comparator")
class DemographicProviderComparatorUnitTest extends CarlosUnitTestBase {

    private static Stream<Arguments> providerPairs() {
        return Stream.of(
                Arguments.of(null, "2", -1),
                Arguments.of("2", null, 1),
                Arguments.of(null, null, 0),
                Arguments.of(null, "", -1),
                Arguments.of("", null, 1),
                Arguments.of("", "2", -1),
                Arguments.of("2", "2", 0),
                Arguments.of("10", "2", -1));
    }

    @ParameterizedTest
    @MethodSource("providerPairs")
    @DisplayName("should put missing providers first while preserving string order")
    void shouldOrderProviderNumbers_whenEitherProviderIsMissing(String left, String right, int sign) {
        assertThat(Integer.signum(Demographic.ProviderNoComparator.compare(patient(left), patient(right))))
                .isEqualTo(sign);
    }

    @Test
    @DisplayName("should retain every patient and stable ties when sorting mixed provider values")
    void shouldRetainPatientsAndStableTies_whenSortingMixedProviders() {
        Demographic firstMissing = patient(null);
        Demographic secondMissing = patient(null);
        Demographic blank = patient("");
        Demographic ten = patient("10");
        Demographic two = patient("2");
        List<Demographic> patients = new ArrayList<>(List.of(two, firstMissing, blank, secondMissing, ten));

        patients.sort(Demographic.ProviderNoComparator);

        assertThat(patients).extracting(Demographic::getProviderNo).containsExactly(null, null, "", "10", "2");
        assertThat(patients.get(0)).isSameAs(firstMissing);
        assertThat(patients.get(1)).isSameAs(secondMissing);
        assertThat(firstMissing.getProviderNo()).isNull();
        assertThat(blank.getProviderNo()).isEmpty();
    }

    private static Demographic patient(String provider) {
        Demographic patient = new Demographic();
        patient.setProviderNo(provider);
        return patient;
    }
}
