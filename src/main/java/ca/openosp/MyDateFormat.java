//CHECKSTYLE:OFF
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
 */


package ca.openosp;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.commons.KeyConstants;

/**
 * Date and time formatting utility class for handling various date/time conversions.
 * 
 * <p>This class provides a comprehensive set of static methods for:</p>
 * <ul>
 *   <li>Converting between different date formats (MySQL standard, display formats, etc.)</li>
 *   <li>Parsing date/time strings with various separators and formats</li>
 *   <li>Extracting date components (year, month, day, hour) from formatted strings</li>
 *   <li>Formatting times with AM/PM notation</li>
 *   <li>Calculating date differences</li>
 *   <li>Converting between 12-hour and 24-hour time formats</li>
 * </ul>
 * 
 * <p>Common date formats used:</p>
 * <ul>
 *   <li><strong>MySQL standard:</strong> yyyy-MM-dd (e.g., "2024-01-15")</li>
 *   <li><strong>Display format:</strong> yyyy/MM/dd (e.g., "2024/01/15")</li>
 *   <li><strong>Compact format:</strong> yyyyMMdd (e.g., "20240115")</li>
 *   <li><strong>Time format:</strong> HH:mm:ss (24-hour) or hh:mm am/pm (12-hour)</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> This class is not thread-safe when using SimpleDateFormat.
 * Consider using java.time package (Java 8+) for thread-safe date/time operations.</p>
 */
public class MyDateFormat {
    
    /**
     * Constructs a new MyDateFormat instance.
     */
    public MyDateFormat() {
        //this.aDateTime = d;
    }

    /**
     * Calculates the number of days between two Calendar dates.
     * 
     * @param start the start date
     * @param end the end date
     * @return the number of days between start and end, or 0 if either parameter is null
     */
    public static int getDaysDiff(Calendar start, Calendar end) {
        if (start == null || end == null) return 0;
        long days = (end.getTimeInMillis() - start.getTimeInMillis()) / (24 * 60 * 60 * 1000);
        return (int) days;
    }

    /**
     * Formats a month or day value to always be two digits with leading zero if needed.
     * 
     * @param value the month or day value (e.g., "8" or "15")
     * @return two-digit string (e.g., "08" or "15")
     */
    public static String formatMonthOrDay(String value) {
        String str2 = "0" + value;
        return str2.substring(str2.length() - 2, str2.length());
    }

    /**
     * Converts an integer to a two-digit string with leading zero if needed.
     * For example: 8 becomes "08", 19 remains "19".
     * 
     * @param d the integer value (typically day, month, or hour)
     * @return two-digit string representation
     */
    public static String getDigitalXX(int d) {
        return (d > 9 ? ("" + d) : ("0" + d));
    }

    /**
     * Converts a 24-hour time to 12-hour format without the AM/PM indicator.
     * For example: 18 becomes "06", 16 becomes "04", 10 becomes "10".
     * 
     * @param hour the hour in 24-hour format (0-23)
     * @return two-digit string in 12-hour format
     */
    public static String getTimeXXampm(int hour) {
        return (hour > 12 ? (getDigitalXX(hour - 12)) : getDigitalXX(hour));
    }

    /**
     * Returns "am" or "pm" based on the hour.
     * 
     * @param hour the hour in 24-hour format (0-23)
     * @return "am" for hours 0-11, "pm" for hours 12-23
     */
    public static String getTimeAMPM(int hour) {
        return (hour < 12 ? "am" : "pm");
    }

    /**
     * Extracts just the date portion from a datetime string.
     * For example: "2001-01-01 12:00:00" becomes "2001-01-01".
     * If there's no time component, returns the input unchanged.
     * 
     * @param aDate the datetime string
     * @return the date portion only, or empty string if aDate is null
     */
    public static String getMyStandardDate(java.lang.String aDate) {
        if (aDate == null) return "";
        if (aDate.indexOf(' ') < 0) {
            return aDate;
        } else {
            return aDate.substring(0, aDate.indexOf(' '));
        }
    }

    /**
     * Formats a Date object to MySQL standard date format (yyyy-MM-dd).
     * 
     * @param aDate the Date object to format
     * @return the formatted date string, or empty string if aDate is null
     */
    public static String getMyStandardDate(java.util.Date aDate) {
        if (aDate == null) return "";
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        return formatter.format(aDate);
    }

    /**
     * Formats a Calendar date to display format (yyyy/MM/dd).
     * 
     * @param cal the Calendar object to format
     * @return the formatted date string, or empty string if cal is null
     */
    public static String getStandardDate(Calendar cal) {
        if (cal == null) return "";
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
        return formatter.format(cal.getTime());
    }

    /**
     * Formats a Calendar datetime to display format (yyyy/MM/dd HH:mm:ss).
     * 
     * @param cal the Calendar object to format
     * @return the formatted datetime string, or empty string if cal is null
     */
    public static String getStandardDateTime(Calendar cal) {
        if (cal == null) return "";
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        return formatter.format(cal.getTime());
    }

    /**
     * Creates a MySQL standard date string from year, month, and day components.
     * For example: (2001, 2, 2) becomes "2001-02-02".
     * 
     * @param year the year (4 digits)
     * @param month the month (1-12)
     * @param day the day (1-31)
     * @return the formatted date string in MySQL format
     */
    public static String getMysqlStandardDate(int year, int month, int day) {
        return (year + "-" + getDigitalXX(month) + "-" + getDigitalXX(day));
    }

    /**
     * Creates a compact date string from year, month, and day components.
     * For example: (2001, 2, 2) becomes "20010202".
     * 
     * @param year the year (4 digits)
     * @param month the month (1-12)
     * @param day the day (1-31)
     * @return the formatted date string in compact format
     */
    public static String getStandardDate(int year, int month, int day) {
        return (year + getDigitalXX(month) + getDigitalXX(day));
    }

    /**
     * Extracts the day component from a standard date string.
     * For example: "2001-02-02" returns 2.
     * 
     * @param aDate the date string in standard format
     * @return the day value, or 0 if parsing fails
     */
    public static int getDayFromStandardDate(String aDate) {
        try {
            aDate = getMyStandardDate(aDate);
            return Integer.parseInt(aDate.trim().substring(aDate.trim().lastIndexOf('-') + 1));
        } catch (Exception e) {
            // bad string doesn't parse properly
            return (0);
        }
    }

    /**
     * Extracts the month component from a standard date string.
     * For example: "2001-02-15" returns 2.
     * 
     * @param aDate the date string in standard format
     * @return the month value (1-12), or 0 if parsing fails
     */
    public static int getMonthFromStandardDate(String aDate) {
        try {
            aDate = getMyStandardDate(aDate);
            return Integer.parseInt(aDate.trim().substring(aDate.trim().indexOf('-') + 1, aDate.trim().lastIndexOf('-')));
        } catch (Exception e) {
            // bad string doesn't parse properly
            return (0);
        }
    }

    /**
     * Extracts the year component from a standard date string.
     * For example: "2001-02-15" returns 2001.
     * 
     * @param aDate the date string in standard format
     * @return the year value, or 0 if parsing fails
     */
    public static int getYearFromStandardDate(String aDate) {
        try {
            aDate = getMyStandardDate(aDate);
            return Integer.parseInt(aDate.trim().substring(0, aDate.trim().indexOf('-')));
        } catch (Exception e) {
            // bad string doesn't parse properly
            return (0);
        }
    }

    /**
     * Extracts the hour from a standard time string.
     * If the string contains a space, extracts the time portion after the space.
     * 
     * @param aTime the time string (e.g., "14:30:00" or "2024-01-15 14:30:00")
     * @return the hour value (0-23)
     */
    public static int getHourFromStandardTime(String aTime) {
        int i = aTime.indexOf(' ');
        if (i >= 0) aTime = aTime.substring(i + 1);
        return Integer.parseInt(aTime.substring(0, 2));
    }

    /**
     * Converts a time string with optional AM/PM to 24-hour format (HH:mm:ss).
     * 
     * <p>Supports various input formats:</p>
     * <ul>
     *   <li>"8:20pm" becomes "20:20:00"</li>
     *   <li>"9:9am" becomes "09:09:00"</li>
     *   <li>"8:20" becomes "08:20:00"</li>
     *   <li>"12:30pm" becomes "12:30:00"</li>
     * </ul>
     * 
     * @param aXX_XXampm the time string with optional am/pm suffix
     * @return the time in 24-hour format (HH:mm:ss), or "\\N" (MySQL null) if parsing fails
     */
    public static String getTimeXX_XX_XX(String aXX_XXampm) {
        String temp = "\\N"; //mySQL = null
        int hour = 0;

        aXX_XXampm = aXX_XXampm.trim().toLowerCase();
        int i1 = aXX_XXampm.indexOf(58); //":" a s c i i is 58
        if (i1 > 0) {
            hour = Integer.parseInt(aXX_XXampm.substring(0, i1).trim());
            temp = aXX_XXampm.substring(i1 + 1); //temp xxam or xx
            //t2=aXX_XXampm.indexOf(58);
            if (aXX_XXampm.endsWith("am")) {
                temp = temp.substring(0, temp.length() - 2).trim();

                temp = getDigitalXX(hour) + ":" + getDigitalXX(Integer.parseInt(temp)) + ":00";
            } else if (aXX_XXampm.endsWith("pm")) {
                temp = temp.substring(0, temp.length() - 2).trim();
                //get rid of 12pm
                temp = getDigitalXX(hour == 12 ? 12 : (hour + 12)) + ":" + getDigitalXX(Integer.parseInt(temp)) + ":00";
            } else {
                temp = temp.trim();

                temp = getDigitalXX(hour) + ":" + getDigitalXX(Integer.parseInt(temp)) + ":00";
            }
        }
        return temp;
    }

    /**
     * Formats a Date object to system date string format (yyyy-MM-dd).
     * 
     * @param pDate the Date object to format
     * @return the formatted date string
     */
    public static String getSysDateString(java.util.Date pDate) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        return formatter.format(pDate);
    }

    /**
     * Parses a date string to a SQL Date object.
     * 
     * <p>Supports various date formats:</p>
     * <ul>
     *   <li>Special value "TODAY" returns current date</li>
     *   <li>Delimited formats: "yyyy-MM-dd", "yyyy/MM/dd", "dd-MM-yyyy", "dd/MM/yyyy"</li>
     *   <li>Compact format: "yyyyMMdd"</li>
     * </ul>
     * 
     * @param pDate the date string to parse
     * @return SQL Date object, or null if pDate is null/empty or parsing fails
     */
    public static java.sql.Date getSysDate(String pDate) {
        pDate = StringUtils.trimToNull(pDate);

        if (pDate == null) return null;

        if ("TODAY".equals(pDate.toUpperCase())) return new java.sql.Date(new Date().getTime());
        try {
            char sep = '-';
            boolean bnosep = false;
            int idx = pDate.indexOf(sep);
            if (idx < 0) {
                sep = '/';
                idx = pDate.indexOf(sep);
            }
            bnosep = idx < 0;
            int day, month, year;
            if (bnosep) {
                year = Integer.parseInt(pDate.substring(0, 4));
                month = Integer.parseInt(pDate.substring(4, 6));
                day = Integer.parseInt(pDate.substring(6, 8));
            } else {
                year = Integer.parseInt(pDate.substring(0, idx));
                int idx1 = pDate.indexOf(sep, idx + 1);
                month = Integer.parseInt(pDate.substring(idx + 1, idx1));
                idx = idx1;
                idx1 = pDate.indexOf(' ');
                if (idx1 < 0) idx1 = pDate.length();
                day = Integer.parseInt(pDate.substring(idx + 1, idx1));
            }
            if (month > 0) {
                month = month - 1;
            }
            GregorianCalendar cal = new GregorianCalendar(year, month, day, 0, 0, 0);
            return new java.sql.Date(cal.getTime().getTime());
        } catch (Exception e) {
            MiscUtils.getLogger().debug("Invalid Date - the input date is in wrong format or out of range");
            return null;
        }
    }

    public static java.sql.Date getSysTime(String pDate) {
        pDate = StringUtils.trimToNull(pDate);

        if (pDate == null) return null;

        if (pDate.indexOf(":") == -1) {
            return null;
        }

        String parts[] = pDate.split(":");

        if (parts.length != 2) {
            return null;
        }

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
        c.set(Calendar.MINUTE, Integer.parseInt(parts[1]));

        return new java.sql.Date(c.getTime().getTime());
    }

    public static java.sql.Date dayEnd(String pDate) {
        if (pDate == null || "".equals(pDate)) return null;
        try {
            char sep = '-';
            boolean bnosep = false;
            int idx = pDate.indexOf(sep);
            if (idx < 0) {
                sep = '/';
                idx = pDate.indexOf(sep);
            }
            bnosep = idx < 0;
            int day, month, year;
            if (bnosep) {
                year = Integer.parseInt(pDate.substring(0, 4));
                month = Integer.parseInt(pDate.substring(4, 6));
                day = Integer.parseInt(pDate.substring(6, 8));
            } else {
                year = Integer.parseInt(pDate.substring(0, idx));
                int idx1 = pDate.indexOf(sep, idx + 1);
                month = Integer.parseInt(pDate.substring(idx + 1, idx1));
                idx = idx1;
                idx1 = pDate.indexOf(' ');
                if (idx1 < 0) idx1 = pDate.length();
                day = Integer.parseInt(pDate.substring(idx + 1, idx1));
            }
            if (month > 0) {
                month = month - 1;
            }
            GregorianCalendar cal = new GregorianCalendar(year, month, day, 23, 59, 59);
            return new java.sql.Date(cal.getTime().getTime());
        } catch (Exception e) {
            MiscUtils.getLogger().debug("Invalid Date - the input date is in wrong format or out of range");
            return null;
        }
    }

    //yyyy-mm-dd hh:mm:ss
    public static Calendar getCalendarwithTime(String pDate) {
        pDate = pDate.replace('-', '/');
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        try {
            Date date = formatter.parse(pDate);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isBefore(Calendar cal1, Calendar cal2) {
        String str1 = getStandardDate(cal1);
        String str2 = getStandardDate(cal2);

        return str1.compareTo(str2) < 0;
    }

    public static Calendar getCalendar(java.util.Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal;
    }

    public static Calendar getCalendar(String pDate, String dateFormat) {
        if (pDate == null || "".equals(pDate)) return null;
        GregorianCalendar cal = null;
        int day, month, year;
        if (KeyConstants.DATE_YYYYMMDD.equals(dateFormat.toUpperCase()) ||
                KeyConstants.DATE_YYYYMMDDHHMM.equals(dateFormat.toUpperCase()))
            return getCalendar(pDate);
        else if (KeyConstants.DATE_DDMMYYYY.equals(dateFormat.toUpperCase())) {
            year = Integer.parseInt(pDate.substring(4, 8));
            month = Integer.parseInt(pDate.substring(2, 4));
            day = Integer.parseInt(pDate.substring(0, 2));
            if (month > 0) {
                month = month - 1;
            }
            cal = new GregorianCalendar(year, month, day);
        } else if (KeyConstants.DATE_MMDDYYYY.equals(dateFormat.toUpperCase())) {
            year = Integer.parseInt(pDate.substring(4, 8));
            day = Integer.parseInt(pDate.substring(2, 4));
            month = Integer.parseInt(pDate.substring(0, 2));
            if (month > 0) {
                month = month - 1;
            }
            cal = new GregorianCalendar(year, month, day);
        }
        return cal;
    }

    public static Calendar getCalendar(String pDate) {
        //date format yyyymmddHHMM or yyyymmdd or yyyy/mm/dd or yyyy-mm-dd
        if (pDate == null || "".equals(pDate)) return null;
        if ("TODAY".equals(pDate.toUpperCase())) return Calendar.getInstance();
        int len = pDate.length();
        try {
            char sep = '-';
            boolean bnosep = false;
            int idx = pDate.indexOf(sep);
            if (idx < 0) {
                sep = '/';
                idx = pDate.indexOf(sep);
            }
            bnosep = idx < 0;
            int day, month, year;
            int hour = 0, min = 0;
            if (bnosep && len == 8) {
                year = Integer.parseInt(pDate.substring(0, 4));
                month = Integer.parseInt(pDate.substring(4, 6));
                day = Integer.parseInt(pDate.substring(6, 8));
            } else if (bnosep && len == 12) {
                year = Integer.parseInt(pDate.substring(0, 4));
                month = Integer.parseInt(pDate.substring(4, 6));
                day = Integer.parseInt(pDate.substring(6, 8));
                hour = Integer.parseInt(pDate.substring(8, 10));
                min = Integer.parseInt(pDate.substring(10, 12));
            } else {
                year = Integer.parseInt(pDate.substring(0, idx));
                int idx1 = pDate.indexOf(sep, idx + 1);
                month = Integer.parseInt(pDate.substring(idx + 1, idx1));
                idx = idx1;
                idx1 = pDate.indexOf(' ');
                if (idx1 < 0) idx1 = pDate.length();
                day = Integer.parseInt(pDate.substring(idx + 1, idx1));
            }
            if (month > 0) {
                month = month - 1;
            }
            GregorianCalendar cal = null;
            if (hour > 0) cal = new GregorianCalendar(year, month, day, hour, min);
            else cal = new GregorianCalendar(year, month, day);
            return cal;
        } catch (Exception e) {
            MiscUtils.getLogger().debug("Invalid Date - the input date is in wrong format or out of range");
            return null;
        }
    }

    public static java.sql.Date getSysDateEX(String pDate, int days) {
        if (pDate == null || "".equals(pDate)) return null;
        if ("TODAY".equals(pDate.toUpperCase())) return new java.sql.Date(new Date().getTime());
        try {
            char sep = '-';
            boolean bnosep = false;
            int idx = pDate.indexOf(sep);
            if (idx < 0) {
                sep = '/';
                idx = pDate.indexOf(sep);
            }
            bnosep = idx < 0;
            int day, month, year;
            if (bnosep) {
                year = Integer.parseInt(pDate.substring(0, 4));
                month = Integer.parseInt(pDate.substring(4, 6));
                day = Integer.parseInt(pDate.substring(6, 8));
            } else {
                year = Integer.parseInt(pDate.substring(0, idx));
                int idx1 = pDate.indexOf(sep, idx + 1);
                month = Integer.parseInt(pDate.substring(idx + 1, idx1));
                idx = idx1;
                idx1 = pDate.indexOf(' ');
                if (idx1 < 0) idx1 = pDate.length();
                day = Integer.parseInt(pDate.substring(idx + 1, idx1));
            }
            if (month > 0) {
                month = month - 1;
            }
            GregorianCalendar cal = new GregorianCalendar(year, month, day);
            cal.add(Calendar.DAY_OF_YEAR, days);
            return new java.sql.Date(cal.getTime().getTime());
        } catch (Exception e) {
            MiscUtils.getLogger().debug("Invalid Date - the input date is in wrong format or out of range");
            return null;
        }
    }

    public static java.sql.Date getCurrentDate() {
        GregorianCalendar cal = new GregorianCalendar();
        return new java.sql.Date(cal.getTime().getTime());
    }

    //from  20:20:00to 08:20pm,  09:09:00 to 09:09am, or 20:20 to 08:20pm
    public static String getTimeXX_XXampm(String aXX_XX_XX) {
        String temp = null; //mySQL = null
        int hour = 0;

        aXX_XX_XX = aXX_XX_XX.trim().toLowerCase();
        int i1 = aXX_XX_XX.indexOf(58); //":" a s c i i is 58
        if (i1 > 0) {
            hour = Integer.parseInt(aXX_XX_XX.substring(0, i1).trim());
            temp = aXX_XX_XX.substring(i1 + 1).trim(); //temp XX:XX or x:xx
            temp = getTimeXXampm(hour) + ":" + temp.substring(0, 2) + getTimeAMPM(hour);
        }
        return temp;
    }

    public static Calendar getDayStart(Calendar pDate) {
        int year = pDate.get(Calendar.YEAR);
        int month = pDate.get(Calendar.MONTH);
        int day = pDate.get(Calendar.DATE);
        return new GregorianCalendar(year, month, day, 0, 0, 0);
    }

    public static Calendar getDayEnd(Calendar pDate) {
        int year = pDate.get(Calendar.YEAR);
        int month = pDate.get(Calendar.MONTH);
        int day = pDate.get(Calendar.DATE);
        return new GregorianCalendar(year, month, day, 23, 59, 59);
    }

    public static int getAge(int year, int month, int date) {
        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = (now.get(Calendar.MONTH) + 1);
        int curDay = now.get(Calendar.DAY_OF_MONTH);
        int age = 0;

        if (curMonth > month || (curMonth == month && curDay >= date)) {
            age = curYear - year;
        } else {
            age = curYear - year - 1;
        }
        return age;
    }

    public static int getAge(String year1, String month1, String date1) {
        int year = Integer.parseInt(year1);
        int month = Integer.parseInt(month1);
        int date = Integer.parseInt(date1);
        int age = MyDateFormat.getAge(year, month, date);
        return age;
    }

    public static String formatMonthDay(String pValue) {
        if (pValue == null) return null;

        if (pValue.length() == 1) {
            return "0" + pValue;
        } else {
            return pValue;
        }
    }

}
