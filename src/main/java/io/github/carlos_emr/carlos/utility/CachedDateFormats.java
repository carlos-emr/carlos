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
package io.github.carlos_emr.carlos.utility;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Thread-local cache of {@link SimpleDateFormat} instances, keyed by pattern (and
 * optional {@link Locale}).
 *
 * <p>{@code SimpleDateFormat} is not thread-safe and constructing one is comparatively
 * expensive, so legacy CARLOS code allocated a fresh formatter on every request. This
 * helper reuses one formatter per (pattern, locale) per thread, so callers get the same
 * lenient parsing and format output as {@code new SimpleDateFormat(...)} with no
 * per-request allocation and no cross-thread sharing of mutable state.</p>
 *
 * <p>The public surface ({@link #parse}, {@link #format}, {@link #parseDefault},
 * {@link #formatDefault}) uses each cached formatter within a single call and never returns
 * it, so callers cannot leave it in a mutated state. When a specific {@link TimeZone} is
 * needed, use {@link #format(Date, String, TimeZone)}, which applies the zone for one format
 * and then restores the formatter so the cached instance is never left mutated for the next
 * caller of the same pattern on the same thread. The shared per-thread formatters themselves
 * ({@link #defaultInstance}, {@link #forPattern}) are private precisely so no other class can
 * obtain and mutate them.</p>
 *
 * <p><strong>Cache lifetime:</strong> entries are held per thread for the life of that thread
 * (e.g. pooled Tomcat workers) and are never evicted. That is bounded and intended for the
 * fixed set of literal patterns used across CARLOS. Callers must not pass attacker- or
 * request-controlled <em>pattern</em> strings, which would grow the per-thread map without
 * bound; request-derived <em>locales</em> are fine (small, fixed cardinality). As a defensive
 * backstop against a caller that ignores that contract, caching stops past
 * {@value #MAX_CACHED_PATTERNS_PER_THREAD} distinct patterns per thread (see {@link #forPattern}).</p>
 *
 * <p><strong>Why {@code SimpleDateFormat} and not {@code java.time}:</strong> a
 * {@link java.time.format.DateTimeFormatter} is immutable and thread-safe and would make this
 * cache unnecessary. CARLOS keeps {@code SimpleDateFormat} here deliberately, to preserve the
 * exact lenient-parsing and legacy format behaviour of the migrated call sites (out-of-range
 * field roll-over, trailing-text tolerance, and the no-arg SHORT/SHORT default) that
 * {@code DateTimeFormatter} does not reproduce without extra work. Prefer {@code java.time} for
 * <em>new</em> code that does not rely on that legacy leniency.</p>
 *
 * @since 2026-05-23
 */
public final class CachedDateFormats {

    private CachedDateFormats() {
    }

    /**
     * Separator between the pattern and the locale language tag in a cache key. A space is
     * collision-safe here: a {@link Locale#toLanguageTag()} value is BCP-47 (letters, digits,
     * and hyphens) and never contains a space, so the tag is always the space-free suffix of the
     * key. That keeps {@code pattern + separator + tag} unambiguous even when the pattern itself
     * contains spaces, and a null-locale key (empty tag, so a trailing space) can never equal a
     * non-null-locale key.
     */
    private static final char KEY_SEPARATOR = ' ';

    /**
     * Cache key reserved for the no-arg {@link SimpleDateFormat} (SHORT/SHORT, default locale).
     * It is the {@link #KEY_SEPARATOR} space followed by {@code __noarg__}. No real
     * {@code pattern + separator + tag} key can equal it: the space-free suffix of any real key is
     * its {@link Locale#toLanguageTag()} value, which is BCP-47 (letters, digits, hyphens) and so
     * can never be {@code __noarg__} — an underscore is not a BCP-47 character. The pattern itself
     * is irrelevant to this guarantee (it may even be empty), since the language-tag suffix alone
     * already rules out a collision.
     */
    private static final String NO_ARG_KEY = KEY_SEPARATOR + "__noarg__";

    /**
     * Upper bound on distinct formatters retained per thread. Real CARLOS usage is a small fixed
     * set of literal patterns, so this is only a safety valve: a caller that violates the
     * "no request-controlled patterns" contract cannot grow a worker thread's map without limit.
     * Above the bound callers still receive a correct, thread-confined formatter; it is simply
     * not cached.
     */
    private static final int MAX_CACHED_PATTERNS_PER_THREAD = 256;

    private static final ThreadLocal<Map<String, SimpleDateFormat>> CACHE =
            ThreadLocal.withInitial(HashMap::new);

    /**
     * Returns a thread-confined {@link SimpleDateFormat} for {@code pattern} using the
     * JVM default {@code FORMAT} locale, equivalent to {@code new SimpleDateFormat(pattern)}.
     */
    // Private: the raw mutable formatter is never exposed outside this class. Callers use it
    // within a single call (see parse/format/defaultInstance) and must not leave it mutated.
    private static SimpleDateFormat forPattern(String pattern) {
        return forPattern(pattern, null);
    }

    /**
     * Returns a thread-confined {@link SimpleDateFormat} for {@code pattern} and
     * {@code locale}, equivalent to {@code new SimpleDateFormat(pattern, locale)}.
     * A null {@code locale} uses the JVM default {@code FORMAT} locale.
     */
    private static SimpleDateFormat forPattern(String pattern, Locale locale) {
        String key = pattern + KEY_SEPARATOR + (locale == null ? "" : locale.toLanguageTag());
        Map<String, SimpleDateFormat> cache = CACHE.get();
        SimpleDateFormat cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        SimpleDateFormat created = locale == null
                ? new SimpleDateFormat(pattern)
                : new SimpleDateFormat(pattern, locale);
        // Safety valve (see MAX_CACHED_PATTERNS_PER_THREAD): only the fixed set of literal CARLOS
        // patterns should ever reach here. Stop retaining past the bound so a contract-violating
        // caller cannot grow this thread's map without limit; output is unaffected either way.
        if (cache.size() < MAX_CACHED_PATTERNS_PER_THREAD) {
            cache.put(key, created);
        }
        return created;
    }

    /**
     * Returns the thread-confined no-arg {@link SimpleDateFormat}, exactly
     * {@code new SimpleDateFormat()} (SHORT date + SHORT time in the default {@code FORMAT}
     * locale). Private: like {@link #forPattern}, the shared mutable instance is not
     * exposed to callers; use {@link #parseDefault}/{@link #formatDefault} instead.
     */
    private static SimpleDateFormat defaultInstance() {
        return CACHE.get().computeIfAbsent(NO_ARG_KEY, k -> new SimpleDateFormat());
    }

    /**
     * Number of formatters cached on the current thread. Visible for testing only: it lets unit
     * tests assert per-thread caching and cross-thread isolation without exposing (and thereby
     * risking mutation of) a cached {@link SimpleDateFormat}.
     */
    static int cachedFormatterCount() {
        return CACHE.get().size();
    }

    /**
     * Parses {@code text} with the no-arg {@link SimpleDateFormat} (SHORT date + SHORT time,
     * default {@code FORMAT} locale), mirroring the legacy {@code new SimpleDateFormat().parse(text)}.
     */
    public static Date parseDefault(String text) throws ParseException {
        return defaultInstance().parse(text);
    }

    /**
     * Formats {@code date} with the no-arg {@link SimpleDateFormat} (SHORT date + SHORT time,
     * default {@code FORMAT} locale), mirroring the legacy {@code new SimpleDateFormat().format(date)}.
     */
    public static String formatDefault(Date date) {
        return defaultInstance().format(date);
    }

    /** Parses {@code text} with {@code pattern} (default locale), mirroring {@code SimpleDateFormat.parse}. */
    public static Date parse(String text, String pattern) throws ParseException {
        return forPattern(pattern).parse(text);
    }

    /** Parses {@code text} with {@code pattern} and {@code locale}. */
    public static Date parse(String text, String pattern, Locale locale) throws ParseException {
        return forPattern(pattern, locale).parse(text);
    }

    /** Formats {@code date} with {@code pattern} (default locale). */
    public static String format(Date date, String pattern) {
        return forPattern(pattern).format(date);
    }

    /** Formats {@code date} with {@code pattern} and {@code locale}. */
    public static String format(Date date, String pattern, Locale locale) {
        return forPattern(pattern, locale).format(date);
    }

    /**
     * Formats {@code date} with {@code pattern} in the given {@code zone}, then restores the
     * cached formatter's previous time zone so the next caller of the same pattern on this
     * thread is unaffected. Mirrors the legacy {@code new SimpleDateFormat(pattern)} +
     * {@code setTimeZone(zone)} + {@code format(date)} sequence without leaking the zone.
     */
    public static String format(Date date, String pattern, TimeZone zone) {
        SimpleDateFormat sdf = forPattern(pattern);
        TimeZone original = sdf.getTimeZone();
        try {
            sdf.setTimeZone(zone);
            return sdf.format(date);
        } finally {
            sdf.setTimeZone(original);
        }
    }
}
