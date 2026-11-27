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


package io.github.carlos_emr.carlos.prevention;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;

/**
 * @author Jay Gallagher
 */
public class Prevention {
    private static Logger log = MiscUtils.getLogger();

    String sex;
    String name = null;  // Not really needed but handy for testing
    Hashtable preventionTypes = new Hashtable();
    Date DOB = null;

    Map<String, Object> warnings = new HashMap<String, Object>();

    ArrayList<String> messageList = new ArrayList<String>();
    ArrayList<String> reminder = new ArrayList<String>();

    int ageInMonths = -1;

    public Prevention() {
    }

    public Prevention(String nam, String se, Date birthdate) {
        name = nam;
        sex = se;
        DOB = birthdate;
    }

    public Prevention(String se, Date birthdate) {
        sex = se;
        DOB = birthdate;
    }

    public Prevention(String demographicNo) {

    }

    public void log(String logMessage) {
        log.debug(name + " :" + logMessage);
    }

    public void setSex(String s) {
        sex = s;
    }

    public String getSex() {
        return sex;
    }

    public java.lang.String getName() {
        return name;
    }

    public void setName(java.lang.String name) {
        this.name = name;
    }

    public void addWarning(String warn) {
        messageList.add(warn);
    }

    public void addWarning(String prevName, String warn) {
        addWarning(warn);
        warnings.put(prevName, warn);
    }

    public ArrayList<String> getWarnings() {
        return messageList;
    }

    @SuppressWarnings("rawtypes")
    public Map getWarningMsgs() {
        return warnings;
    }

    public void addReminder(String warn) {
        reminder.add(warn);
    }

    public ArrayList<String> getReminder() {
        return reminder;
    }

    public boolean isMale() {
        boolean retval = false;
        if (sex != null && sex.equals("M")) {
            retval = true;
        }
        return retval;
    }

    public boolean isFemale() {
        boolean retval = false;
        if (sex != null && sex.equals("F")) {
            retval = true;
        }
        return retval;
    }

    public void addPreventionItem(PreventionItem pItem) {
        if (preventionTypes.containsKey(pItem.name)) {
            Vector v = (Vector) preventionTypes.get(pItem.name);
            v.add(pItem);
        } else {
            Vector v = new Vector();
            v.add(pItem);
            preventionTypes.put(pItem.name, v);
        }
    }

    /** A Date-only rule set cannot prove timing, spacing or latest status for this history. */
    public boolean isDateReviewRequired() {
        for (Object value : preventionTypes.values()) {
            for (Object item : (Vector) value) {
                if (((PreventionItem) item).requiresDateReview()) return true;
            }
        }
        return false;
    }

    public boolean isDateReviewRequired(String preventionType) {
        Vector items = (Vector) preventionTypes.get(preventionType);
        if (items != null) {
            for (Object item : items) {
                if (((PreventionItem) item).requiresDateReview()) return true;
            }
        }
        return false;
    }

    private void requireExactHistory(String preventionType) {
        if (isDateReviewRequired(preventionType)) throw new PreventionDatePrecisionException();
    }

    public void addDateReviewWarnings() {
        for (Object key : preventionTypes.keySet()) {
            Vector items = (Vector) preventionTypes.get(key);
            for (Object item : items) {
                if (((PreventionItem) item).requiresDateReview()) {
                    addWarning((String) key, "Prevention timing is partial or unknown; review history before applying due or dose rules.");
                    break;
                }
            }
        }
    }

    public int getAgeInMonths(Date DOB) {
        if (DOB != null)
            return getNumMonths(DOB, Calendar.getInstance().getTime());
        else
            return 0;
    }

    public int getAgeInMonths() {
        if (ageInMonths == -1) {
            ageInMonths = getAgeInMonths(DOB);
        }
        return ageInMonths;
    }

    public int getAgeInYears() {
        if (DOB != null)
            return getNumYears(DOB, Calendar.getInstance().getTime());
        else
            return 0;
    }

    public boolean isTodayinDateRange(String startDate, String endDate) {
        boolean inRange = false;
        Calendar calendar = Calendar.getInstance();
        Date today = calendar.getTime();
        try {
            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            Date startd = formatter.parse(startDate);
            Date endd = formatter.parse(endDate);

            if (today.after(startd) && today.before(endd)) {
                inRange = true;
            }
        } catch (ParseException e) {
        }
        return inRange;
    }

    public boolean isLastPreventionWithinRange(String preventionType, String startDate, String endDate) {
        requireExactHistory(preventionType);
        boolean withinRange = false;
        Date lastdate = getLastPreventionDate(preventionType);
        try {
            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            Date startd = formatter.parse(startDate);
            Date endd = formatter.parse(endDate);

            if (lastdate != null && lastdate.after(startd) && lastdate.before(endd)) {
                withinRange = true;
            }
        } catch (ParseException e) {
            MiscUtils.getLogger().error("Error", e);
        } catch (Exception ex) {
            MiscUtils.getLogger().error("Error", ex);
        }
        return withinRange;
    }

    ////
    public Date getLastPreventionDate(String preventionType) {
        //Still needs to be implemented
        Date lastDate = null;
        Vector vec = (Vector) preventionTypes.get(preventionType);
        if (vec != null) {
            for (Object item : vec) {
                // A synthetic anchor cannot establish which event happened last.
                if (((PreventionItem) item).requiresDateReview()) return null;
            }
            PreventionItem p = (PreventionItem) vec.get(vec.size() - 1);  // Get date from return object
            lastDate = p.getDatePreformed();
            log.debug("getting last date preformed of a " + preventionType + " :" + lastDate);
        }

        return lastDate; //
    }

    public boolean isNextDateSet(String preventionType) {
        boolean isSet = true;
        Date nextDate = getNextPreventionDate(preventionType);
        log.debug("IS SET WHAT DOES IT HAVE " + nextDate);
        if (nextDate == null) {
            isSet = false;
        }
        return isSet;
    }

    public boolean isNotNextDateSet(String preventionType) {
        return !isNextDateSet(preventionType);
    }

    public boolean isPassedNextDate(String preventionType) {
        boolean isPassed = true;
        Date nextDate = getNextPreventionDate(preventionType);
        log.debug(nextDate);
        if (nextDate != null) {
            Calendar cal = Calendar.getInstance();
            if (!cal.getTime().after(nextDate)) {
                isPassed = false;
            }
        }
        return isPassed;
    }

    public boolean isNotPassedNextDate(String preventionType) {
        return !isPassedNextDate(preventionType);
    }

    public Date getNextPreventionDate(String preventionType) {
        requireExactHistory(preventionType);
        Date nextDate = null;
        Vector vec = (Vector) preventionTypes.get(preventionType);
        if (vec != null) {
            PreventionItem p = (PreventionItem) vec.get(vec.size() - 1);  // Get date from return object
            nextDate = p.getNextDate();
            log.debug("getting next date preformed of a " + preventionType + " :" + nextDate);
        }
        return nextDate; //
    }

    public boolean isPreventionNever(String preventionType) {
        requireExactHistory(preventionType);
        boolean ispreventionnever = false;
        Vector vec = (Vector) preventionTypes.get(preventionType);
        if (vec != null) {
            PreventionItem p = (PreventionItem) vec.get(vec.size() - 1);  // Get date from return object
            ispreventionnever = p.getNeverVal();
            log.debug("getting never of a " + preventionType + " :" + ispreventionnever);
        }
        return ispreventionnever; //
    }

    public boolean isNotPreventionNever(String preventionType) {
        return !isPreventionNever(preventionType);
    }


    public boolean isInelligible(String preventionType) {
        boolean isInelligible = false;
        Vector vec = getPreventionData(preventionType);
        PreventionItem p;
        for (int idx = 0; idx < vec.size(); ++idx) {
            p = (PreventionItem) vec.get(idx);
            if (p.isInelligible()) {
                isInelligible = true;
                break;
            }
        }

        return isInelligible;
    }

    public boolean isNotInelligible(String preventionType) {
        return !isInelligible(preventionType);
    }

    /**
     * Returns the raw refused status value for the most recent prevention record of the given type.
     * <p>
     * Standard semantics: {@code 0}=active/completed, {@code 1}=refused,
     * {@code 2}=ineligible, {@code 3}=completed externally.
     * <p>
     * For the "Smoking" prevention type, the refused field is repurposed to encode
     * smoking history status:
     * <ul>
     *   <li>{@code 0} - Currently smoking ("Yes")</li>
     *   <li>{@code 1} - Not smoking ("No")</li>
     *   <li>{@code 2} - Previously smoked ("Previous")</li>
     * </ul>
     * <p>
     * <b>Important note on {@code isPreventionNever("Smoking")}:</b> The {@code never}
     * column in the preventions table is separate from the {@code refused} column and is
     * always {@code 0} for all three smoking states. Therefore {@code isPreventionNever}
     * cannot be used to detect non-smokers; use {@code isCurrentlySmoking} instead.
     *
     * @param preventionType String the prevention type key (e.g. "Smoking", "LDCT")
     * @return int the raw refused status value, or {@code -1} if no record exists
     * @since 2026-03-14
     */
    public int getRefusedStatus(String preventionType) {
        int status = -1;
        Vector vec = (Vector) preventionTypes.get(preventionType);
        if (vec != null && !vec.isEmpty()) {
            for (Object item : vec) {
                if (((PreventionItem) item).requiresDateReview()) return -1;
            }
            PreventionItem p = (PreventionItem) vec.get(vec.size() - 1);
            status = p.getRefusedStatus();
        }
        return status;
    }

    /**
     * Returns {@code true} if the most recent prevention record for the given type indicates
     * a "currently active" or "yes" response (refused status = 0).
     * <p>
     * For the "Smoking" prevention type specifically, this returns {@code true} when the
     * patient is a current smoker. The Smoking assessment stores its answer in the
     * {@code refused} field: {@code 0}=Yes (current smoker), {@code 1}=No (non-smoker),
     * {@code 2}=Previous (ex-smoker).
     * <p>
     * Use this method instead of {@code !isPreventionNever("Smoking")} for LDCT eligibility
     * checks. The {@code never} column is always {@code 0} for all smoking states and cannot
     * distinguish current smokers from non-smokers.
     *
     * @param preventionType String the prevention type key; must be {@code "Smoking"} —
     *                       returns {@code false} for any other type to prevent semantic misuse
     * @return boolean {@code true} if preventionType is "Smoking" and the most recent record
     *         has refused status = 0 (current smoker)
     * @since 2026-03-14
     */
    public boolean isCurrentlySmoking(String preventionType) {
        if (!"Smoking".equals(preventionType)) {
            return false;
        }
        requireExactHistory(preventionType);
        return getRefusedStatus(preventionType) == 0;
    }


    public int getHowManyMonthsSinceLast(String preventionType) {
        int retval = -1;
        try {
            retval = getNumMonths(getLastPreventionDate(preventionType), Calendar.getInstance().getTime());
        } catch (Exception e) {
            log.debug("Probably no record of this prevention");
            log.debug(e.getMessage(), e);
            retval = -1;
        }
        return retval;
    }

    public int getHowManyDaysSinceLast(String preventionType) {
        return getNumDays(getLastPreventionDate(preventionType), Calendar.getInstance().getTime());
    }

    /**
     * Whole calendar days from the date of the last {@code preventionType} record to today, in the server's time
     * zone. Unlike {@link #getHowManyDaysSinceLast(String)}, which divides elapsed milliseconds by 24 hours, neither
     * the time of day the record was saved with nor a daylight saving change in between moves it: a dose dated the
     * 1st is 28 days old from the 29th (#5048).
     *
     * @param preventionType String the prevention type key (e.g. "Rota-1")
     * @return int the day count, negative for a record dated in the future, or {@code -1} when there is no usable
     *         record; a rule that must tell "none" from "tomorrow" checks {@link #getNumberOfPreventionType(String)}
     * @since 2026-11-12
     */
    public int getCalendarDaysSinceLast(String preventionType) {
        Date last = getLastPreventionDate(preventionType);
        if (last == null) {
            return -1;
        }
        ZoneId zone = ZoneId.systemDefault();
        return calendarDaysBetween(last, LocalDate.now(zone), zone);
    }

    /**
     * Whole calendar days from the date {@code given} falls on in {@code zone} to {@code today}. It subtracts two
     * calendar dates, so the time of day {@code given} carries and a daylight saving change in {@code zone}
     * between the two dates cannot add or remove a day (a count of elapsed hours divided by 24 can). Public and
     * static so that a test can fix both the dates and the zone (#5048).
     *
     * @param given Date the moment the record was dated with (may carry a time of day)
     * @param today LocalDate the date to count to
     * @param zone ZoneId the time zone whose calendar {@code given} is read in
     * @return int the day count, negative when {@code given} falls after {@code today}
     */
    public static int calendarDaysBetween(Date given, LocalDate today, ZoneId zone) {
        LocalDate day = Instant.ofEpochMilli(given.getTime()).atZone(zone).toLocalDate();
        return Math.toIntExact(ChronoUnit.DAYS.between(day, today));
    }

    /**
     * Whole calendar months from {@code from} to {@code to}, counted without drift: month {@code n} is reached on
     * {@code from.plusMonths(n)}, which for a day the later month does not have (the 29th to the 31st) is that
     * month's last day, so a dose given on 31 January is one month old on 28 or 29 February and exactly ten years
     * old on 31 January ten years later; one given on 29 February is ten years old on 28 February. The legacy
     * {@code getNumMonths} adds a month at a time to the same date, so after a short month the day stays at the
     * 28th and a dose or birth on the 29th to the 31st reaches a threshold up to three days early; it is left as it
     * is (it moves every existing reminder), and only the rules written for #5048 use this count.
     *
     * @param from LocalDate the start date (a dose date)
     * @param to LocalDate the end date (today)
     * @return int the whole months, never negative (0 when {@code to} is before {@code from})
     */
    public static int calendarMonthsBetween(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return 0;
        }
        int months = (int) ChronoUnit.MONTHS.between(from, to);
        // ChronoUnit.MONTHS counts a month complete only on the same day number; a month with fewer days
        // completes on its last day.
        while (!from.plusMonths(months + 1L).isAfter(to)) {
            months++;
        }
        return months;
    }

    public int getNumberOfPreventionType(String preventionType) {
        int retval = 0;
        Vector vec = (Vector) preventionTypes.get(preventionType);
        if (vec != null) {
            retval = vec.size();
        }
        return retval;
    }

    public Vector getPreventionData(String preventionType) {
        Vector a = (Vector) preventionTypes.get(preventionType);
        if (a == null) {
            a = new Vector();
        }
        return a;
    }

    public int getAgeInMonthsLastPreventionTypeGiven(String preventionType) {
        return getNumMonths(DOB, getLastPreventionDate(preventionType));
    }

    private String getStrDate(Date d) {
        if (d == null) {
            return null;
        }
        return d.toString();
    }

    private int getNumMonths(Date dStart, Date dEnd) {
        if (dStart == null || dEnd == null)
            return -1;
        int i = 0;
        log.debug("Getting the number of months between " + getStrDate(dStart) + " and " + getStrDate(dEnd));
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

    private int getNumMonths(Calendar dStart, Calendar dEnd) {
        return getNumMonths(dStart.getTime(), dEnd.getTime());
    }

    private int getNumYears(Date dStart, Date dEnd) {
        if (dStart == null || dEnd == null) return -1;
        int i = 0;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dStart);
        while (calendar.getTime().before(dEnd) || calendar.getTime().equals(dEnd)) {
            calendar.add(Calendar.MONTH, 12);
            i++;
        }
        i--;
        if (i < 0) {
            i = 0;
        }
        return i;
    }

    private int getNumDays(Date dStart, Date dEnd) {
        long diffDays = -1;
        if (dStart == null || dEnd == null) return -1;
        try {
            // Compute dEnd − dStart so that past dates yield positive day counts
            // (consistent with UtilDateUtilities.getNumDays and getHowManyMonthsSinceLast)
            long timeDiff = dEnd.getTime() - dStart.getTime();
            diffDays = timeDiff / (24 * 60 * 60 * 1000);
        } catch (Exception e) {
        }
        return Long.valueOf(diffDays).intValue();
    }

    public static void main(String[] args) {
        Calendar date1 = new GregorianCalendar(1980, Calendar.JANUARY, 31);
        Date d1 = date1.getTime();
        Calendar date2 = new GregorianCalendar(1980, Calendar.MARCH, 1);
        Date d2 = date2.getTime();
        Prevention p = new Prevention();
        log.debug(p.getNumMonths(date1, date2));

    }

    /**
     * Getter for property DOB.
     *
     * @return Value of property DOB.
     */
    public java.util.Date getDOB() {
        return DOB;
    }

    /**
     * Setter for property DOB.
     *
     * @param DOB New value of property DOB.
     */
    public void setDOB(java.util.Date DOB) {
        this.DOB = DOB;
    }

}
