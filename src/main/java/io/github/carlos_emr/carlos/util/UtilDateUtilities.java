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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * @deprecated 2013-04-28 use io.github.carlos_emr.carlos.util.DateUtils instead
 */
@Deprecated
public class UtilDateUtilities {

    /**
     * Cached, thread-safe formatters for fixed patterns used inside this class.
     * Caller-provided patterns use {@link DateTimeFormatter#ofPattern(String, Locale)} directly;
     * that is still far cheaper than {@code new SimpleDateFormat(pattern, locale)} because
     * {@link DateTimeFormatter} is immutable and thread-safe (no per-call Calendar state).
     */
    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd");

    private static ZonedDateTime toZoned(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault());
    }

    public static Date StringToDate(String s) {
        return StringToDate(s, defaultPattern, defaultLocale);
    }

    public static Date StringToDate(String s, Locale locale) {
        return StringToDate(s, defaultPattern, locale);
    }

    public static Date StringToDate(String s, String spattern) {
        return StringToDate(s, spattern, defaultLocale);
    }

    public static Date StringToDate(String s, String spattern, Locale locale) {
        try {
            return parseToDate(s, spattern, locale);
        } catch (Exception exception) {
            return null;
        }
    }

    public static String DateToString(Date date) {
        return DateToString(date, defaultPattern, defaultLocale);
    }

    public static String DateToString(Date date, Locale locale) {
        return DateToString(date, defaultPattern, locale);
    }

    public static String DateToString(Date date, String spattern) {
        return DateToString(date, spattern, defaultLocale);
    }

    public static String DateToString(Date date, String spattern, Locale locale) {
        if (date != null) {
            return DateTimeFormatter.ofPattern(spattern, locale).format(toZoned(date));
        } else {
            return "";
        }
    }

    //"yyyy-MM-dd";
    public static String justYear(Date date) {
        return YEAR_FORMATTER.format(toZoned(date));
    }

    public static String justMonth(Date date) {
        return MONTH_FORMATTER.format(toZoned(date));
    }

    public static String justDay(Date date) {
        return DAY_FORMATTER.format(toZoned(date));
    }

    public static Date Tomorrow() {
        Calendar c = GregorianCalendar.getInstance();
        c.roll(Calendar.DATE, 1);
        return c.getTime();
    }

    public static Date calcDate(String s, String s1, String s2) {
        if (s == null || s1 == null || s2 == null || s.isEmpty() || s1.isEmpty() || s2.isEmpty()) return (null);

        int i = Integer.parseInt(s);
        int j = Integer.parseInt(s1) - 1;
        int k = Integer.parseInt(s2);
        GregorianCalendar gregoriancalendar = new GregorianCalendar(i, j, k);
        return gregoriancalendar.getTime();
    }

    public static String calcAge(Date DOB) {
        return calcAgeAtDate(DOB, new GregorianCalendar().getTime());
    }


    /**
     * This returns the Patients Age string at a point in time.  IE. How old the patient will be right now or how old will they be on march.31 of this year.
     *
     * @param DOB         Demographics Date of birth
     * @param pointInTime The date you would like to calculate there age at.
     * @return age string ( ie 2 months, 4 years .etc )
     */
    public static String calcAgeAtDate(Date DOB, Date pointInTime) {
        if (DOB == null) return (null);

        // If as of date is before birth, return "Not born"
        if (pointInTime.before(DOB)) {
            return ResourceBundle.getBundle("oscarResources").getString("global.notBorn");
        }

        GregorianCalendar now = new GregorianCalendar();
        now.setTime(pointInTime);
        int curYear = now.get(Calendar.YEAR);
        int curMonth = now.get(Calendar.MONTH) + 1;
        int curDay = now.get(Calendar.DAY_OF_MONTH);

        GregorianCalendar birthDate = new GregorianCalendar();
        birthDate.setTime(DOB);
        int birthYear = birthDate.get(Calendar.YEAR);
        int birthMonth = birthDate.get(Calendar.MONTH) + 1;
        int birthDay = birthDate.get(5);

        int ageInYears = curYear - birthYear;
        String result = ageInYears + " " + ResourceBundle.getBundle("oscarResources_en").getString("global.years");


        if (curMonth > birthMonth || curMonth == birthMonth && curDay >= birthDay) {
            ageInYears = curYear - birthYear;
            result = ageInYears + " " + ResourceBundle.getBundle("oscarResources_en").getString("global.years");
        } else {
            ageInYears = curYear - birthYear - 1;
            result = ageInYears + " " + ResourceBundle.getBundle("oscarResources_en").getString("global.years");
        }
        if (ageInYears < 2) {
            int yearDiff = curYear - birthYear;
            int ageInDays;
            if (yearDiff == 2) {
                ageInDays = (birthDate.getActualMaximum(Calendar.DAY_OF_YEAR) - birthDate.get(Calendar.DAY_OF_YEAR)) + now.get(Calendar.DAY_OF_YEAR) + 365;
            } else if (yearDiff == 1) {
                ageInDays = (birthDate.getActualMaximum(Calendar.DAY_OF_YEAR) - birthDate.get(Calendar.DAY_OF_YEAR)) + now.get(Calendar.DAY_OF_YEAR);
            } else {
                ageInDays = now.get(Calendar.DAY_OF_YEAR) - birthDate.get(Calendar.DAY_OF_YEAR);
            }
            if (ageInDays / 7 > 9) {
                result = ageInDays / 30 + " " + ResourceBundle.getBundle("oscarResources_en").getString("global.months");
            } else if (ageInDays >= 14) {
                result = ageInDays / 7 + " " + ResourceBundle.getBundle("oscarResources_en").getString("global.weeks");
            } else {
                result = ageInDays + " " + ResourceBundle.getBundle("oscarResources_en").getString("global.days");
            }
        }
        return result;
    }


    public static int calcAge(String year_of_birth, String month_of_birth, String date_of_birth) {
        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = (now.get(Calendar.MONTH) + 1);
        int curDay = now.get(Calendar.DAY_OF_MONTH);
        int age = 0;

        if (curMonth > Integer.parseInt(month_of_birth)) {
            age = curYear - Integer.parseInt(year_of_birth);
        } else {
            if (curMonth == Integer.parseInt(month_of_birth) && curDay > Integer.parseInt(date_of_birth)) {
                age = curYear - Integer.parseInt(year_of_birth);
            } else {
                age = curYear - Integer.parseInt(year_of_birth) - 1;
            }
        }
        return age;
    }

    private static String defaultPattern = "yyyy-MM-dd";
    //    private static String dateTimePattern = "yyyy-MM-dd HH:mm:ss"; timeStampPattern = "yyyyMMddHHmmss";
    private static Locale defaultLocale = Locale.CANADA;

    public static String getToday(String datePattern) {
        return DateTimeFormatter.ofPattern(datePattern).format(toZoned(new Date()));
    }

    /**
     * For Parsing Dates.
     *
     * @param dateStr     The date string to be parsed
     * @param datePattern The date pattern to use to parse the date string
     * @return Date object. If date was unable to be parsed the object will be null
     */
    public static Date getDateFromString(String dateStr, String datePattern) {
        try {
            return parseToDate(dateStr, datePattern, defaultLocale);
        } catch (DateTimeParseException e) {
            //no point logging this..returns null
            return null;
        }
    }

    /**
     * Thread-safe parse helper. Parses {@code s} using {@code pattern}+{@code locale} and
     * returns a {@link Date} representing the equivalent instant in the system default zone.
     * Handles patterns with time-of-day components and date-only patterns transparently.
     */
    private static Date parseToDate(String s, String pattern, Locale locale) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, locale);
        TemporalAccessor parsed = formatter.parse(s);
        ZoneId zone = ZoneId.systemDefault();
        Instant instant;
        try {
            instant = LocalDateTime.from(parsed).atZone(zone).toInstant();
        } catch (DateTimeException e) {
            try {
                instant = LocalDate.from(parsed).atStartOfDay(zone).toInstant();
            } catch (DateTimeException e2) {
                instant = Instant.from(parsed);
            }
        }
        return Date.from(instant);
    }


    //This if probably not the most effiecent way to calcu

    /**
     * Gets the number of months between two date objects
     *
     * @param dStart Start Date
     * @param dEnd   End Date
     * @return the number of months
     */
    public static int getNumMonths(Date dStart, Date dEnd) {
        if (dStart == null || dEnd == null) return (0);

        int i = 0;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dStart);
        while (calendar.getTime().before(dEnd) || calendar.getTime().equals(dEnd)) {
            calendar.add(Calendar.MONTH, 1);
            i++;
        }
        i--;
        if (i < 0) {
            i = 0;
        }
        return i;
    }


    //This if probably not the most effiecent way to calcu

    /**
     * Gets the number of months between two date objects
     *
     * @param dStart Start Date
     * @param dEnd   End Date
     * @return the number of months
     */
    public static long getNumDays(Date dStart, Date dEnd) {
        if (dStart == null || dEnd == null) return (0);

        long msDifference = dEnd.getTime() - dStart.getTime();
        long daysDifference = msDifference / (1000 * 60 * 60 * 24);
        return daysDifference;
    }

    /**
     * Gets the number of years between two date objects
     *
     * @param dStart Start Date
     * @param dEnd   End Date
     * @return Number of year between
     */
    public static int getNumYears(Date dStart, Date dEnd) {
        if (dStart == null || dEnd == null) return (0);

        GregorianCalendar now = new GregorianCalendar();
        now.setTime(dEnd);
        int curYear = now.get(Calendar.YEAR);
        int curMonth = now.get(Calendar.MONTH) + 1;
        int curDay = now.get(Calendar.DAY_OF_MONTH);

        GregorianCalendar birthDate = new GregorianCalendar();
        birthDate.setTime(dStart);
        int birthYear = birthDate.get(Calendar.YEAR);
        int birthMonth = birthDate.get(Calendar.MONTH) + 1;
        int birthDay = birthDate.get(5);

        int ageInYears = curYear - birthYear;

        if (curMonth > birthMonth || curMonth == birthMonth && curDay >= birthDay) {
            ageInYears = curYear - birthYear;
        } else {
            ageInYears = curYear - birthYear - 1;
        }
        return ageInYears;
    }


    /**
     * Gets the number of months between two Calendar objects
     *
     * @param dStart start date
     * @param dEnd   end date
     * @return number of months between
     */
    public static int getNumMonths(Calendar dStart, Calendar dEnd) {
        return getNumMonths(dStart.getTime(), dEnd.getTime());
    }


    public static int calculateGestationAge(Date today, Date edd) {
        int i = 40;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(edd);
        //Is Today before edd
        if (today.before(edd)) {

            while (today.before(calendar.getTime()) || today.equals(calendar.getTime())) {
                i--;
                calendar.add(Calendar.DAY_OF_YEAR, -7);
                if (i < 0) {
                    break;
                }
            }
            i++;
        } else if (today.after(edd)) {
            // Weeks past 40 weeks?
            while (today.after(calendar.getTime())) {
                i++;
                calendar.add(Calendar.DAY_OF_YEAR, 7);
            }
        }

        if (i < 0) {
            i = 0;
        }
        return i;
    }


    public static int nullSafeCompare(Date d1, Date d2) {
        if (d1 == null && d2 == null) return 0;
        if (d1 == null) return 1;
        if (d2 == null) return -1;

        if (d1.equals(d2)) return 0;
        if (d1.before(d2)) {
            return 1;
        } else {
            return -1;
        }

    }
}
