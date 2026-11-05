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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The legacy CVCMedication.din column is INT, so DINs lose their leading zeros on the way back
 * from the database; the getter restores the canonical 8-digit form.
 */
@Tag("unit")
@Tag("fast")
@Tag("prevention")
class CVCMedicationDinUnitTest {

    @ParameterizedTest
    @CsvSource({
            "2541866, 02541866",
            "02541866, 02541866",
            "12345678, 12345678",
            "A123, A123",
    })
    void shouldReturnCanonicalDin_forStoredValue(String stored, String expected) {
        CVCMedication medication = new CVCMedication();
        medication.setDin(stored);

        assertThat(medication.getDin()).isEqualTo(expected);
    }

    @Test
    void shouldReturnNull_whenNoDinStored() {
        assertThat(new CVCMedication().getDin()).isNull();
    }
}
