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
package io.github.carlos_emr.carlos.demographic.data;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A parsed date-of-birth search keyword, expressed as the three SQL {@code LIKE} values the
 * demographic DOB search binds against {@code year_of_birth}, {@code month_of_birth} and
 * {@code date_of_birth}.
 *
 * <p>Accepted keyword shapes (hyphen separated, surrounding whitespace ignored):</p>
 * <ul>
 *   <li>{@code YYYY} &mdash; any patient born that year ({@code 1975})</li>
 *   <li>{@code YYYY-MM} &mdash; that year and month ({@code 1975-03})</li>
 *   <li>{@code YYYY-MM-DD} &mdash; an exact date ({@code 1975-03-05})</li>
 * </ul>
 * <p>Any whole segment may instead be the {@code %} wildcard, e.g. {@code 1975-%-05} or
 * {@code %-03-05} (every March 5th birthday). Omitted trailing segments are wildcards, and a
 * single trailing hyphen left by the browser formatter ({@code 1975-}) is tolerated. A one-digit
 * month or day is zero-padded to match the two-character stored form.</p>
 *
 * <p>Deliberately rejected: partial segments ({@code 197}, {@code 197%}), the SQL single-character
 * wildcard {@code _}, anything non-numeric, out-of-range months/days, and keywords with no concrete
 * segment at all ({@code %}, {@code %-%-%}). The last rule stops a bare wildcard from listing every
 * patient in the clinic from a date-of-birth search.</p>
 *
 * <p>The returned values are bound as query parameters; this class never builds SQL text. The
 * browser formatter in {@code zdemographicfulltitlesearch.jsp} applies the same grammar as user
 * feedback only &mdash; this parser is authoritative.</p>
 *
 * @param year  {@code LIKE} value for the four-character year column, or {@code %}
 * @param month {@code LIKE} value for the two-character month column, or {@code %}
 * @param day   {@code LIKE} value for the two-character day column, or {@code %}
 * @since 2026-09-26
 */
public record DobSearchPattern(String year, String month, String day) {

    /** The SQL {@code LIKE} wildcard, accepted only as a whole segment. */
    public static final String WILDCARD = "%";

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");
    private static final Pattern MONTH_OR_DAY = Pattern.compile("\\d{1,2}");

    /**
     * Parses a DOB search keyword.
     *
     * @param keyword the raw search keyword; may be {@code null}
     * @return the parsed pattern, or empty when the keyword is not one of the accepted shapes
     */
    public static Optional<DobSearchPattern> parse(String keyword) {
        if (keyword == null) {
            return Optional.empty();
        }
        String trimmed = keyword.trim();
        // The formatter leaves "1975-" while the user is mid-entry; treat it as "1975".
        if (trimmed.endsWith("-")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        // limit -1 keeps empty segments so "1975--05" is rejected rather than collapsed.
        String[] segments = trimmed.split("-", -1);
        if (segments.length > 3) {
            return Optional.empty();
        }

        String year = segment(segments, 0);
        String month = segment(segments, 1);
        String day = segment(segments, 2);

        if (!WILDCARD.equals(year) && !YEAR_PATTERN.matcher(year).matches()) {
            return Optional.empty();
        }
        month = normalizeMonthOrDay(month, 12);
        day = normalizeMonthOrDay(day, 31);
        if (month == null || day == null) {
            return Optional.empty();
        }
        if (WILDCARD.equals(year) && WILDCARD.equals(month) && WILDCARD.equals(day)) {
            return Optional.empty();
        }
        return Optional.of(new DobSearchPattern(year, month, day));
    }

    private static String segment(String[] segments, int index) {
        return index < segments.length ? segments[index] : WILDCARD;
    }

    /** Returns the zero-padded segment, {@code %}, or {@code null} when the segment is invalid. */
    private static String normalizeMonthOrDay(String value, int max) {
        if (WILDCARD.equals(value)) {
            return WILDCARD;
        }
        if (!MONTH_OR_DAY.matcher(value).matches()) {
            return null;
        }
        int number = Integer.parseInt(value);
        if (number < 1 || number > max) {
            return null;
        }
        return number < 10 ? "0" + number : Integer.toString(number);
    }
}
