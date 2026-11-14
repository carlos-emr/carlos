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
 * @author jay
 */
public interface PreventionReport {
    /**
     * Key of the report entry that counts the patients whose preventions could not be read (a
     * decimal string, {@code "0"} when every chart was read). Those patients are in
     * {@code returnReport} as {@link io.github.carlos_emr.carlos.prevention.pageUtil.PreventionReportDisplay#unreadable(Integer)}
     * rows; a report with a count above zero is incomplete and must be shown as such.
     */
    String UNREADABLE_COUNT = "unreadable";

    public Hashtable runReport(LoggedInInfo loggedInInfo, ArrayList<ArrayList<String>> list, Date asofDate);
}
