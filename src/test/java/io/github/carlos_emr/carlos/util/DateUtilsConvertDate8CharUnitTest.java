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
package io.github.carlos_emr.carlos.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Lock-in tests for {@link DateUtils#convertDate8Char(String)}.
 *
 * <p>The legacy implementation used a lenient {@code SimpleDateFormat("yyyy-MM-dd")}, so
 * un-padded inputs such as {@code 2024-3-5} parsed successfully. These tests pin that
 * behaviour so the date-allocation refactor cannot silently make parsing stricter.</p>
 *
 * @since 2026-05-23
 */
@Tag("unit")
@DisplayName("DateUtils.convertDate8Char")
class DateUtilsConvertDate8CharUnitTest {

    @Test
    @DisplayName("should convert a zero-padded ISO date to 8 chars")
    void shouldConvert_forIsoDateString() {
        assertThat(DateUtils.convertDate8Char("2024-03-05")).isEqualTo("20240305");
    }

    @Test
    @DisplayName("should convert leniently for an un-padded ISO date")
    void shouldConvertLeniently_forUnpaddedIsoDate() {
        assertThat(DateUtils.convertDate8Char("2024-3-5")).isEqualTo("20240305");
    }

    @Test
    @DisplayName("should return the zero sentinel for null input")
    void shouldReturnZeroes_forNullInput() {
        assertThat(DateUtils.convertDate8Char(null)).isEqualTo("00000000");
    }

    @Test
    @DisplayName("should return the zero sentinel for unparseable input")
    void shouldReturnZeroes_forUnparseableInput() {
        assertThat(DateUtils.convertDate8Char("not-a-date")).isEqualTo("00000000");
    }
}
