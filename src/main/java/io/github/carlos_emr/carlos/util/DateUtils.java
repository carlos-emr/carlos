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


package io.github.carlos_emr.carlos.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * Date formatting and parsing utility class providing locale-aware date/time
 * operations, calendar arithmetic, age calculations, and null-safe date comparisons.
 * Reads date and time format patterns from CARLOS properties.
 *
 * @deprecated 2013-12-09 use {@code io.github.carlos_emr.carlos.util.DateUtils} - note: this class
 *     appears to be the intended replacement despite sharing the same simple name. The deprecation
 *     notice is inherited from the upstream project.
 * @since 2001-01-01
 */
@Deprecated
public final class DateUtils {

    private static Logger logger = MiscUtils.getLogger();

    private static String dateFormatString = CarlosProperties.getInstance().getProperty("DATE_FORMAT");
    private static String timeFormatString = CarlosProperties.getInstance().getProperty("TIME_FORMAT");

    /**
     * @param locale can be null
     * @return if date is null will return a blank string.
     */
    public static String formatDate(Date date, Locale locale) {
        return (format(dateFormatString, date, locale));
    }

    /**
     * @param locale can be null
     * @return if date is null will return a blank string.
     */
    public static String formatTime(Date date, Locale locale) {
        return (format(timeFormatString, date, locale));
    }

    /**
     * Formats a date and time into a single string using the configured date and time patterns.
     *
     * @param date Date the date to format
     * @param locale Locale the locale for formatting (can be null)
     * @return String the formatted date-time string
     */
    public static String formatDateTime(Date date, Locale locale) {
        return (formatDate(date, locale) + ' ' + formatTime(date, locale));
    }

    /**
     * @param locale can be null
     * @return if String is null will return null.
     * @throws ParseException
     */
    public static Date parseDate(String s, Locale locale) throws ParseException {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }

        SimpleDateFormat dateFormatter = null;

        if (locale == null) dateFormatter = new SimpleDateFormat(dateFormatString);
        else dateFormatter = new SimpleDateFormat(dateFormatString, locale);

        return (dateFormatter.parse(s));
    }

    /**
     * @param locale can be null
     * @return if String is null will return null.
     * @throws ParseException
     */
    public static Date parseDateTime(String s, Locale locale) throws ParseException {
        if (s == null) return (null);

        SimpleDateFormat dateTimeFormatter = null;

        if (locale == null) dateTimeFormatter = new SimpleDateFormat(dateFormatString + " " + timeFormatString);
        else dateTimeFormatter = new SimpleDateFormat(dateFormatString + " " + timeFormatString, locale);

        return (dateTimeFormatter.parse(s));
    }

    /**
     * @param locale can be null
     * @return if calendar is null will return a blank string.
     */
    public static String formatDate(Calendar calendar, Locale locale) {
        if (calendar == null) return ("");

        return (formatDate(calendar.getTime(), locale));
    }

    /**
     * @param locale can be null
     * @return if calendar is null will return a blank string.
     */
    public static String formatTime(Calendar calendar, Locale locale) {
        if (calendar == null) return ("");

        return (formatTime(calendar.getTime(), locale));
    }

    /**
     * Formats a calendar date and time into a single string.
     *
     * @param calendar Calendar the calendar to format
     * @param locale Locale the locale for formatting (can be null)
     * @return String the formatted date-time string
     */
    public static String formatDateTime(Calendar calendar, Locale locale) {
        return (formatDate(calendar, locale) + ' ' + formatTime(calendar, locale));
    }

    /**
     * @param locale can be null
     * @return if String is null will return null.
     * @throws ParseException
     */
    public static synchronized GregorianCalendar parseDateAsCalendar(String s, Locale locale) throws ParseException {
        Date d = parseDate(s, locale);

        if (d == null) return (null);

        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(d);
        zeroTimeFields(cal);
        return (cal);
    }

    /**
     * sets hours/minutes/seconds/milliseconds to 0
     *
     * @deprecated use apache commons DateUtils.truncate()
     */
    @Deprecated
    public static void zeroTimeFields(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.getTimeInMillis();
    }

    /**
     * Formats a date using the specified pattern with the default locale.
     *
     * @param format String the {@link SimpleDateFormat} pattern
     * @param date Date the date to format
     * @return String the formatted date string, or blank if date is null
     */
    public static String format(String format, Date date) {
        return format(format, date, null);
    }

    /**
     * @param locale can be null
     * @return if date is null will return a blank string.
     */
    public static String format(String format, Date date, Locale locale) {
        if (date == null) return ("");

        SimpleDateFormat dateFormatter = null;

        if (locale == null) dateFormatter = new SimpleDateFormat(format);
        else dateFormatter = new SimpleDateFormat(format, locale);

        return (dateFormatter.format(date));
    }

    /**
     * @deprecated use formatDate() parseDate() instead
     */
    @Deprecated
    private static SimpleDateFormat sdf;

    /**
     * @deprecated use formatDate() parseDate() instead
     */
    @Deprecated
    private static String formatDate = "dd/MM/yyyy";

    /**
     * Formats a calendar to ISO date-time format with a space instead of 'T' separator.
     *
     * @param cal Calendar the calendar to format
     * @return String the ISO date-time string with space separator
     */
    public static String getISODateTimeFormatNoT(Calendar cal) {
        return (DateFormatUtils.ISO_DATETIME_FORMAT.format(cal).replace('T', ' '));
    }

    /**
     * @deprecated use formatDate() parseDate() instead
     */
    @Deprecated
    public static SimpleDateFormat getDateFormatter() {

        if (sdf == null) {

            sdf = new SimpleDateFormat(formatDate);

        }

        return sdf;

    }

    /**
     * Sets the global date formatter pattern.
     *
     * @param pattern String the {@link SimpleDateFormat} pattern
     * @deprecated use formatDate() parseDate() instead
     */
    public static void setDateFormatter(String pattern) {

        sdf = new SimpleDateFormat(pattern);

    }

    /**
     * Returns today's date formatted using the default locale date format.
     *
     * @return String today's date as a formatted string
     */
    public static String getDate() {

        Date date = new Date();

        return DateFormat.getDateInstance().format(date);

    }

    /**
     * Formats a date using the default SimpleDateFormat pattern.
     *
     * @param date Date the date to format
     * @return String the formatted date string
     */
    public static String getDate(Date date) {

        SimpleDateFormat sdf = new SimpleDateFormat();

        return sdf.format(date);

    }

    /**
     * Formats a date using the specified pattern and locale.
     *
     * @param date Date the date to format (returns empty string if null)
     * @param format String the {@link SimpleDateFormat} pattern
     * @param locale Locale the locale for formatting
     * @return String the formatted date string, or empty if date is null
     */
    public static String getDate(Date date, String format, Locale locale) {
        if (date == null) return "";

        SimpleDateFormat sdf = new SimpleDateFormat(format, locale);

        return sdf.format(date);
    }

    /**
     * Formats a date using the specified pattern.
     *
     * @param date Date the date to format
     * @param format String the {@link SimpleDateFormat} pattern
     * @return String the formatted date string
     */
    public static String getDate(Date date, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);

        return sdf.format(date);
    }

    /**
     * Returns the current date and time formatted using the default locale date-time format.
     *
     * @return String the current date-time as a formatted string
     */
    public static String getDateTime() {

        Date date = new Date();

        return DateFormat.getDateTimeInstance().format(date);

    }

    /**
     * @deprecated use formatDate(Date,Locale) instead.
     */
    @Deprecated
    public static String formatDate(String date, String format,

                                    String formatAtual) {

        try {

            setDateFormatter(formatAtual);

            Date data = getDateFormatter().parse(date);

            logger.debug("[DateUtils] - formatDate: data formatada: " +

                    getDateFormatter().format(data));

            setDateFormatter(format);

            return getDateFormatter().format(data);

        } catch (ParseException e) {

            logger.error("[DateUtils] - formatDate: ", e);

        }

        return "";

    }

    /**
     * @deprecated use formatDate(Date,Locale) instead.
     */
    @Deprecated
    public static String formatDate(String date, String format) {

        try {

            SimpleDateFormat sdf = new SimpleDateFormat();

            Date data = sdf.parse(date);

            logger.debug("[DateUtils] - formatDate: data formatada: " +

                    sdf.format(data));

            setDateFormatter(format);

            return getDateFormatter().format(data);

        } catch (ParseException e) {

            logger.error("[DateUtils] - formatDate: ", e);

        }

        return "";

    }

    /**
     * Adds a number of days to a start date and returns the result as a formatted string.
     *
     * @param startDate Date the starting date
     * @param numDaysAdded int the number of days to add
     * @param locale Locale the locale for date formatting
     * @return String the resulting date formatted as a string, or "INVALID DATE" if start is null
     */
    public static String sumDate(Date startDate, int numDaysAdded, Locale locale) {
        String sumDateStr = "INVALID DATE";
        if (startDate != null) {
            Calendar calendar = new GregorianCalendar();
            calendar.setTime(startDate);
            calendar.add(Calendar.DATE, numDaysAdded);
            Date sumDate = calendar.getTime();
            sumDateStr = formatDate(sumDate, locale);
        }
        return sumDateStr;
    }

    /**
     * Adds a number of days (as a string) to today's date and returns the result formatted.
     *
     * @param format String the {@link SimpleDateFormat} pattern for the result
     * @param pSum String the number of days to add (parsed as integer)
     * @return String the resulting date formatted as a string
     */
    public static String sumDate(String format, String pSum) {

        int iSum = Integer.valueOf(pSum).intValue();

        logger.debug("[DateUtils] - sumDate: iSum = " + iSum);

        Calendar calendar = new GregorianCalendar();

        Date now = new Date();

        calendar.setTime(now);

        calendar.add(Calendar.DATE, iSum);

        Date data = calendar.getTime();

        setDateFormatter(format);

        return getDateFormatter().format(data);

    }

    public String NextDay(int day, int month, int year) {

        boolean leapyear;

        switch (month) {

            // the months with 31 days without december

            case 1:

            case 3:

            case 5:

            case 7:

            case 8:

            case 10:

                if (day < 31) {

                    day++;

                } else {

                    day = 1;

                    month++;

                }

                break;

            case 12:

                if (day < 31) {

                    day++;

                } else {

                    day = 1;

                    month = 1;

                    year++;

                }

                break;

            case 2:

                if (day < 28) {

                    day++;

                } else {

                    if (((year % 4 == 0) && !(year % 100 == 0)) || (year % 400 == 0)) {

                        leapyear = true;

                    } else {

                        leapyear = false;

                        // in a leapyear 29 days

                    }
                    if (leapyear == true) {

                        if (day == 28) {

                            day++;

                        } else {

                            day = 1;

                            month++;

                        }
                    } else {

                        day = 1;

                        month++;

                    }

                }

                break;

            // these are the other month 4 6 9 11

            default:

                if (day < 30) {

                    day++;

                } else {

                    day = 1;

                    month++;

                }

        } // switch

        String nextDay = year + "-" + month + "-" + day;

        return nextDay;

    }

    public String NextDay(int day, int month, int year, int numDays) {

        int modValue = 28;

        while (numDays > 0) {

            int curNumDays = numDays % modValue;

            if (curNumDays == 0) {

                curNumDays = modValue;

            }

            switch (month) {

                // the months with 31 days without december

                case 1:

                case 3:

                case 5:

                case 7:

                case 8:

                case 10:

                    if (day + curNumDays < 31) {

                        day = day + curNumDays;

                    } else if (((day + curNumDays) % 31) == 0) {

                        day = 31;

                    } else {

                        day = ((day + curNumDays) % 31);

                        month++;

                    }

                    break;

                case 12:

                    if (day + curNumDays < 31) {

                        day = day + curNumDays;

                    } else if (((day + curNumDays) % 31) == 0) {

                        day = 31;

                    } else {

                        day = ((day + curNumDays) % 31);

                        month = 1;

                        year++;

                    }

                    break;

                case 2:

                    if (((year % 4 == 0) && !(year % 100 == 0)) || (year % 400 == 0)) {

                        if (day + curNumDays < 29) {

                            day = day + curNumDays;

                        } else if (((day + curNumDays) % 29) == 0) {

                            day = 29;

                        } else {

                            day = ((day + curNumDays) % 29);

                            month++;

                        }

                    } else {

                        if (day + curNumDays < 28) {

                            day = day + curNumDays;

                        } else if (((day + curNumDays) % 28) == 0) {

                            day = 28;

                        } else {

                            day = ((day + curNumDays) % 28);

                            month++;

                        }

                    }

                    break;

                // these are the other month 4 6 9 11

                default:

                    if (day + curNumDays < 30) {

                        day = day + curNumDays;

                    } else if (((day + curNumDays) % 30) == 0) {

                        day = 30;

                    } else {

                        day = ((day + curNumDays) % 30);

                        month++;

                    }

            } // switch

            numDays = numDays - curNumDays;

        }

        String nextDay = year + "-" + month + "-" + day;

        return nextDay;

    }

    /**
     * Gets the difference between two dates, in days. Takes two dates represented in milliseconds and returns the difference in days
     */
    public static long getDifDays(Date greater, Date lesser) {
        long timeInMillis = greater.getTime() - lesser.getTime();
        return (timeInMillis / org.apache.commons.lang3.time.DateUtils.MILLIS_PER_DAY);
    }

    public static long getNumberOfDaysBetweenTwoDates(Calendar cal1, Calendar cal2) {
        long timeInMillis = Math.abs(cal1.getTimeInMillis() - cal2.getTimeInMillis());
        return (timeInMillis / org.apache.commons.lang3.time.DateUtils.MILLIS_PER_DAY);
    }

    public static long getNumberOfDaysBetweenTwoDates(Date date1, Date date2) {
        long timeInMillis = Math.abs(date1.getTime() - date2.getTime());
        return (timeInMillis / org.apache.commons.lang3.time.DateUtils.MILLIS_PER_DAY);
    }

    /**
     * Converts a String date with the form 'yyyy-MM-dd' to a String date with the form 'yyyyMMdd'
     *
     * @param oldDateString String - The string to be converted
     * @return String - The formatted date String
     */
    public static String convertDate8Char(String oldDateString) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        String sdate = "00000000";
        try {
            if (oldDateString != null) {
                Date tempDate = fmt.parse(oldDateString);
                sdate = new SimpleDateFormat("yyyyMMdd").format(tempDate);
            }
        } catch (ParseException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }
        return sdate;
    }

    /**
     * null safe method for converting a date object to calendar object.
     */
    public static GregorianCalendar toGregorianCalendar(Date date) {
        if (date == null) return (null);

        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        gregorianCalendar.setTime(date);
        gregorianCalendar.getTime();
        return (gregorianCalendar);
    }

    /**
     * null safe method for converting an iso date from string to calendar.
     */
    public static GregorianCalendar toGregorianCalendarDate(String isoDateString) {
        if (isoDateString == null) return (null);

        try {
            String[] split = isoDateString.split("-");
            int year = Integer.parseInt(split[0]);
            int month = Integer.parseInt(split[1]) - 1;
            int day = Integer.parseInt(split[2]);

            return (new GregorianCalendar(year, month, day));
        } catch (Exception e) {
            throw (new IllegalArgumentException("The passed in string is not a valid ISO date"));
        }
    }

    public static Date toDate(String isoDateString) {
        GregorianCalendar cal = toGregorianCalendarDate(isoDateString);
        if (cal != null) return (cal.getTime());
        else return (null);
    }

    /**
     * date2-date1
     * <p>
     * if either are null, it returns null.
     */
    public static Integer yearDifference(Date date1, Date date2) {
        if (date1 == null || date2 == null) return (null);

        Calendar cal1 = new GregorianCalendar();
        cal1.setTime(date1);

        Calendar cal2 = new GregorianCalendar();
        cal2.setTime(date2);

        return (yearDifference(cal1, cal2));
    }

    /**
     * date2-date1
     * <p>
     * if either are null, it returns null.
     */
    public static Integer yearDifference(Calendar date1, Calendar date2) {
        if (date1 == null || date2 == null) return (null);

        int yearDiff = date2.get(Calendar.YEAR) - date1.get(Calendar.YEAR);

        if (date2.get(Calendar.MONTH) > date1.get(Calendar.MONTH)) yearDiff--;
        else if (date2.get(Calendar.MONTH) == date1.get(Calendar.MONTH) && date2.get(Calendar.DAY_OF_MONTH) < date1.get(Calendar.DAY_OF_MONTH))
            yearDiff--;

        return (yearDiff);
    }

    /**
     * This method will calculate the age of the person on the given date.
     */
    public static Integer getAge(Calendar dateOfBirth, Calendar onThisDay) {
        return (yearDifference(dateOfBirth, onThisDay));
    }

    /**
     * date2-date1
     * <p>
     * if either are null, it returns null.
     */
    public static Integer calculateDayDifference(Calendar date1, Calendar date2) {
        if (date1 == null || date2 == null) return (null);

        long ms = date2.getTimeInMillis() - date1.getTimeInMillis();
        return ((int) (ms / org.apache.commons.lang3.time.DateUtils.MILLIS_PER_DAY));
    }

    public static Integer calculateDayDifference(Calendar date1, Date date2) {

        return (calculateDayDifference(date1, toCalendar(date2)));
    }

    public static Integer calculateDayDifference(Date date1, Calendar date2) {
        return (calculateDayDifference(toCalendar(date1), date2));
    }

    /**
     * date2-date1
     * <p>
     * if either are null, it returns null.
     */
    public static Integer calculateDayDifference(Date date1, Date date2) {
        return (calculateDayDifference(toCalendar(date1), toCalendar(date2)));
    }


    public static Integer nullSafeCompare(Date d1, Date d2) {
        if (d1 == null && d2 == null) return 0;
        if (d1 == null) return 1;
        if (d2 == null) return -1;

        if (d1.equals(d2)) return 0;
        if (d1.before(d2)) return 1;
        if (d1.after(d2)) return -1;

        return null; //should never happen
    }

    /**
     * This will take 2 date objects, presumably one that holds the date and the other that holds the time
     * and it will merge the two into one object that has both the date and the time. This method is not
     * normally useful but our database seems to have a lot of split date/time objects.
     * If either parameters are null it will return null.
     * This method will materialise the result before returning.
     */
    public static GregorianCalendar toGregorianCalendar(Date date, Date time) {
        if (date == null || time == null) return (null);

        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(date);

        GregorianCalendar cal2 = new GregorianCalendar();
        cal2.setTime(time);

        cal.set(GregorianCalendar.HOUR_OF_DAY, cal2.get(GregorianCalendar.HOUR_OF_DAY));
        cal.set(GregorianCalendar.MINUTE, cal2.get(GregorianCalendar.MINUTE));
        cal.set(GregorianCalendar.SECOND, cal2.get(GregorianCalendar.SECOND));
        cal.set(GregorianCalendar.MILLISECOND, cal2.get(GregorianCalendar.MILLISECOND));
        cal.getTime();

        return (cal);
    }

    /**
     * This method will set the calendar to the beginning of the month, i.e. day=1, hour=0, minute=0, sec=0, ms=0. It will return the same instance passed in (not a clone of it).
     */
    public static Calendar setToBeginningOfMonth(Calendar cal) {
        cal.set(Calendar.DAY_OF_MONTH, 1);
        return (setToBeginningOfDay(cal));
    }

    /**
     * This method will set the calenders hour/min/sec//milliseconds all to 0.
     */
    public static Calendar setToBeginningOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // force calculation / materialisation of actual time.
        cal.getTimeInMillis();

        return (cal);
    }

    public static GregorianCalendar toCalendar(Date date) {
        if (date == null) return (null);

        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(date);
        return (cal);
    }

    /**
     * Null safe toDate
     */
    public static Date toDate(Calendar cal) {
        if (cal == null) return (null);

        return (cal.getTime());
    }
}
