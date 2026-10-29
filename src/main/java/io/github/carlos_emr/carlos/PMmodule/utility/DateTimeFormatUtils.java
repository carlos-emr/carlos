/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.PMmodule.utility;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.format.ResolverStyle;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Legacy date and time text for the Population Report and new demographic records.
 *
 * <p>Dates and times are the clinic's: they are read and written in the JVM default zone, which
 * the packaged service sets to the clinic's zone, as it stands when the method is called. The
 * formatters are immutable {@link DateTimeFormatter}s and safe to share between concurrent
 * requests; the shared {@code SimpleDateFormat}s they replaced returned one request's date to
 * another (issue #5301).</p>
 *
 * @deprecated 2013-12-09 use io.github.carlos_emr.carlos.util.DateUtils instead
 */
@Deprecated
public final class DateTimeFormatUtils {

    private static final Logger log = MiscUtils.getLogger();

    /** The form every date is written in: {@code yyyy-MM-dd}. */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT);

    /**
     * Reads a date with a four-digit year and a one- or two-digit month and day, as the legacy
     * parser did. Unlike that lenient parser it refuses a date that does not exist, such as
     * 30 February, rather than reading it as a date in the following month, and refuses trailing
     * text rather than ignoring it.
     */
    private static final DateTimeFormatter DATE_PARSER = DateTimeFormatter.ofPattern("uuuu-M-d", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DATE_TEXT = Pattern.compile("[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}");

    /**
     * The short Canadian time, for example {@code 8:05 p.m.}: the same localized pattern the
     * legacy {@code DateFormat.getTimeInstance(SHORT, Locale.CANADA)} used. Parsing ignores case
     * and treats the space separators alike, as that lenient parser did.
     */
    private static final DateTimeFormatter TIME_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .parseLenient()
            .appendLocalized(null, FormatStyle.SHORT)
            .toFormatter(Locale.CANADA);

    // Public methods

    // Timestamp

    public static final Timestamp getPast(int numYears) {
        return getPast(Calendar.getInstance(), numYears);
    }

    public static final Timestamp getPast(Calendar calendar, int numYears) {
        calendar.add(Calendar.YEAR, -numYears);

        return new Timestamp(calendar.getTimeInMillis());
    }

    // Dates

    public static final Date getFuture(Date start, Integer daysInFuture) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, daysInFuture);
        Date future = calendar.getTime();

        return getDateFromDate(future);
    }

    /**
     * Writes the clinic's calendar date of {@code date} as {@code yyyy-MM-dd}.
     *
     * @param date Date the instant to write; may be {@code null}
     * @return String the date text, or an empty string when {@code date} is {@code null}
     */
    public static final String getStringFromDate(Date date) {
        return getStringFromDate(date, DATE_FORMAT);
    }

    /**
     * Writes {@code date} in the clinic's zone with {@code format}.
     *
     * @param date Date the instant to write; may be {@code null}
     * @param format DateTimeFormatter an immutable formatter, safe to keep in a shared constant
     * @return String the formatted text, or an empty string when {@code date} is {@code null}
     */
    public static final String getStringFromDate(Date date, DateTimeFormatter format) {
        return format(date, format);
    }

    /**
     * Writes {@code date} with a caller's legacy {@link DateFormat}, in that format's own zone.
     *
     * <p>A {@code DateFormat} is not safe to share between threads. This method formats with its
     * own copy of {@code format} and never changes the caller's instance, so callers that share one
     * instance and use it only through this method each get their own date back.</p>
     *
     * @param date Date the instant to write; may be {@code null}
     * @param format DateFormat the caller's format
     * @return String the formatted text, or an empty string when {@code date} is {@code null}
     * @deprecated pass an immutable {@link DateTimeFormatter} to
     *     {@link #getStringFromDate(Date, DateTimeFormatter)} instead; kept so existing callers
     *     still compile and link
     */
    @Deprecated
    public static final String getStringFromDate(Date date, DateFormat format) {
        if (date == null) {
            return "";
        }
        return ((DateFormat) format.clone()).format(date);
    }

    /**
     * Reads {@code yyyy-MM-dd} text as the clinic's midnight on that date.
     *
     * @param date String the date text; may be {@code null} or empty
     * @return Date the clinic's midnight on that date, or {@code null} for {@code null} or empty text
     * @throws IllegalStateException if the text is not a calendar date
     */
    public static final Date getDateFromString(String date) {
        if (date == null || date.isEmpty()) {
            return null;
        }
        try {
            String text = date.trim();
            if (!DATE_TEXT.matcher(text).matches()) {
                throw new DateTimeParseException("Not a yyyy-MM-dd date", text, 0);
            }
            return startOfClinicDay(LocalDate.parse(text, DATE_PARSER));
        } catch (DateTimeException e) {
            throw unparseable(e);
        }
    }

    /**
     * Reads {@code yyyy-MM-dd} text exactly as {@link #getDateFromString(String)} does. The
     * format argument has never been used: this overload has always read {@code yyyy-MM-dd}.
     *
     * @param date String the date text; may be {@code null} or empty
     * @param format DateFormat ignored
     * @return Date the clinic's midnight on that date, or {@code null} for {@code null} or empty text
     * @throws IllegalStateException if the text is not a calendar date
     * @deprecated use {@link #getDateFromString(String)}; kept so existing callers still compile
     *     and link
     */
    @Deprecated
    public static final Date getDateFromString(String date, DateFormat format) {
        return getDateFromString(date);
    }

    /**
     * Truncates {@code date} to the clinic's midnight on the same calendar date.
     *
     * @param date Date the instant to truncate; may be {@code null}
     * @return Date the clinic's midnight on that date, or {@code null} when {@code date} is {@code null}
     */
    public static final Date getDateFromDate(Date date) {
        return (date != null) ? startOfClinicDay(inClinicZone(date).toLocalDate()) : null;
    }

    // Times

    public static final Date getTimeFromLong(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        Date date = calendar.getTime();

        return getTimeFromDate(date);
    }

    /**
     * Writes the clinic's time of day of {@code time} as the short Canadian time.
     *
     * @param time Date the instant to write; may be {@code null}
     * @return String the time text, or an empty string when {@code time} is {@code null}
     */
    public static final String getStringFromTime(Date time) {
        return format(time, TIME_FORMAT);
    }

    /**
     * Reads short Canadian time text as that time of day on 1 January 1970 in the clinic's zone,
     * the date the legacy parser gave a time without a date.
     *
     * @param time String the time text; may be {@code null} or empty
     * @return Date the time on 1 January 1970, or {@code null} for {@code null} or empty text
     * @throws IllegalStateException if the text is not a short Canadian time
     */
    public static final Date getTimeFromString(String time) {
        if (time == null || time.isEmpty()) {
            return null;
        }
        try {
            LocalTime parsed = LocalTime.from(TIME_FORMAT.parse(time));
            return Date.from(LocalDate.EPOCH.atTime(parsed).atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeException e) {
            throw unparseable(e);
        }
    }

    /**
     * Keeps only the clinic's time of day of {@code date}, to the minute the short time shows.
     *
     * @param date Date the instant; may be {@code null}
     * @return Date that time on 1 January 1970, or {@code null} when {@code date} is {@code null}
     */
    public static final Date getTimeFromDate(Date date) {
        return getTimeFromString(getStringFromTime(date));
    }

    // Private methods

    /**
     * Places {@code date} in the clinic's zone. Reads the epoch milliseconds rather than calling
     * {@code toInstant()}, which a {@link java.sql.Date} refuses.
     */
    private static ZonedDateTime inClinicZone(Date date) {
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault());
    }

    private static Date startOfClinicDay(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static String format(Date date, DateTimeFormatter format) {
        return (date != null) ? format.format(inClinicZone(date)) : "";
    }

    private static IllegalStateException unparseable(DateTimeException e) {
        log.error("Error", e);
        return new IllegalStateException(e);
    }

}
