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
package io.github.carlos_emr.carlos.prescript.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("prescription")
@DisplayName("RxPrescriptionData logging helper behavior")
class RxPrescriptionDataUnitTest {

    @Test
    @DisplayName("should return zero when length input is null")
    void shouldReturnZero_whenLengthInputIsNull() {
        assertThat(RxPrescriptionData.safeLength(null)).isZero();
    }

    @Test
    @DisplayName("should return character count when length input is present")
    void shouldReturnCharacterCount_whenLengthInputIsPresent() {
        assertThat(RxPrescriptionData.safeLength("take daily")).isEqualTo(10);
    }

    @Test
    @DisplayName("should return empty string when full outline is null")
    void shouldReturnEmptyString_whenFullOutlineIsNull() {
        assertThat(RxPrescriptionData.textViewLineForFullOutline(null)).isEmpty();
    }

    @Test
    @DisplayName("should replace semicolons when full outline has separators")
    void shouldReplaceSemicolons_whenFullOutlineHasSeparators() {
        assertThat(RxPrescriptionData.textViewLineForFullOutline("one;two;three"))
                .isEqualTo("one\ntwo\nthree");
    }
}
