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


/*
 *
 */

package io.github.carlos_emr.carlos.login;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Tracks failed login attempts and enforces time-based lockout for a single IP address or username.
 *
 * <p>Each instance records the start time of the first failed attempt, counts subsequent failures,
 * and transitions to a blocked state (status == 0) when the failure count exceeds the configured
 * maximum threshold. Blocks automatically expire after the configured duration.
 *
 * <p>Status values:
 * <ul>
 *   <li>1 - Normal (login attempts allowed)</li>
 *   <li>0 - Blocked (login attempts denied until timeout expires)</li>
 * </ul>
 *
 * @see LoginCheckLogin for the coordinator that manages LoginInfoBean instances
 * @see LoginList for the singleton registry of active tracking entries
 * @since 2026-03-17
 */
public final class LoginInfoBean {
    private GregorianCalendar starttime = null;
    private int times = 1;
    private int status = 1; // 1 - normal, 0 - block out

    private int maxtimes = 3;
    private int maxduration = 10;

    /**
     * Default constructor with default thresholds (3 attempts, 10 minute duration).
     */
    public LoginInfoBean() {
    }

    /**
     * Constructs a tracking entry with custom thresholds.
     *
     * @param starttime1 GregorianCalendar the time of the first failed attempt
     * @param maxtimes1 int maximum number of failed attempts before blocking (decremented by 1 internally)
     * @param maxduration1 int duration in minutes before the block expires
     */
    public LoginInfoBean(GregorianCalendar starttime1, int maxtimes1, int maxduration1) {
        starttime = starttime1;
        maxtimes = maxtimes1 - 1;
        maxduration = maxduration1;
    }

    /**
     * Resets this tracking entry with a new start time, clearing the attempt count and restoring normal status.
     *
     * @param starttime1 GregorianCalendar the new start time for tracking
     */
    public void initialLoginInfoBean(GregorianCalendar starttime1) {
        starttime = starttime1;
        int times = 0;
        int status = 1; // 1 - normal, 0 - block out
    }

    /**
     * Records an additional failed login attempt and blocks if threshold exceeded.
     *
     * <p>If the tracking duration has expired, the entry is reset. Otherwise, the attempt
     * count is incremented and the status is set to blocked (0) if the count exceeds
     * the maximum allowed failures.
     *
     * @param now GregorianCalendar the current time
     * @param times1 int number of additional attempts to record (typically 1)
     */
    public void updateLoginInfoBean(GregorianCalendar now, int times1) {
        //if time out, initial bean again.
        if (getTimeOutStatus(now)) {
            initialLoginInfoBean(now);
            return;
        }
        //else times++. if times out, status block
        ++times;
        if (times > maxtimes)
            status = 0; // 1 - normal, 0 - block out
    }

    /**
     * Checks whether this tracking entry's duration has expired.
     *
     * @param now GregorianCalendar the current time to compare against
     * @return boolean true if the configured duration has elapsed since start time
     */
    public boolean getTimeOutStatus(GregorianCalendar now) {
        boolean btemp = false;
        //if time out and status is 1, return true
        GregorianCalendar cal = (GregorianCalendar) starttime.clone();
        cal.add(Calendar.MINUTE, maxduration);
        if (cal.getTimeInMillis() < now.getTimeInMillis())
            btemp = true; //starttime = starttime1;

        return btemp;
    }

    /**
     * Sets the start time for this tracking entry.
     *
     * @param starttime1 GregorianCalendar the start time
     */
    public void setStarttime(GregorianCalendar starttime1) {
        starttime = starttime1;
    }

    /**
     * Sets the failed attempt count.
     *
     * @param times1 int the number of failed attempts
     */
    public void setTimes(int times1) {
        times = times1;
    }

    /**
     * Sets the login status (1 = normal, 0 = blocked).
     *
     * @param status1 int the status value
     */
    public void setStatus(int status1) {
        status = status1;
    }

    /**
     * Gets the start time of the first failed attempt.
     *
     * @return GregorianCalendar the start time
     */
    public GregorianCalendar getStarttime() {
        return (starttime);
    }

    /**
     * Gets the current failed attempt count.
     *
     * @return int the number of failed attempts
     */
    public int getTimes() {
        return (times);
    }

    /**
     * Gets the current login status (1 = normal, 0 = blocked).
     *
     * @return int the status value
     */
    public int getStatus() {
        return (status);
    }
}
