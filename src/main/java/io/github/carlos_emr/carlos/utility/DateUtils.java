/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.utility;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * Date and time utility class providing formatting, parsing, and calculation functions.
 * 
 * <p>This class provides methods for:</p>
 * <ul>
 *   <li>Formatting dates and times according to system-configured formats</li>
 *   <li>Parsing dates from various string formats</li>
 *   <li>Converting between different date types (java.util.Date, java.sql.Date, Calendar)</li>
 *   <li>Calculating date differences and date arithmetic</li>
 *   <li>Validating date ranges and checking for date ordering</li>
 * </ul>
 * 
 * <p>Date and time formats are configured via CarlosProperties:</p>
 * <ul>
 *   <li><code>DATE_FORMAT</code> - System-wide date format (e.g., "yyyy-MM-dd")</li>
 *   <li><code>TIME_FORMAT</code> - System-wide time format (e.g., "HH:mm:ss")</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> This class is not thread-safe when using SimpleDateFormat.
 * Consider using java.time package (Java 8+) for thread-safe date/time operations.</p>
 * 
 * @see java.time For modern, thread-safe date/time handling (Java 8+)
 */
public final class DateUtils {
    /** JavaScript-compatible ISO date format pattern */
    public static final String JS_ISO_DATE_FORMAT = "yy-mm-dd";

    /**
     * Constructs a new DateUtils instance.
     * Note: This is a utility class with static methods; instantiation is not typically needed.
     */
    public DateUtils() {
    }

    /** System-configured date format from properties */
    private static String dateFormatString = CarlosProperties.getInstance().getProperty("DATE_FORMAT");
    
    /** System-configured time format from properties */
    private static String timeFormatString = CarlosProperties.getInstance().getProperty("TIME_FORMAT");

    /**
     * Formats a date using the system date format.
     * 
     * @param date the date to format
     * @param locale the locale for formatting (can be null for default locale)
     * @return formatted date string, or empty string if date is null
     */
    public static String formatDate(Date date, Locale locale) {
        return (format(dateFormatString, date, locale));
    }

    /**
     * Formats a time using the system time format.
     * 
     * @param date the date/time to format
     * @param locale the locale for formatting (can be null for default locale)
     * @return formatted time string, or empty string if date is null
     */
    public static String formatTime(Date date, Locale locale) {
        return (format(timeFormatString, date, locale));
    }

    /**
     * Formats a date and time using the system date and time formats.
     * 
     * @param date the date/time to format
     * @param locale the locale for formatting (can be null for default locale)
     * @return formatted datetime string with date and time separated by a space
     */
    public static String formatDateTime(Date date, Locale locale) {
        return (formatDate(date, locale) + ' ' + formatTime(date, locale));
    }

    /**
     * Formats a date using a custom format string and locale.
     * 
     * @param format the date format pattern (e.g., "yyyy-MM-dd")
     * @param date the date to format
     * @param locale the locale for formatting (can be null for default locale)
     * @return formatted date string, or empty string if date is null
     */
    public static String format(String format, Date date, Locale locale) {
        if (date == null) {
            return "";
        }

        SimpleDateFormat dateFormatter;
        if (locale == null) {
            dateFormatter = new SimpleDateFormat(format);
        } else {
            dateFormatter = new SimpleDateFormat(format, locale);
        }

        return dateFormatter.format(date);
    }

    /**
     * Returns an ISO datetime string without the 'T' separator and without seconds.
     *
     * @param cal Calendar the calendar to format
     * @return String the formatted datetime (e.g., "2026-03-22 14:30"), or empty string if {@code null}
     */
    public static String getIsoDateTimeNoTNoSeconds(Calendar cal) {
        if (cal == null) {
            return "";
        } else {
            String s = getIsoDateTimeNoT(cal);
            return s.substring(0, s.length() - 3);
        }
    }

    /**
     * Returns an ISO datetime string with the 'T' separator replaced by a space.
     *
     * @param cal Calendar the calendar to format
     * @return String the formatted datetime, or empty string if {@code null}
     */
    public static String getIsoDateTimeNoT(Calendar cal) {
        return cal == null ? "" : DateFormatUtils.ISO_DATETIME_FORMAT.format(cal).replace('T', ' ');
    }

    /**
     * Returns a standard ISO datetime string including the 'T' separator.
     *
     * @param cal Calendar the calendar to format
     * @return String the ISO formatted datetime, or empty string if {@code null}
     */
    public static String getIsoDateTime(Calendar cal) {
        return cal == null ? "" : DateFormatUtils.ISO_DATETIME_FORMAT.format(cal);
    }

    /**
     * Returns an ISO date string (date portion only, no time).
     *
     * @param cal Calendar the calendar to format
     * @return String the ISO formatted date, or empty string if {@code null}
     */
    public static String getIsoDate(Calendar cal) {
        return cal == null ? "" : DateFormatUtils.ISO_DATE_FORMAT.format(cal);
    }

    /**
     * Parses an ISO date string into a {@link Date}.
     *
     * @param s String the ISO date string to parse
     * @return Date the parsed date, or {@code null} if the input is blank
     * @throws ParseException if the string cannot be parsed
     */
    public static Date parseIsoDate(String s) throws ParseException {
        s = StringUtils.trimToNull(s);
        if (s == null) {
            return null;
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat(DateFormatUtils.ISO_DATE_FORMAT.getPattern());
            Date date = dateFormat.parse(s);
            return date;
        }
    }

    /**
     * Parses an ISO date string into a {@link GregorianCalendar}.
     *
     * @param s String the ISO date string to parse
     * @return GregorianCalendar the parsed calendar, or {@code null} if the input is blank
     * @throws ParseException if the string cannot be parsed
     */
    public static GregorianCalendar parseIsoDateAsCalendar(String s) throws ParseException {
        Date date = parseIsoDate(s);
        if (date == null) {
            return null;
        } else {
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(date);
            cal.getTimeInMillis();
            return cal;
        }
    }

    /**
     * Parses an ISO datetime string into a {@link Date}.
     *
     * @param s String the ISO datetime string to parse
     * @return Date the parsed date, or {@code null} if the input is blank
     * @throws ParseException if the string cannot be parsed
     */
    public static Date parseIsoDateTime(String s) throws ParseException {
        s = StringUtils.trimToNull(s);
        if (s == null) {
            return null;
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat(DateFormatUtils.ISO_DATETIME_FORMAT.getPattern());
            Date date = dateFormat.parse(s);
            return date;
        }
    }

    /**
     * Parses an ISO datetime string into a {@link GregorianCalendar}.
     *
     * @param s String the ISO datetime string to parse
     * @return GregorianCalendar the parsed calendar, or {@code null} if the input is blank
     * @throws ParseException if the string cannot be parsed
     */
    public static GregorianCalendar parseIsoDateTimeAsCalendar(String s) throws ParseException {
        Date date = parseIsoDateTime(s);
        if (date == null) {
            return null;
        } else {
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(date);
            cal.getTimeInMillis();
            return cal;
        }
    }

    /**
     * Calculates the difference in whole years between two calendar dates.
     *
     * @param date1 Calendar the earlier date
     * @param date2 Calendar the later date
     * @return Integer the number of complete years between the dates, or {@code null} if either is {@code null}
     */
    public static Integer yearDifference(Calendar date1, Calendar date2) {
        if (date1 != null && date2 != null) {
            int yearDiff = date2.get(1) - date1.get(1);
            if (date2.get(6) > date1.get(6)) {
                --yearDiff;
            }

            return yearDiff;
        } else {
            return null;
        }
    }

    /**
     * Calculates a patient's age in years as of the specified date.
     *
     * @param dateOfBirth Calendar the patient's date of birth
     * @param onThisDay   Calendar the reference date for the age calculation
     * @return Integer the age in complete years, or {@code null} if either date is {@code null}
     */
    public static Integer getAge(Calendar dateOfBirth, Calendar onThisDay) {
        return yearDifference(dateOfBirth, onThisDay);
    }

    /**
     * Resets the time portion of a calendar to midnight (00:00:00.000).
     *
     * @param cal Calendar the calendar to modify
     * @return Calendar the same calendar instance with time set to beginning of day
     */
    public static Calendar setToBeginningOfDay(Calendar cal) {
        cal.set(11, 0);
        cal.set(12, 0);
        cal.set(13, 0);
        cal.set(14, 0);
        cal.getTimeInMillis();
        return cal;
    }

    /**
     * Parses a JavaScript-style ISO datetime string without seconds (e.g., "2026-03-22 14:30").
     *
     * @param s String the datetime string to parse in "yyyy-MM-dd HH:mm" format
     * @return Date the parsed date, or {@code null} if the input is blank
     * @throws ParseException if the string cannot be parsed
     */
    public static Date parseJsIsoDateTimeNoTNoSeconds(String s) throws ParseException {
        s = StringUtils.trimToNull(s);
        if (s == null) {
            return null;
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            Date date = dateFormat.parse(s);
            return date;
        }
    }
}
