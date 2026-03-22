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
 * Data transfer object representing a single patient's prevention compliance status
 * in a prevention report.
 *
 * <p>Contains the patient's demographic number, compliance state (e.g., "Up to date",
 * "Overdue", "Refused"), display color coding, date of last prevention, and follow-up
 * tracking fields. Implements {@link Comparable} to sort by rank (severity).</p>
 *
 * @since 2001-2002
 * @see io.github.carlos_emr.carlos.prevention.reports.PreventionReport
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

    /** Default no-argument constructor. */
    public PreventionReportDisplay() {
    }

    /**
     * Compares this display item to another by rank for sorting. Lower ranks
     * (more urgent statuses) appear first.
     *
     * @param o Object the other {@code PreventionReportDisplay} to compare to
     * @return int negative if this rank is lower, 0 if equal, positive if higher
     */
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
