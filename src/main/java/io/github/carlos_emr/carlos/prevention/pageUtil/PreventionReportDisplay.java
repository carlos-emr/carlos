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


package io.github.carlos_emr.carlos.prevention.pageUtil;

import java.util.Date;

/**
 * @author Jay Gallagher
 */
public class PreventionReportDisplay implements Comparable {

    public Integer demographicNo = null;
    public String lastDate = null;
    public int rank = 0;
    public String state = null;
    public String numMonths = null;
    public String color = null;
    public String numShots = null;
    public String bonusStatus = null;
    public String billStatus = null;

    //FollowUp Data
    public Date lastFollowup = null;
    public String lastFollupProcedure = null;
    public String nextSuggestedProcedure = null;

    public PreventionReportDisplay() {
    }

    /**
     * The state of a patient whose preventions could not be read. Like the other states it is
     * the English text the reports have always carried; the report page shows this one through
     * its {@code prevention.reporting.unreadable} message.
     */
    public static final String STATE_UNREADABLE = "Could not be read";

    /** The colour name of an unreadable row; the report page gives it its own badge. */
    public static final String COLOR_UNREADABLE = "unreadable";

    /**
     * The report row of a patient whose preventions could not be read. The patient stays in the
     * report, ranked ahead of every other state so the row is seen first, and is neither counted
     * as up to date nor offered a follow-up letter or a bonus: nothing is known about them.
     *
     * @param demographicNo the patient
     * @return the row to add to the report in place of a computed one
     */
    public static PreventionReportDisplay unreadable(Integer demographicNo) {
        PreventionReportDisplay row = new PreventionReportDisplay();
        row.demographicNo = demographicNo;
        row.rank = 0;
        row.lastDate = "------";
        row.state = STATE_UNREADABLE;
        row.numMonths = "------";
        row.numShots = "------";
        row.color = COLOR_UNREADABLE;
        row.bonusStatus = "N";
        row.billStatus = "N";
        row.nextSuggestedProcedure = "----";
        return row;
    }

    /**
     * @return whether this row stands for a patient whose preventions could not be read
     */
    public boolean isUnreadable() {
        return STATE_UNREADABLE.equals(state);
    }

    public int compareTo(Object o) {

        int ret = 0;
        if (this.rank < ((PreventionReportDisplay) o).rank) {
            ret = -1;
        } else if (this.rank > ((PreventionReportDisplay) o).rank) {
            ret = +1;
        }
        return ret;
        // If this < o, return a negative value
        // If this = o, return 0
        // If this > o, return a positive value
    }

}
