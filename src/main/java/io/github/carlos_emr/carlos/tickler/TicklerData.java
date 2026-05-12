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
 * Data access and transformation utility for Ticklers.
 * Provides helper methods for listing, adding, and checking the existence of ticklers,
 * while handling necessary data conversions (e.g., String to Date, status character mapping).
 * 
 * @author Jay Gallagher
 */
public class TicklerData {

    // Tickler Status Constants
    public static final String ACTIVE = "A";
    public static final String COMPLETED = "C";
    public static final String DELETED = "D";

    // Tickler Priority Constants
    public static final String HIGH = "High";
    public static final String NORMAL = "Normal";
    public static final String LOW = "Low";

    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);

    /**
     * Default constructor.
     */
    public TicklerData() {
    }

    /**
     * Retrieves a list of ticklers for a given demographic within a specified date range.
     * Parses the date strings into java.util.Date objects before querying.
     *
     * @param loggedInInfo   the user's session context
     * @param demographic_no the demographic ID to retrieve ticklers for
     * @param beginDate      the start date string for the filter
     * @param endDate        the end date string for the filter
     * @return a list of matching Tickler objects
     */
    public List<Tickler> listTickler(LoggedInInfo loggedInInfo, String demographic_no, String beginDate, String endDate) {
        return ticklerManager.listTicklers(loggedInInfo, Integer.parseInt(demographic_no), ConversionUtils.fromDateString(beginDate), ConversionUtils.fromDateString(endDate));
    }

    /**
     * Creates and persists a new tickler with explicitly provided fields.
     * Handles string-to-date conversion and maps string statuses/priorities to their
     * corresponding internal representations.
     *
     * @param loggedInInfo     the user's session context
     * @param demographic_no   the target patient's ID
     * @param message          the task description/message
     * @param status           the status string (A, C, D)
     * @param service_date     the string representation of the service date (yyyy-MM-dd)
     * @param creator          the provider ID of the task creator
     * @param priority         the priority level (High, Normal, Low)
     * @param task_assigned_to the provider ID assigned to the task
     */
    public void addTickler(LoggedInInfo loggedInInfo, String demographic_no, String message, String status, String service_date, String creator, String priority, String task_assigned_to) {

        String date = service_date;
        if (date != null && !date.equals("now()")) {          // Just a hack for now.
            date = "'" + service_date + "'";
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Tickler t = new Tickler();
        t.setDemographicNo(Integer.parseInt(demographic_no));
        t.setMessage(message);
        
        String effectiveStatus = status == null ? ACTIVE : status.trim();
        if (effectiveStatus.isEmpty()) {
            effectiveStatus = ACTIVE;
        }
        // Take the single-letter tickler status code (A, C, or D).
        t.setStatusAsChar(effectiveStatus.charAt(0));

        try {
            // Attempt to parse the provided date
            t.setServiceDate(formatter.parse(service_date));
        } catch (ParseException e) {
            // Fallback to the current date if parsing fails
            MiscUtils.getLogger().error("Error parsing tickler service date", e);
            t.setServiceDate(new Date());
        }
        
        t.setCreator(creator);
        t.setPriorityAsString(priority);
        t.setTaskAssignedTo(task_assigned_to);

        ticklerManager.addTickler(loggedInInfo, t);
    }

    /**
     * Checks if a tickler already exists for a specific patient, assigned to a specific provider,
     * with an exact message match.
     *
     * @param demographic      the patient ID
     * @param task_assigned_to the provider ID the task is assigned to
     * @param message          the exact message to search for
     * @return true if a matching tickler exists, false otherwise
     */
    public boolean hasTickler(String demographic, String task_assigned_to, String message) {
        return ticklerManager.hasTickler(demographic, task_assigned_to, message);
    }

}
