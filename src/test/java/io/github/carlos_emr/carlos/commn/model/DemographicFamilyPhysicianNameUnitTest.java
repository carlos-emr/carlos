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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the null-safety of the transient {@code familyPhysician} parse getters.
 *
 * <p>These getters feed the raw {@code family_physician} column into a regex {@code Matcher}.
 * The column is frequently null, which previously threw an NPE and surfaced as a 500 when
 * Jackson serialized a Demographic over the REST API (issue #2957).</p>
 *
 * @since 2026-06-22
 */
@DisplayName("Demographic family physician name parsing")
@Tag("unit")
@Tag("demographic")
class DemographicFamilyPhysicianNameUnitTest {

    @Test
    @DisplayName("should return empty strings without throwing when familyPhysician is null")
    void shouldReturnEmpty_whenFamilyPhysicianIsNull() {
        Demographic demographic = new Demographic();
        // familyPhysician left null

        assertThatCode(() -> {
            assertThat(demographic.getFamilyPhysicianLastName()).isEmpty();
            assertThat(demographic.getFamilyPhysicianFirstName()).isEmpty();
            assertThat(demographic.getFamilyPhysicianFullName()).isEmpty();
            assertThat(demographic.getFamilyPhysicianNumber()).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should parse last and first name when familyPhysician is populated")
    void shouldParseNames_whenFamilyPhysicianPopulated() {
        Demographic demographic = new Demographic();
        demographic.setFamilyPhysician("<rdohip>1234</rdohip><rd>Smith, John</rd>");

        assertThat(demographic.getFamilyPhysicianLastName()).isEqualTo("Smith");
        assertThat(demographic.getFamilyPhysicianFirstName()).isEqualTo("John");
        assertThat(demographic.getFamilyPhysicianNumber()).isEqualTo("1234");
    }
}
