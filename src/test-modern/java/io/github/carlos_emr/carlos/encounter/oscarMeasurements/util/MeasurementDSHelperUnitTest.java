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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MeasurementDSHelper} null-safety behavior when no
 * measurement data bean ({@code mdb}) is loaded.
 *
 * <p>The no-arg constructor leaves {@code mdb = null}, which represents the
 * scenario where a patient has no measurements of the requested type. All
 * public methods called by DRL rules must handle this gracefully without
 * throwing exceptions, since rules may fire against patients with incomplete
 * measurement histories.</p>
 *
 * @see MeasurementDSHelper
 * @since 2026-02-18
 */
@Tag("unit")
@Tag("drools")
@DisplayName("MeasurementDSHelper")
class MeasurementDSHelperUnitTest {

    @Nested
    @DisplayName("null mdb guards")
    class NullMdbGuards {

        @Test
        @DisplayName("should not throw when setIndicationColor called with null mdb")
        void shouldNotThrow_whenSetIndicationColorCalledWithNullMdb() {
            MeasurementDSHelper helper = new MeasurementDSHelper();

            assertThatCode(() -> helper.setIndicationColor("RED"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should return -1 for getDataAsDouble when mdb is null")
        void shouldReturnNegativeOne_forGetDataAsDoubleWhenMdbIsNull() {
            MeasurementDSHelper helper = new MeasurementDSHelper();

            assertThat(helper.getDataAsDouble()).isEqualTo(-1.0);
        }

        @Test
        @DisplayName("should return false for isDataEqualTo when mdb is null")
        void shouldReturnFalse_forIsDataEqualToWhenMdbIsNull() {
            MeasurementDSHelper helper = new MeasurementDSHelper();

            assertThat(helper.isDataEqualTo("anything")).isFalse();
        }

        @Test
        @DisplayName("should return -1 for getNumberFromSplit when mdb is null")
        void shouldReturnNegativeOne_forGetNumberFromSplitWhenMdbIsNull() {
            MeasurementDSHelper helper = new MeasurementDSHelper();

            assertThat(helper.getNumberFromSplit("/", 0)).isEqualTo(-1.0);
        }

        @Test
        @DisplayName("should return null for getDateObserved when mdb is null")
        void shouldReturnNull_forGetDateObservedWhenMdbIsNull() {
            MeasurementDSHelper helper = new MeasurementDSHelper();

            assertThat(helper.getDateObserved()).isNull();
        }

        @Test
        @DisplayName("should throw IllegalStateException for getLastDateRecordedInMths when mdb is null")
        void shouldThrowException_forGetLastDateRecordedInMthsWhenMdbIsNull() {
            MeasurementDSHelper helper = new MeasurementDSHelper();

            assertThatThrownBy(helper::getLastDateRecordedInMths)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("setMeasurement()");
        }
    }

    @Nested
    @DisplayName("default state")
    class DefaultState {

        @Test
        @DisplayName("should return false for isInRange by default")
        void shouldReturnFalse_forIsInRangeByDefault() {
            MeasurementDSHelper helper = new MeasurementDSHelper();

            assertThat(helper.isInRange()).isFalse();
        }

        @Test
        @DisplayName("should return false for hasProblem by default")
        void shouldReturnFalse_forHasProblemByDefault() {
            MeasurementDSHelper helper = new MeasurementDSHelper();

            assertThat(helper.hasProblem()).isFalse();
        }

        @Test
        @DisplayName("should update inRange via setter")
        void shouldUpdateInRange_viaSetter() {
            MeasurementDSHelper helper = new MeasurementDSHelper();

            helper.setInRange(true);

            assertThat(helper.isInRange()).isTrue();
        }
    }
}
