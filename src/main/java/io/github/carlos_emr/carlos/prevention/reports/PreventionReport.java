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


package io.github.carlos_emr.carlos.prevention.reports;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Interface for prevention compliance report generators.
 *
 * <p>Implementations evaluate a set of patients against specific prevention guidelines
 * (e.g., PAP smear, mammogram, flu shot, childhood immunizations, FOBT) and return
 * compliance statistics including up-to-date counts, percentages, and per-patient
 * report display items.</p>
 *
 * @since 2001-2002
 * @see PreventionReportFactory
 * @see io.github.carlos_emr.carlos.prevention.pageUtil.PreventionReportDisplay
 */
public interface PreventionReport {

    /**
     * Runs the prevention compliance report for the given patient set.
     *
     * @param loggedInInfo LoggedInInfo the logged-in session context
     * @param list ArrayList&lt;ArrayList&lt;String&gt;&gt; the patient set where each inner list
     *             contains demographic number as the first element
     * @param asofDate Date the as-of date for calculating compliance
     * @return Hashtable containing report results with keys: "up2date", "percent",
     *         "percentWithGrace", "returnReport", "inEligible", "eformSearch",
     *         "followUpType", "BillCode"
     */
    public Hashtable runReport(LoggedInInfo loggedInInfo, ArrayList<ArrayList<String>> list, Date asofDate);
}
