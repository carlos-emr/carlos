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

/**
 * Represents a patient's prevention and immunization profile used for Drools-based
 * clinical decision support evaluation.
 *
 * <p>Holds patient demographics (sex, date of birth), a collection of
 * {@link PreventionItem} entries grouped by prevention type, and accumulates
 * clinical warnings and reminders produced by the prevention rule engine
 * ({@link PreventionDS}).</p>
 *
 * <p>This class is inserted as a fact into the Drools {@code KieSession} where
 * prevention rules evaluate the patient's age, sex, and immunization history
 * to generate appropriate clinical decision support messages.</p>
 *
 * @since 2001-2002
 * @see PreventionItem
 * @see PreventionDS
 * @see PreventionDSImpl
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

    /** Default no-argument constructor. */
    public Prevention() {
    }

    /**
     * Constructs a Prevention profile with name, sex, and date of birth.
     *
     * @param nam String the patient's name (used for debug logging)
     * @param se String the patient's sex code ("M" or "F")
     * @param birthdate Date the patient's date of birth
     */
    public Prevention(String nam, String se, Date birthdate) {
        name = nam;
        sex = se;
        DOB = birthdate;
    }

    /**
     * Constructs a Prevention profile with sex and date of birth.
     *
     * @param se String the patient's sex code ("M" or "F")
     * @param birthdate Date the patient's date of birth
     */
    public Prevention(String se, Date birthdate) {
        sex = se;
        DOB = birthdate;
    }

    /**
     * Constructs a Prevention profile for the given demographic number.
     *
     * @param demographicNo String the patient's demographic number
     */
    public Prevention(String demographicNo) {

    }

    /**
     * Logs a debug message prefixed with the patient's name.
     *
     * @param logMessage String the message to log
     */
    public void log(String logMessage) {
        log.debug(name + " :" + logMessage);
    }

    /**
     * Sets the patient's sex code.
     *
     * @param s String the sex code ("M" or "F")
     */
    public void setSex(String s) {
        sex = s;
    }

    /**
     * Returns the patient's sex code.
     *
     * @return String the sex code ("M" or "F"), or {@code null} if not set
     */
    public String getSex() {
        return sex;
    }

    public java.lang.String getName() {
        return name;
    }

    public void setName(java.lang.String name) {
        this.name = name;
    }

    /**
     * Adds a warning message to the general warnings list.
     *
     * @param warn String the warning message text
     */
    public void addWarning(String warn) {
        messageList.add(warn);
    }

    /**
     * Adds a warning message associated with a specific prevention type.
     *
     * @param prevName String the prevention type name (used as map key)
     * @param warn String the warning message text
     */
    public void addWarning(String prevName, String warn) {
        addWarning(warn);
        warnings.put(prevName, warn);
    }

    /**
     * Returns the list of all accumulated warning messages.
     *
     * @return ArrayList&lt;String&gt; the warning messages, never {@code null}
     */
    public ArrayList<String> getWarnings() {
        return messageList;
    }

    /**
     * Returns the map of prevention-type-specific warning messages.
     *
     * @return Map keyed by prevention type name with warning message values
     */
    @SuppressWarnings("rawtypes")
    public Map getWarningMsgs() {
        return warnings;
    }

    /**
     * Adds a clinical reminder message.
     *
     * @param warn String the reminder message text
     */
    public void addReminder(String warn) {
        reminder.add(warn);
    }

    /**
     * Returns the list of all accumulated reminder messages.
     *
     * @return ArrayList&lt;String&gt; the reminder messages, never {@code null}
     */
    public ArrayList<String> getReminder() {
        return reminder;
    }

    /**
     * Checks whether the patient is male.
     *
     * @return boolean {@code true} if sex code is "M"
     */
    public boolean isMale() {
        boolean retval = false;
        if (sex != null && sex.equals("M")) {
            retval = true;
        }
        return retval;
    }

    /**
     * Checks whether the patient is female.
     *
     * @return boolean {@code true} if sex code is "F"
     */
    public boolean isFemale() {
        boolean retval = false;
        if (sex != null && sex.equals("F")) {
            retval = true;
        }
        return retval;
    }

    /**
     * Adds a prevention item to this profile, grouped by prevention type name.
     * Multiple items of the same type are stored as a vector.
     *
     * @param pItem PreventionItem the prevention/immunization record to add
     */
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

    /**
     * Calculates the patient's age in months from the given date of birth to now.
     *
     * @param DOB Date the date of birth
     * @return int the age in months, or 0 if DOB is {@code null}
     */
    public int getAgeInMonths(Date DOB) {
        if (DOB != null)
            return getNumMonths(DOB, Calendar.getInstance().getTime());
        else
            return 0;
    }

    /**
     * Returns the patient's age in months, calculated from this profile's DOB.
     * The result is cached after the first calculation.
     *
     * @return int the age in months, or 0 if DOB is {@code null}
     */
    public int getAgeInMonths() {
        if (ageInMonths == -1) {
            ageInMonths = getAgeInMonths(DOB);
        }
        return ageInMonths;
    }

    /**
     * Returns the patient's age in years, calculated from this profile's DOB to now.
     *
     * @return int the age in years, or 0 if DOB is {@code null}
     */
    public int getAgeInYears() {
        if (DOB != null)
            return getNumYears(DOB, Calendar.getInstance().getTime());
        else
            return 0;
    }

    /**
     * Checks whether today falls within the specified date range (exclusive).
     *
     * @param startDate String the range start date in "yyyy-MM-dd" format
     * @param endDate String the range end date in "yyyy-MM-dd" format
     * @return boolean {@code true} if today is after startDate and before endDate
     */
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

    /**
     * Checks whether the last prevention of the given type falls within the specified date range.
     *
     * @param preventionType String the prevention type name (e.g., "Flu", "DTaP-IPV")
     * @param startDate String the range start date in "yyyy-MM-dd" format
     * @param endDate String the range end date in "yyyy-MM-dd" format
     * @return boolean {@code true} if the last prevention date is within range
     */
    public boolean isLastPreventionWithinRange(String preventionType, String startDate, String endDate) {
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

    /**
     * Returns the date of the most recent prevention of the specified type.
     *
     * @param preventionType String the prevention type name
     * @return Date the date of the last prevention, or {@code null} if no records exist
     */
    public Date getLastPreventionDate(String preventionType) {
        //Still needs to be implemented
        Date lastDate = null;
        Vector vec = (Vector) preventionTypes.get(preventionType);
        if (vec != null) {
            PreventionItem p = (PreventionItem) vec.get(vec.size() - 1);  // Get date from return object
            lastDate = p.getDatePreformed();
            log.debug("getting last date preformed of a " + preventionType + " :" + lastDate.toString());
        }

        return lastDate; //
    }

    /**
     * Checks whether a next-date is set for the most recent prevention of the given type.
     *
     * @param preventionType String the prevention type name
     * @return boolean {@code true} if a next date is set
     */
    public boolean isNextDateSet(String preventionType) {
        boolean isSet = true;
        Date nextDate = getNextPreventionDate(preventionType);
        log.debug("IS SET WHAT DOES IT HAVE " + nextDate);
        if (nextDate == null) {
            isSet = false;
        }
        return isSet;
    }

    /**
     * Checks whether a next-date is NOT set for the given prevention type.
     *
     * @param preventionType String the prevention type name
     * @return boolean {@code true} if no next date is set
     */
    public boolean isNotNextDateSet(String preventionType) {
        return !isNextDateSet(preventionType);
    }

    /**
     * Checks whether today has passed the next scheduled date for the given prevention type.
     *
     * @param preventionType String the prevention type name
     * @return boolean {@code true} if today is after the next date, or if no next date exists
     */
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

    /**
     * Checks whether today has NOT passed the next scheduled date for the given prevention type.
     *
     * @param preventionType String the prevention type name
     * @return boolean {@code true} if the next date has not yet passed
     */
    public boolean isNotPassedNextDate(String preventionType) {
        return !isPassedNextDate(preventionType);
    }

    /**
     * Returns the next scheduled date for the most recent prevention of the given type.
     *
     * @param preventionType String the prevention type name
     * @return Date the next scheduled prevention date, or {@code null} if not set
     */
    public Date getNextPreventionDate(String preventionType) {
        Date nextDate = null;
        Vector vec = (Vector) preventionTypes.get(preventionType);
        if (vec != null) {
            PreventionItem p = (PreventionItem) vec.get(vec.size() - 1);  // Get date from return object
            nextDate = p.getNextDate();
            log.debug("getting next date preformed of a " + preventionType + " :" + nextDate);
        }
        return nextDate; //
    }

    /**
     * Checks whether the most recent prevention of the given type is marked as "never".
     *
     * @param preventionType String the prevention type name
     * @return boolean {@code true} if the prevention is marked as never to be given
     */
    public boolean isPreventionNever(String preventionType) {
        boolean ispreventionnever = false;
        Vector vec = (Vector) preventionTypes.get(preventionType);
        if (vec != null) {
            PreventionItem p = (PreventionItem) vec.get(vec.size() - 1);  // Get date from return object
            ispreventionnever = p.getNeverVal();
            log.debug("getting never of a " + preventionType + " :" + ispreventionnever);
        }
        return ispreventionnever; //
    }

    /**
     * Checks whether the prevention is NOT marked as "never" for the given type.
     *
     * @param preventionType String the prevention type name
     * @return boolean {@code true} if the prevention is not marked as never
     */
    public boolean isNotPreventionNever(String preventionType) {
        return !isNotPreventionNever(preventionType);
    }


    /**
     * Checks whether any record of the given prevention type is marked as ineligible.
     *
     * @param preventionType String the prevention type name
     * @return boolean {@code true} if at least one record is ineligible
     */
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

    /**
     * Checks whether no record of the given prevention type is marked as ineligible.
     *
     * @param preventionType String the prevention type name
     * @return boolean {@code true} if no records are ineligible
     */
    public boolean isNotInelligible(String preventionType) {
        return !isInelligible(preventionType);
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
            long timeDiff = dStart.getTime() - dEnd.getTime();
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
