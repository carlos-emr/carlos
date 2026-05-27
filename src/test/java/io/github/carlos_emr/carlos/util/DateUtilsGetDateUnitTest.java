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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Lock-in tests for {@link DateUtils#getDate(Date, String, Locale)}.
 *
 * <p>The date-allocation refactor changed the {@code null} locale path: the legacy code did
 * {@code new SimpleDateFormat(format, locale)} unconditionally, which threw {@link NullPointerException}
 * for a {@code null} locale. The new code falls back to the default-locale formatter instead.
 * These tests pin that fallback plus the explicit-locale and {@code null}-date contracts so the
 * behaviour cannot drift again silently.</p>
 *
 * @since 2026-05-23
 */
@Tag("unit")
@DisplayName("DateUtils.getDate(Date, format, Locale)")
class DateUtilsGetDateUnitTest {

    private static final Date FIXED = new Date(1704067200000L); // 2024-01-01T00:00:00Z

    @Test
    @DisplayName("should fall back to the default locale when locale is null")
    void shouldFormatWithDefaultLocale_whenLocaleNull() {
        assertThat(DateUtils.getDate(FIXED, "dd-MMM-yyyy", null))
                .isEqualTo(new SimpleDateFormat("dd-MMM-yyyy").format(FIXED));
    }

    @Test
    @DisplayName("should format with the supplied locale when one is provided")
    void shouldFormatWithSuppliedLocale_whenLocaleProvided() {
        assertThat(DateUtils.getDate(FIXED, "dd-MMM-yyyy", Locale.FRENCH))
                .isEqualTo(new SimpleDateFormat("dd-MMM-yyyy", Locale.FRENCH).format(FIXED));
    }

    @Test
    @DisplayName("should return an empty string for a null date")
    void shouldReturnEmpty_forNullDate() {
        assertThat(DateUtils.getDate(null, "dd-MMM-yyyy", Locale.CANADA)).isEmpty();
    }
}
