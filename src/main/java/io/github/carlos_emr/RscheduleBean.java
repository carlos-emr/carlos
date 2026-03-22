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


package io.github.carlos_emr;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.StringTokenizer;

/**
 * Bean representing a recurring schedule configuration for providers.
 * 
 * <p>This bean manages provider recurring schedule information including:</p>
 * <ul>
 *   <li>Provider identification and creator tracking</li>
 *   <li>Schedule date range (start date to end date)</li>
 *   <li>Days of week specification</li>
 *   <li>Available hours configuration</li>
 *   <li>Active/inactive status</li>
 * </ul>
 * 
 * <p>The bean supports multiple day-of-week formats including:</p>
 * <ul>
 *   <li>Standard weekday tags: SUN, MON, TUE, WED, THU, FRI, SAT</li>
 *   <li>Site-specific day tags: A7, A1, A2, A3, A4, A5, A6</li>
 * </ul>
 */
public class RscheduleBean {

    /** Provider number */
    public String provider_no = "";
    /** Schedule start date */
    public String sdate = "";
    /** Schedule end date */
    public String edate = "";
    /** Availability indicator */
    public String available = "";
    /** Day of week specification */
    public String day_of_week = "";
    /** Available hours begin */
    public String avail_hourB = "";
    /** Available hours */
    public String avail_hour = "";
    /** Creator identifier */
    public String creator = "";
    /** Active status (A = active) */
    public String active = "A";
    
    /** Standard weekday tag names */
    private String weekdaytag[] = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    /** Site-specific day tag names */
    private String sitedaytag[] = {"A7", "A1", "A2", "A3", "A4", "A5", "A6"};

    /**
     * Constructs a new empty RscheduleBean.
     */
    public RscheduleBean() {
    }

    /**
     * Constructs a new RscheduleBean with all schedule properties.
     * 
     * @param provider_no1 the provider number
     * @param sdate1 the start date
     * @param edate1 the end date
     * @param bAvailable1 the availability indicator
     * @param day_of_week1 the day of week specification
     * @param avail_hourB1 the beginning of available hours
     * @param avail_hour1 the available hours
     * @param creator1 the creator identifier
     */
    public RscheduleBean(String provider_no1, String sdate1, String edate1, String bAvailable1, String day_of_week1, String avail_hourB1, String avail_hour1, String creator1) {
        provider_no = provider_no1;
        sdate = sdate1;
        edate = edate1;
        available = bAvailable1;
        day_of_week = day_of_week1;
        avail_hourB = avail_hourB1;
        avail_hour = avail_hour1;
        creator = creator1;
    }

    /**
     * Sets all schedule properties.
     * 
     * @param provider_no1 the provider number
     * @param sdate1 the start date
     * @param edate1 the end date
     * @param bAvailable1 the availability indicator
     * @param day_of_week1 the day of week specification
     * @param avail_hourB1 the beginning of available hours
     * @param avail_hour1 the available hours
     * @param creator1 the creator identifier
     */
    public void setRscheduleBean(String provider_no1, String sdate1, String edate1, String bAvailable1, String day_of_week1, String avail_hourB1, String avail_hour1, String creator1) {
        provider_no = provider_no1;
        sdate = sdate1;
        edate = edate1;
        available = bAvailable1;
        day_of_week = day_of_week1;
        avail_hourB = avail_hourB1;
        avail_hour = avail_hour1;
        creator = creator1;
    }

    /**
     * Sets all schedule properties with two separate day-of-week specifications.
     * The day specifications are combined with a pipe (|) separator.
     * 
     * @param provider_no1 the provider number
     * @param sdate1 the start date
     * @param edate1 the end date
     * @param bAvailable1 the availability indicator
     * @param day_of_week1 the first day of week specification
     * @param day_of_week2 the second day of week specification
     * @param avail_hourB1 the beginning of available hours
     * @param avail_hour1 the available hours
     * @param creator1 the creator identifier
     */
    public void setRscheduleBean(String provider_no1, String sdate1, String edate1, String bAvailable1, String day_of_week1, String day_of_week2, String avail_hourB1, String avail_hour1, String creator1) {
        provider_no = provider_no1;
        sdate = sdate1;
        edate = edate1;
        available = bAvailable1;
        day_of_week = day_of_week1 + "|" + day_of_week2;
        avail_hourB = avail_hourB1;
        avail_hour = avail_hour1;
        creator = creator1;
    }

    /**
     * Resets all schedule properties to empty strings.
     */
    public void clear() {
        provider_no = "";
        sdate = "";
        edate = "";
        available = "";
        day_of_week = "";
        avail_hourB = "";
        avail_hour = "";
        creator = "";
    }

    /**
     * Determines whether the given date falls on an even week relative to the schedule start date.
     *
     * <p>Used for alternating week schedules to determine which week pattern applies.</p>
     *
     * @param aDate GregorianCalendar the date to check
     * @return boolean true if the date is in an even week relative to the start date
     */
    public boolean getEvenWeek(GregorianCalendar aDate) {
        int sWeek = (new GregorianCalendar(MyDateFormat.getYearFromStandardDate(this.sdate), MyDateFormat.getMonthFromStandardDate(this.sdate) - 1, MyDateFormat.getDayFromStandardDate(this.sdate))).get(Calendar.WEEK_OF_YEAR);
        int curWeek = aDate.get(Calendar.WEEK_OF_YEAR);
        return ((curWeek - sWeek) % 2 == 0 ? true : false);
    }

    /**
     * Determines whether the given date falls on an even week relative to the specified start date.
     *
     * @param sDate GregorianCalendar the reference start date
     * @param aDate GregorianCalendar the date to check
     * @return boolean true if the date is in an even week relative to the start date
     */
    private boolean getEvenWeek(GregorianCalendar sDate, GregorianCalendar aDate) {
        int sWeek = sDate.get(Calendar.WEEK_OF_YEAR);
        int curWeek = aDate.get(Calendar.WEEK_OF_YEAR);
        return ((curWeek - sWeek) % 2 == 0 ? true : false);
    }

    /**
     * Checks whether the provider is available on the given date.
     *
     * <p>For alternating week schedules (available="A"), selects the appropriate
     * day-of-week pattern based on whether the date falls in an even or odd week.</p>
     *
     * @param aDate GregorianCalendar the date to check availability for
     * @return boolean true if the provider is available on the given date
     */
    public boolean getDateAvail(GregorianCalendar aDate) {
        String aVailable = null, aDOW = null;
        if (this.available.compareTo("A") == 0) {
            aVailable = "A";
            if (getEvenWeek(new GregorianCalendar(MyDateFormat.getYearFromStandardDate(this.sdate), MyDateFormat.getMonthFromStandardDate(this.sdate) - 1, MyDateFormat.getDayFromStandardDate(this.sdate)), aDate)) {
                aDOW = this.day_of_week.substring(0, this.day_of_week.indexOf("|"));
            } else aDOW = this.day_of_week.substring(this.day_of_week.indexOf("|") + 1);

        } else {
            aVailable = this.available;
            aDOW = this.day_of_week;
        }
        return (getSingleDateAvail(aDate, aVailable, aDOW));
    }

    /**
     * Checks availability for a single date against a specific availability flag and day-of-week set.
     *
     * <p>Tokenizes the day-of-week string and checks if the given date's day matches
     * any of the specified days. The availability flag determines the default and
     * matched states (e.g., "1" or "A" means default unavailable, match = available).</p>
     *
     * @param aDate GregorianCalendar the date to check
     * @param aVailable String the availability flag ("0", "1", or "A")
     * @param aDOW String space-separated day-of-week numbers
     * @return boolean true if the provider is available on the given date
     */
    public boolean getSingleDateAvail(GregorianCalendar aDate, String aVailable, String aDOW) {
        boolean bAvail = (aVailable.compareTo("1") == 0 || aVailable.compareTo("A") == 0) ? false : true;
        boolean bAvailableTemp = (aVailable.compareTo("0") == 0) ? false : true;

        //check if it is unavailable, then break
        StringTokenizer st = new StringTokenizer(aDOW);
        while (st.hasMoreTokens()) {
            if (st.nextToken().compareTo("" + aDate.get(Calendar.DAY_OF_WEEK)) == 0) { //prompt the number, from 0?

                bAvail = bAvailableTemp;
                break;
            }
        }
/*
    //check if it is a special day of month, then modify the status
    st = new StringTokenizer(this.avail_hourB);
    while (st.hasMoreTokens() ) {
      if( st.nextToken().compareTo(""+ aDate.get(Calendar.avail_hourB) )==0 ) { 
    	  bAvail = bAvailableTemp;
    	  break;
      }
    }
*/
        return bAvail;
    }

    /**
     * Checks whether the provider is available on the given date string.
     *
     * @param aDate String the date in standard format (yyyy-MM-dd)
     * @return boolean true if the provider is available on the given date
     */
    public boolean getDateAvail(String aDate) {
        return (getDateAvail(new GregorianCalendar(MyDateFormat.getYearFromStandardDate(aDate), MyDateFormat.getMonthFromStandardDate(aDate) - 1, MyDateFormat.getDayFromStandardDate(aDate))));
    }

    /**
     * Gets the available hour template name for the given date's day of week.
     *
     * <p>Extracts the schedule template name from the XML-formatted {@code avail_hour}
     * or {@code avail_hourB} field using standard weekday tags (SUN, MON, etc.),
     * selecting the appropriate week for alternating schedules.</p>
     *
     * @param aDate GregorianCalendar the date to look up the hour template for
     * @return String the template name for the given day, or empty string if none
     */
    public String getDateAvailHour(GregorianCalendar aDate) {
        String val = "";
        if (provider_no != "") {
            int j = aDate.get(Calendar.DAY_OF_WEEK) - 1;
            int i = j == 7 ? 0 : j;
            if (this.available.compareTo("A") == 0) {
                if (getEvenWeek(new GregorianCalendar(MyDateFormat.getYearFromStandardDate(this.sdate), MyDateFormat.getMonthFromStandardDate(this.sdate) - 1, MyDateFormat.getDayFromStandardDate(this.sdate)), aDate)) {
                    val = SxmlMisc.getXmlContent(avail_hour, ("<" + weekdaytag[i] + ">"), "</" + weekdaytag[i] + ">");
                } else
                    val = SxmlMisc.getXmlContent(avail_hourB, ("<" + weekdaytag[i] + ">"), "</" + weekdaytag[i] + ">");
            } else val = SxmlMisc.getXmlContent(avail_hour, ("<" + weekdaytag[i] + ">"), "</" + weekdaytag[i] + ">");
        }
        return val;
    }

    /**
     * Gets the site availability value for the given date's day of week.
     *
     * <p>Similar to {@link #getDateAvailHour(GregorianCalendar)} but uses
     * site-specific day tags (A7, A1, A2, etc.) instead of standard weekday tags.</p>
     *
     * @param aDate GregorianCalendar the date to look up site availability for
     * @return String the site availability value, or empty string if none
     */
    public String getSiteAvail(GregorianCalendar aDate) {
        String val = "";
        if (provider_no != "") {
            int j = aDate.get(Calendar.DAY_OF_WEEK) - 1;
            int i = j == 7 ? 0 : j;
            if (this.available.compareTo("A") == 0) {
                if (getEvenWeek(new GregorianCalendar(MyDateFormat.getYearFromStandardDate(this.sdate), MyDateFormat.getMonthFromStandardDate(this.sdate) - 1, MyDateFormat.getDayFromStandardDate(this.sdate)), aDate)) {
                    val = SxmlMisc.getXmlContent(avail_hour, ("<" + sitedaytag[i] + ">"), "</" + sitedaytag[i] + ">");
                } else
                    val = SxmlMisc.getXmlContent(avail_hourB, ("<" + sitedaytag[i] + ">"), "</" + sitedaytag[i] + ">");
            } else val = SxmlMisc.getXmlContent(avail_hour, ("<" + sitedaytag[i] + ">"), "</" + sitedaytag[i] + ">");
        }
        return val;
    }

    /**
     * Gets the available hour template name for the given date string's day of week.
     *
     * @param aDate String the date in standard format (yyyy-MM-dd)
     * @return String the template name for the given day, or empty string if none
     */
    public String getDateAvailHour(String aDate) {
        return (getDateAvailHour(new GregorianCalendar(MyDateFormat.getYearFromStandardDate(aDate), MyDateFormat.getMonthFromStandardDate(aDate) - 1, MyDateFormat.getDayFromStandardDate(aDate))));
    }

    /**
     * Gets the full available hours XML string for the given date.
     *
     * <p>For alternating schedules, returns either {@code avail_hour} or
     * {@code avail_hourB} depending on whether the date falls in an even or odd week.</p>
     *
     * @param aDate GregorianCalendar the date to determine the hours for
     * @return String the full XML-formatted available hours string
     */
    public String getAvailHour(GregorianCalendar aDate) {
        String val = "";
        if (this.available.compareTo("A") == 0) {
            if (getEvenWeek(aDate)) {
                val = avail_hour;
            } else val = avail_hourB;
        } else val = avail_hour;
        return val;
    }

}
