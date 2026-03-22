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


package io.github.carlos_emr.carlos.tickler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Data access helper for tickler operations including listing, creating,
 * and checking for existing ticklers. Defines status constants (Active, Completed,
 * Deleted) and priority constants (High, Normal, Low) used throughout the tickler subsystem.
 *
 * @since 2026-03-17
 */
public class TicklerData {

    public static String ACTIVE = "A";
    public static String COMPLETED = "C";
    public static String DELETED = "D";

    public static String HIGH = "High";
    public static String NORMAL = "Normal";
    public static String LOW = "Low";

    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);

    public TicklerData() {
    }

    /**
     * Lists ticklers for a patient within the specified date range.
     *
     * @param loggedInInfo LoggedInInfo the current session context
     * @param demographic_no String the patient demographic number
     * @param beginDate String the start date in yyyy-MM-dd format
     * @param endDate String the end date in yyyy-MM-dd format
     * @return List of Tickler ticklers matching the criteria
     */
    public List<Tickler> listTickler(LoggedInInfo loggedInInfo, String demographic_no, String beginDate, String endDate) {
        return ticklerManager.listTicklers(loggedInInfo, Integer.parseInt(demographic_no), ConversionUtils.fromDateString(beginDate), ConversionUtils.fromDateString(endDate));
    }

    /**
     * Creates and persists a new tickler with the specified attributes. Falls back to the
     * current date if the service date cannot be parsed.
     *
     * @param loggedInInfo LoggedInInfo the current session context
     * @param demographic_no String the patient demographic number
     * @param message String the tickler message text
     * @param status String the tickler status character (A=Active, C=Completed, D=Deleted)
     * @param service_date String the service date in yyyy-MM-dd format, or "now()" for the current date
     * @param creator String the provider number of the tickler creator
     * @param priority String the priority level (High, Normal, or Low)
     * @param task_assigned_to String the provider number of the assigned recipient
     */
    public void addTickler(LoggedInInfo loggedInInfo, String demographic_no, String message, String status, String service_date, String creator, String priority, String task_assigned_to) {

        String date = service_date;
        if (date != null && !date.equals("now()")) {          //Just a hack for now.
            date = "'" + service_date + "'";
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Tickler t = new Tickler();
        t.setDemographicNo(Integer.parseInt(demographic_no));
        t.setMessage(message);
        t.setStatusAsChar(status.toCharArray()[0]);

        try {
            t.setServiceDate(formatter.parse(service_date));
        } catch (ParseException e) {
            MiscUtils.getLogger().error("Error", e);
            t.setServiceDate(new Date());
        }
        t.setCreator(creator);
        t.setPriorityAsString(priority);
        t.setTaskAssignedTo(task_assigned_to);

        ticklerManager.addTickler(loggedInInfo, t);
    }

    /**
     * Checks whether a tickler exists for the given patient, assignee, and message.
     *
     * @param demographic String the patient demographic number
     * @param task_assigned_to String the provider number of the assigned recipient
     * @param message String the tickler message to search for
     * @return boolean true if a matching tickler exists
     */
    public boolean hasTickler(String demographic, String task_assigned_to, String message) {
        return ticklerManager.hasTickler(demographic, task_assigned_to, message);
    }

}
