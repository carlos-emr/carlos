/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DateUtils#format(String, Date, Locale)} after its migration to
 * {@link CachedDateFormats}. Pins the null-locale branch (default-locale fallback) and the
 * null-date short-circuit against {@code new SimpleDateFormat(...)} so neither silently changes.
 *
 * @since 2026-05-23
 */
@Tag("unit")
@DisplayName("DateUtils.format(pattern, date, locale)")
class DateUtilsFormatUnitTest {

    private static final Date FIXED = new Date(1704067200000L); // 2024-01-01T00:00:00Z

    @Test
    @DisplayName("should format with the default locale when locale is null")
    void shouldFormatWithDefaultLocale_whenLocaleNull() {
        assertThat(DateUtils.format("yyyy-MM-dd", FIXED, null))
                .isEqualTo(new SimpleDateFormat("yyyy-MM-dd").format(FIXED));
    }

    @Test
    @DisplayName("should format with the supplied locale when one is provided")
    void shouldFormatWithLocale_whenLocaleProvided() {
        assertThat(DateUtils.format("dd-MMM-yyyy", FIXED, Locale.FRENCH))
                .isEqualTo(new SimpleDateFormat("dd-MMM-yyyy", Locale.FRENCH).format(FIXED));
    }

    @Test
    @DisplayName("should return an empty string for a null date")
    void shouldReturnEmptyString_forNullDate() {
        assertThat(DateUtils.format("yyyy-MM-dd", null, null)).isEmpty();
    }
}
