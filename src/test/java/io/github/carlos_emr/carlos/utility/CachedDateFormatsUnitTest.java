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
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CachedDateFormats}.
 *
 * <p>The contract is byte-for-byte equivalence with directly constructed
 * {@link SimpleDateFormat} instances (same lenient parse behaviour, same format
 * output), with per-thread caching so no allocation happens per request and no
 * mutable state is shared across threads.</p>
 *
 * @since 2026-05-23
 */
@Tag("unit")
@DisplayName("CachedDateFormats")
class CachedDateFormatsUnitTest {

    /** Patterns exercised across the CARLOS date call sites being migrated. */
    private static final String[] PATTERNS = {
            "dd/MM/yyyy", "dd-MMM-yyyy", "dd-MMM-yyyy H:mm", "yyyy-MM-dd", "yyyyMMdd", "yyyy"
    };

    private static final Date FIXED = new Date(1704067200000L); // 2024-01-01T00:00:00Z

    @Test
    @DisplayName("should format identically to new SimpleDateFormat(pattern)")
    void shouldReturnFormatEquivalent_toNewSimpleDateFormat_forPattern() {
        for (String pattern : PATTERNS) {
            assertThat(CachedDateFormats.format(FIXED, pattern))
                    .as("pattern %s", pattern)
                    .isEqualTo(new SimpleDateFormat(pattern).format(FIXED));
        }
    }

    @Test
    @DisplayName("should format identically to new SimpleDateFormat(pattern, locale)")
    void shouldReturnFormatEquivalent_toNewSimpleDateFormat_withLocale() {
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.CANADA}) {
            String formatted = CachedDateFormats.format(FIXED, "dd-MMM-yyyy", locale);
            assertThat(formatted)
                    .as("locale %s", locale)
                    .isEqualTo(new SimpleDateFormat("dd-MMM-yyyy", locale).format(FIXED));
        }
    }

    @Test
    @DisplayName("should reproduce the no-arg SimpleDateFormat() output")
    void shouldReproduceNoArgInstance_forDefaultInstance() {
        assertThat(CachedDateFormats.defaultInstance().format(FIXED))
                .isEqualTo(new SimpleDateFormat().format(FIXED));
    }

    @Test
    @DisplayName("should return the same cached instance for repeated pattern on one thread")
    void shouldReturnSameInstancePerThread_forRepeatedPattern() {
        SimpleDateFormat first = CachedDateFormats.forPattern("yyyy-MM-dd");
        SimpleDateFormat second = CachedDateFormats.forPattern("yyyy-MM-dd");
        assertThat(second).isSameAs(first);
    }

    @Test
    @DisplayName("should give each thread its own instance for the same pattern")
    void shouldIsolateInstances_acrossThreads() throws Exception {
        SimpleDateFormat mainInstance = CachedDateFormats.forPattern("yyyy-MM-dd");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<SimpleDateFormat> other =
                    executor.submit((Callable<SimpleDateFormat>) () -> CachedDateFormats.forPattern("yyyy-MM-dd"));
            assertThat(other.get()).isNotSameAs(mainInstance);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("should round-trip parse then format for representative patterns")
    void shouldRoundTripParseThenFormat_forRepresentativePatterns() throws Exception {
        for (String pattern : PATTERNS) {
            String canonical = new SimpleDateFormat(pattern).format(FIXED);
            Date parsed = CachedDateFormats.parse(canonical, pattern);
            assertThat(CachedDateFormats.format(parsed, pattern))
                    .as("pattern %s", pattern)
                    .isEqualTo(canonical);
        }
    }

    @Test
    @DisplayName("should parse leniently for unpadded fields like SimpleDateFormat")
    void shouldParseLeniently_forUnpaddedFields() throws Exception {
        Date expected = new SimpleDateFormat("yyyy-MM-dd").parse("2024-3-5");
        assertThat(CachedDateFormats.parse("2024-3-5", "yyyy-MM-dd")).isEqualTo(expected);
    }

    @Test
    @DisplayName("should default missing month/day for a year-only pattern")
    void shouldDefaultMissingFields_forYearOnlyPattern() throws Exception {
        Date expected = new SimpleDateFormat("yyyy").parse("2024");
        assertThat(CachedDateFormats.parse("2024", "yyyy")).isEqualTo(expected);
    }

    @Test
    @DisplayName("should not contaminate other patterns when one instance mutates its time zone")
    void shouldNotContaminate_acrossPatternsAfterSetTimeZone() {
        SimpleDateFormat dateOnly = CachedDateFormats.forPattern("yyyy-MM-dd");
        TimeZone originalZone = dateOnly.getTimeZone();

        SimpleDateFormat zoned = CachedDateFormats.forPattern("EEE MMM dd HH:mm:ss z yyyy");
        zoned.setTimeZone(TimeZone.getTimeZone("Pacific/Kiritimati")); // UTC+14, unlikely default

        assertThat(dateOnly.getTimeZone()).isEqualTo(originalZone);
        assertThat(zoned).isNotSameAs(dateOnly);
    }
}
