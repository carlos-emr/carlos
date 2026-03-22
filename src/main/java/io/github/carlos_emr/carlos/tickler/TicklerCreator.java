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

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * Utility class for creating and resolving tickler reminders for patients.
 * Provides convenience methods that wrap {@link TicklerManager} operations,
 * including duplicate detection before creating new ticklers.
 *
 * @since 2026-03-17
 */
public class TicklerCreator {
    TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);


    public TicklerCreator() {
    }

    /**
     * Creates a tickler for a patient if one with the same message does not already exist.
     * The tickler is assigned to the same provider who creates it.
     *
     * @param loggedInInfo LoggedInInfo the current session context
     * @param demoNo String the patient demographic number
     * @param provNo String the provider number (used as both creator and assignee)
     * @param message String the tickler message text
     */
    public void createTickler(LoggedInInfo loggedInInfo, String demoNo, String provNo, String message) {
        if (!ticklerExists(loggedInInfo, demoNo, message)) {
            Tickler t = new Tickler();
            t.setDemographicNo(Integer.parseInt(demoNo));
            t.setMessage(message);
            t.setCreator(provNo);
            t.setTaskAssignedTo(provNo);
            ticklerManager.addTickler(loggedInInfo, t);


        }
    }


    /**
     * Creates a tickler for a patient with a specific assignee, without checking for duplicates.
     *
     * @param loggedInInfo LoggedInInfo the current session context
     * @param demoNo String the patient demographic number
     * @param provNo String the provider number of the tickler creator
     * @param message String the tickler message text
     * @param assignedTo String the provider number of the assigned recipient
     */
    public void createTickler(LoggedInInfo loggedInInfo, String demoNo, String provNo, String message, String assignedTo) {
        Tickler t = new Tickler();
        t.setDemographicNo(Integer.parseInt(demoNo));
        t.setMessage(message);
        t.setCreator(provNo);
        t.setTaskAssignedTo(assignedTo);
        ticklerManager.addTickler(loggedInInfo, t);
    }


    /**
     * Checks whether an active tickler already exists for the given patient with the specified message.
     *
     * @param loggedInInfo LoggedInInfo the current session context
     * @param demoNo String the patient demographic number
     * @param message String the tickler message to search for
     * @return boolean true if an active tickler with the given message exists for the patient
     */
    public boolean ticklerExists(LoggedInInfo loggedInInfo, String demoNo, String message) {
        CustomFilter filter = new CustomFilter();
        filter.setDemographicNo(demoNo);
        filter.setMessage(message);
        filter.setStatus("A");
        List<Tickler> ticklers = ticklerManager.getTicklers(loggedInInfo, filter);
        return !ticklers.isEmpty();
    }

    /**
     * Resolves (completes) ticklers matching the given reminder string for a list of patients.
     *
     * @param loggedInInfo LoggedInInfo the current session context
     * @param providerNo String the provider number performing the resolution
     * @param cdmPatientNos List of String patient demographic numbers whose ticklers should be resolved
     * @param remString String the reminder string to match against tickler messages
     */
    public void resolveTicklers(LoggedInInfo loggedInInfo, String providerNo, List<String> cdmPatientNos, String remString) {
        ticklerManager.resolveTicklers(loggedInInfo, providerNo, cdmPatientNos, remString);
    }
}
