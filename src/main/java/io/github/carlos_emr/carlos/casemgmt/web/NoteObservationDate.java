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
package io.github.carlos_emr.carlos.casemgmt.web;

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.CachedDateFormats;

/**
 * The text form of a chart note's encounter (observation) date, as the eChart note editor shows it
 * in its {@code observation_date} field and posts it back to {@link CaseManagementEntry2Action}.
 *
 * <p><b>Why one fixed form.</b> The field round-trips through the browser: the server renders the
 * stored date into it, the clinician may change it with the calendar, and the save parses it again.
 * It used to be rendered with {@code <fmt:formatDate>}, which formats in the language the browser
 * asks for and, when that language has no date format, silently prints {@code Date.toString()} — a
 * {@code java.sql.Timestamp} such as {@code 2026-10-15 19:14:08.0}. The save only reads English
 * {@code dd-MMM-yyyy H:mm}, so editing a stored note and pressing Sign &amp; Save answered HTTP 500
 * (issue #4711), and a German browser broke the same way on {@code 15-Okt.-2026}. The field is now
 * always rendered in {@link #PATTERN} with English month abbreviations, whatever the browser or
 * server locale.</p>
 *
 * <p><b>What is read back.</b> {@link #parse} accepts that form, and also the month names of the
 * interface language, because the chart's calendar picker is localized and writes, for example,
 * {@code 15-févr-2026 08:00} in French. Month names are matched without case and without the
 * abbreviation period, since the calendar and the JDK disagree on it ({@code févr} vs {@code févr.}).
 * Anything else — including the leaked timestamp form, an impossible date such as 31 February, or
 * trailing text — is refused with a {@link ParseException} rather than guessed at: the date is part
 * of the clinical record, and the legacy lenient {@code SimpleDateFormat} parse silently turned
 * {@code 32-Oct} into 1 November.</p>
 *
 * <p><b>Clock changes.</b> The field is a wall-clock time in the server's time zone (the JVM
 * default; {@code CARLOS_TZ} on a packaged install). Where that zone observes daylight saving time,
 * one hour a year is skipped and one is repeated, and neither is refused (issue #4777). A time in
 * the skipped hour, such as 02:30 on the spring-forward night in Ontario, is moved forward by the
 * length of the gap and saved as 03:30: the lenient parse this replaces did the same, and refusing
 * it would send the clinician to the unreadable-date page for a time the field accepts. A time in
 * the repeated fall-back hour takes the earlier of its two offsets (daylight time), where the
 * legacy parse took the later one. That offset decides only the instant {@code parse} returns:
 * {@code casemgmt_note.observation_date} is a {@code DATETIME} the driver writes as wall-clock time
 * in the JVM zone, so it stores the 01:30 the clinician entered and does not record which
 * occurrence was meant. Only a comparison with the current time during that hour sees the
 * difference.</p>
 *
 * @since 2026-10-16
 */
public final class NoteObservationDate {

    /** Pattern the field is rendered in; month abbreviations are always English. */
    public static final String PATTERN = "dd-MMM-yyyy HH:mm";

    /** {@code d-MMM-yyyy H:mm} with any month word; spacing around and between the parts is tolerated. */
    private static final Pattern TEXT = Pattern.compile(
            "(\\d{1,2})-([^\\s\\d-][^\\s-]*)-(\\d{4})\\s+(\\d{1,2}):(\\d{2})");

    private NoteObservationDate() {
    }

    /**
     * Renders a stored observation date for the editor field.
     *
     * @param date the note's observation date; a Hibernate-loaded {@code java.sql.Timestamp} is fine
     * @return {@link #PATTERN} in English, or an empty string when {@code date} is {@code null}
     */
    public static String format(Date date) {
        return date == null ? "" : CachedDateFormats.format(date, PATTERN, Locale.ENGLISH);
    }

    /**
     * Reads the editor field back.
     *
     * @param text the posted field value; blank means the clinician left no date
     * @param interfaceLocale the language the chart was rendered in (its calendar's language), whose
     *                        month names are accepted besides English; {@code null} for English only
     * @return the date in the server's time zone (a skipped or repeated clock-change hour is
     *         resolved as the class documentation describes), or {@code null} when {@code text} is blank
     * @throws ParseException when the text is not a complete, real encounter date in that form
     */
    public static Date parse(String text, Locale interfaceLocale) throws ParseException {
        return parse(text, interfaceLocale, ZoneId.systemDefault());
    }

    /**
     * {@link #parse(String, Locale)} in a given time zone, so tests can pin the clock-change rules
     * without changing the JVM default zone.
     */
    static Date parse(String text, Locale interfaceLocale, ZoneId zone) throws ParseException {
        if (text == null || text.isBlank()) return null;
        Matcher matcher = TEXT.matcher(text.strip());
        if (!matcher.matches()) throw new ParseException("Unrecognised encounter date", 0);
        int month = monthNumber(matcher.group(2), interfaceLocale);
        if (month < 1) throw new ParseException("Unrecognised month in encounter date", matcher.start(2));
        try {
            LocalDateTime local = LocalDateTime.of(Integer.parseInt(matcher.group(3)), month,
                    Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(4)),
                    Integer.parseInt(matcher.group(5)));
            // atZone moves a skipped-hour time forward by the gap and gives a repeated-hour time
            // its earlier offset; both are kept on purpose (see the class documentation, #4777).
            return Date.from(local.atZone(zone).toInstant());
        } catch (DateTimeException impossible) {
            // 31-Feb, 24:00, 10:60 and the like: refuse rather than roll over.
            throw new ParseException("Impossible encounter date", 0);
        }
    }

    /** 1-12 for a month name of English or the interface language (short or full), else 0. */
    private static int monthNumber(String word, Locale interfaceLocale) {
        String wanted = normalizedMonth(word);
        Set<Locale> languages = new LinkedHashSet<>();
        languages.add(Locale.ENGLISH);
        if (interfaceLocale != null) {
            languages.add(interfaceLocale);
            // fr-CA abbreviates July "juill." where the calendar writes "juil" (the fr form).
            languages.add(Locale.of(interfaceLocale.getLanguage()));
        }
        for (Locale language : languages) {
            DateFormatSymbols symbols = DateFormatSymbols.getInstance(language);
            for (String[] names : new String[][]{symbols.getShortMonths(), symbols.getMonths()}) {
                // Twelve months; the arrays carry a thirteenth, empty, lunar-calendar slot.
                for (int i = 0; i < 12 && i < names.length; i++) {
                    if (!names[i].isEmpty() && normalizedMonth(names[i]).equals(wanted)) return i + 1;
                }
            }
        }
        return 0;
    }

    // FindSecBugs IMPROPER_UNICODE: case folding of a calendar month name for display-text matching against
    // the JDK's own month names; not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case folding of a calendar month name for display-text matching; not a security or authorization decision")
    private static String normalizedMonth(String name) {
        String folded = name.strip().toLowerCase(Locale.ROOT);
        return folded.endsWith(".") ? folded.substring(0, folded.length() - 1) : folded;
    }
}
