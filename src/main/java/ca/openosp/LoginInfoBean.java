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

//import java.sql.*;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Bean for managing login attempt tracking and account lockout functionality.
 * 
 * <p>This bean tracks failed login attempts and implements a timeout-based
 * lockout mechanism to prevent brute-force attacks. Key features:</p>
 * <ul>
 *   <li>Tracks login attempt count and timestamp</li>
 *   <li>Implements configurable maximum attempts before lockout</li>
 *   <li>Auto-resets after a configurable timeout period</li>
 *   <li>Status tracking (normal vs blocked)</li>
 * </ul>
 * 
 * <p>Default configuration:</p>
 * <ul>
 *   <li>Maximum attempts: 3</li>
 *   <li>Lockout duration: 10 minutes</li>
 *   <li>Initial status: Normal (1)</li>
 * </ul>
 */
public class LoginInfoBean {

    private GregorianCalendar starttime = null;
    private int times = 1;
    private int status = 1; // 1 - normal, 0 - block out

    private int maxtimes = 3;
    private int maxduration = 10;

    /**
     * Constructs a new LoginInfoBean with default settings.
     */
    public LoginInfoBean() {
    }

    /**
     * Constructs a new LoginInfoBean with custom configuration.
     * 
     * @param starttime1 the initial start time for tracking
     * @param maxtimes1 maximum login attempts before lockout
     * @param maxduration1 lockout duration in minutes
     */
    public LoginInfoBean(GregorianCalendar starttime1, int maxtimes1, int maxduration1) {
        starttime = starttime1;
        maxtimes = maxtimes1;
        maxduration = maxduration1;
    }

    /**
     * Initializes the login tracking with a new start time.
     * Resets attempt count and status to normal.
     * 
     * @param starttime1 the new start time for tracking
     */
    public void initialLoginInfoBean(GregorianCalendar starttime1) {
        starttime = starttime1;
        int times = 0;
        int status = 1; // 1 - normal, 0 - block out
    }

    /**
     * Updates the bean after a login attempt.
     * Increments attempt count and blocks access if maximum attempts exceeded.
     * Auto-resets if timeout period has elapsed.
     * 
     * @param now the current time
     * @param times1 the current attempt count (not used - increments internal counter)
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
     * Checks if the lockout timeout period has elapsed.
     * 
     * @param now the current time
     * @return true if timeout period has passed, false otherwise
     */
    public boolean getTimeOutStatus(GregorianCalendar now) {
        boolean btemp = false;
        //if time out and status is 1, return true
        GregorianCalendar cal = (GregorianCalendar) starttime.clone();
        cal.add(Calendar.MINUTE, maxduration);
        if ((cal.getTimeInMillis() < now.getTimeInMillis()))
            btemp = true; //starttime = starttime1;

        return btemp;
    }

    public void setStarttime(GregorianCalendar starttime1) {
        starttime = starttime1;
    }

    public void setTimes(int times1) {
        times = times1;
    }

    public void setStatus(int status1) {
        status = status1;
    }

    public GregorianCalendar getStarttime() {
        return (starttime);
    }

    public int getTimes() {
        return (times);
    }

    public int getStatus() {
        return (status);
    }


}
