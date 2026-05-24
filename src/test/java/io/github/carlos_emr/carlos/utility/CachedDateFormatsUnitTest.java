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
    void shouldFormatEquivalentToSimpleDateFormat_forPattern() {
        for (String pattern : PATTERNS) {
            assertThat(CachedDateFormats.format(FIXED, pattern))
                    .as("pattern %s", pattern)
                    .isEqualTo(new SimpleDateFormat(pattern).format(FIXED));
        }
    }

    @Test
    @DisplayName("should format identically to new SimpleDateFormat(pattern, locale)")
    void shouldFormatEquivalentToSimpleDateFormat_withLocale() {
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.CANADA}) {
            String formatted = CachedDateFormats.format(FIXED, "dd-MMM-yyyy", locale);
            assertThat(formatted)
                    .as("locale %s", locale)
                    .isEqualTo(new SimpleDateFormat("dd-MMM-yyyy", locale).format(FIXED));
        }
    }

    @Test
    @DisplayName("should thread the locale through the cache for a distinct-rendering locale")
    void shouldApplyLocale_forDistinctRenderingLocale() {
        // January renders differently in French ("janv.") vs English ("Jan"), so this proves
        // the locale is part of the cache key and is actually applied — not silently dropped.
        String french = CachedDateFormats.format(FIXED, "dd-MMM-yyyy", Locale.FRENCH);
        String english = CachedDateFormats.format(FIXED, "dd-MMM-yyyy", Locale.ENGLISH);
        assertThat(french).isEqualTo(new SimpleDateFormat("dd-MMM-yyyy", Locale.FRENCH).format(FIXED));
        assertThat(french).isNotEqualTo(english);
    }

    @Test
    @DisplayName("should parse with the supplied locale like SimpleDateFormat(pattern, locale)")
    void shouldParseWithLocale_forLocaleSpecificMonth() throws Exception {
        String french = new SimpleDateFormat("dd-MMM-yyyy", Locale.FRENCH).format(FIXED);
        Date expected = new SimpleDateFormat("dd-MMM-yyyy", Locale.FRENCH).parse(french);
        assertThat(CachedDateFormats.parse(french, "dd-MMM-yyyy", Locale.FRENCH)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should format identically to the no-arg SimpleDateFormat()")
    void shouldFormatEquivalentToNoArgSimpleDateFormat_forDefaultFormat() {
        assertThat(CachedDateFormats.formatDefault(FIXED))
                .isEqualTo(new SimpleDateFormat().format(FIXED));
    }

    @Test
    @DisplayName("should parse identically to the no-arg SimpleDateFormat()")
    void shouldParseEquivalentToNoArgSimpleDateFormat_forDefaultParse() throws Exception {
        String canonical = new SimpleDateFormat().format(FIXED);
        assertThat(CachedDateFormats.parseDefault(canonical))
                .isEqualTo(new SimpleDateFormat().parse(canonical));
    }

    @Test
    @DisplayName("should cache one formatter per distinct (pattern, locale) key including the no-arg key")
    void shouldCacheOneFormatterPerKey_includingNoArgKey() throws Exception {
        // Run on a fresh thread so the cache count starts at zero and is independent of any other
        // test that ran earlier on the shared JUnit thread.
        int[] sizes = onFreshThread(() -> {
            int empty = CachedDateFormats.cachedFormatterCount();
            CachedDateFormats.format(FIXED, "dd-MMM-yyyy");                 // key: pattern, default locale
            CachedDateFormats.format(FIXED, "dd-MMM-yyyy");                 // same key, must be reused
            int afterRepeat = CachedDateFormats.cachedFormatterCount();
            CachedDateFormats.format(FIXED, "dd-MMM-yyyy", Locale.FRENCH);  // key: pattern + fr
            CachedDateFormats.format(FIXED, "dd-MMM-yyyy", Locale.ENGLISH); // key: pattern + en
            CachedDateFormats.formatDefault(FIXED);                         // reserved no-arg key
            return new int[]{empty, afterRepeat, CachedDateFormats.cachedFormatterCount()};
        });
        assertThat(sizes[0]).as("a fresh thread starts with an empty cache").isZero();
        assertThat(sizes[1]).as("a repeated pattern reuses its cached formatter").isEqualTo(1);
        // pattern, pattern+fr, pattern+en, and the no-arg sentinel are four distinct, non-colliding keys
        assertThat(sizes[2]).as("each distinct key caches its own formatter").isEqualTo(4);
    }

    @Test
    @DisplayName("should give each thread its own formatter cache")
    void shouldIsolateCache_acrossThreads() throws Exception {
        // Populate this thread's cache, then prove a different thread sees an independent (empty)
        // cache — i.e. the formatters are thread-confined and never shared across threads.
        CachedDateFormats.format(FIXED, "yyyy-MM-dd");
        assertThat(CachedDateFormats.cachedFormatterCount()).isGreaterThanOrEqualTo(1);

        int otherThreadCount = onFreshThread(CachedDateFormats::cachedFormatterCount);
        assertThat(otherThreadCount).as("a fresh thread shares none of this thread's formatters").isZero();
    }

    @Test
    @DisplayName("should stop caching past the per-thread cap but still format correctly")
    void shouldCapPerThreadCache_andStillFormatCorrectly() throws Exception {
        // MAX_CACHED_PATTERNS_PER_THREAD (256) is a defensive backstop against a caller that
        // violates the "no request-controlled patterns" contract: past the bound the per-thread
        // map must stop growing, yet callers must still receive a correct, thread-confined formatter.
        Object[] result = onFreshThread(() -> {
            // Feed well past the cap with distinct, valid patterns. A quoted literal prefix keeps
            // each pattern (and therefore each cache key) unique without changing the parse/format
            // semantics of the trailing yyyy field.
            for (int i = 0; i < 300; i++) {
                CachedDateFormats.format(FIXED, "'P" + i + "'yyyy");
            }
            int count = CachedDateFormats.cachedFormatterCount();
            // "P299" was the 300th distinct pattern — first seen well past the cap, so it was never
            // cached. The formatter returned for it must still be correct all the same.
            String beyondCap = CachedDateFormats.format(FIXED, "'P299'yyyy");
            return new Object[]{count, beyondCap};
        });
        // Exactly the cap: patterns P0..P255 were cached, P256..P299 were not, and this thread never
        // triggered the on-demand no-arg sentinel, so nothing else inflates the count.
        assertThat((int) result[0])
                .as("per-thread cache stops growing at MAX_CACHED_PATTERNS_PER_THREAD")
                .isEqualTo(256);
        assertThat((String) result[1])
                .as("a beyond-cap (uncached) pattern still formats identically to new SimpleDateFormat(pattern)")
                .isEqualTo(new SimpleDateFormat("'P299'yyyy").format(FIXED));
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
    @DisplayName("should parse leniently when the input has trailing text")
    void shouldParseLeniently_forTrailingText() throws Exception {
        Date expected = new SimpleDateFormat("yyyy-MM-dd").parse("2024-03-05xyz");
        assertThat(CachedDateFormats.parse("2024-03-05xyz", "yyyy-MM-dd")).isEqualTo(expected);
    }

    @Test
    @DisplayName("should roll over out-of-range fields like SimpleDateFormat")
    void shouldRollOverOutOfRangeFields_forLenientParse() throws Exception {
        Date expected = new SimpleDateFormat("yyyy-MM-dd").parse("2024-13-40");
        assertThat(CachedDateFormats.parse("2024-13-40", "yyyy-MM-dd")).isEqualTo(expected);
    }

    @Test
    @DisplayName("should apply the supplied time zone when formatting")
    void shouldApplyTimeZone_forFormatWithZone() {
        // FIXED is 2024-01-01T00:00:00Z; Pacific/Kiritimati is UTC+14, so the wall clock is 14:00.
        String utc = CachedDateFormats.format(FIXED, "yyyy-MM-dd HH", TimeZone.getTimeZone("UTC"));
        String kiri = CachedDateFormats.format(FIXED, "yyyy-MM-dd HH", TimeZone.getTimeZone("Pacific/Kiritimati"));
        assertThat(utc).isEqualTo("2024-01-01 00");
        assertThat(kiri).isEqualTo("2024-01-01 14");
    }

    @Test
    @DisplayName("should restore the formatter time zone after a zoned format (no leak to later plain formats)")
    void shouldRestoreTimeZone_afterFormatWithZone() {
        String pattern = "yyyy-MM-dd HH";
        String plainBefore = CachedDateFormats.format(FIXED, pattern);
        // A zoned format must not leave the cached formatter stuck on that zone, so a subsequent
        // plain format of the same pattern must render exactly as it did before.
        CachedDateFormats.format(FIXED, pattern, TimeZone.getTimeZone("Pacific/Kiritimati")); // UTC+14
        String plainAfter = CachedDateFormats.format(FIXED, pattern);
        assertThat(plainAfter)
                .as("plain format output is identical before and after a zoned format")
                .isEqualTo(plainBefore);
    }

    @Test
    @DisplayName("should not let a zoned format of one pattern affect another pattern's output")
    void shouldNotContaminateOtherPatterns_afterZonedFormat() {
        String otherBefore = CachedDateFormats.format(FIXED, "yyyy-MM-dd");
        CachedDateFormats.format(FIXED, "yyyy-MM-dd HH:mm:ss z", TimeZone.getTimeZone("Pacific/Kiritimati"));
        String otherAfter = CachedDateFormats.format(FIXED, "yyyy-MM-dd");
        assertThat(otherAfter).isEqualTo(otherBefore);
    }

    /** Runs {@code task} on a brand-new thread (which therefore sees an empty per-thread cache) and returns its result. */
    private static <T> T onFreshThread(Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(task).get();
        } finally {
            executor.shutdownNow();
        }
    }
}
