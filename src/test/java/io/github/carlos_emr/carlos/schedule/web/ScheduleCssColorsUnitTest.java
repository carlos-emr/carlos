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
package io.github.carlos_emr.carlos.schedule.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Schedule colours written into CSS")
class ScheduleCssColorsUnitTest {

    @ParameterizedTest
    @CsvSource({
            "EED2EE, #EED2EE",
            "FFF68F, #FFF68F",
            "FFFFEE, #FFFFEE",
            "abc, #abc",
            "'  fff68f ', #fff68f",
            "#486ebd, #486ebd",
            "#FFF, #FFF",
            "#11223344, #11223344"
    })
    @DisplayName("should give hex its # whether or not it was stored with one")
    void shouldReturnHashedHex_whenColourIsHex(String stored, String expected) {
        assertThat(ScheduleCssColors.safeCssColor(stored)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"white", "LightBlue", "red"})
    @DisplayName("should keep a plain colour name as it is")
    void shouldKeepName_whenColourIsAPlainName(String stored) {
        assertThat(ScheduleCssColors.safeCssColor(stored)).isEqualTo(stored);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "red;background:url(x)", "expression(alert(1))", "#12345", "rgb(1,2,3)",
            "red blue", "#GGGGGG", "\"red\""})
    @DisplayName("should refuse anything that is not hex or a plain colour name")
    void shouldRefuse_whenColourIsNotSafe(String stored) {
        assertThat(ScheduleCssColors.safeCssColor(stored)).isNull();
    }

    @Test
    @DisplayName("should return null when no colour is stored")
    void shouldReturnNull_whenColourIsMissing() {
        assertThat(ScheduleCssColors.safeCssColor(null)).isNull();
    }
}
