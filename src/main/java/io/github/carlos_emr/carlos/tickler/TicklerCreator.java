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
 * Utility class for creating and managing tickler items.
 * A tickler is essentially a task, reminder, or follow-up item assigned to a provider
 * regarding a specific demographic (patient).
 * 
 * This class abstracts the interaction with the {@link TicklerManager} and provides
 * simplified methods for creating and resolving ticklers.
 */
public class TicklerCreator {
    // Spring-managed bean for core tickler operations
    TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);

    /**
     * Default constructor.
     */
    public TicklerCreator() {
    }

    /**
     * Creates a new tickler for a patient if an identical active tickler does not already exist.
     * This method acts as an idempotent operation, preventing duplicate reminders 
     * for the same task on the same patient. The tickler will be assigned to the creator.
     *
     * @param loggedInInfo the context of the currently logged-in user
     * @param demoNo       the demographic number (patient ID) to associate the tickler with
     * @param provNo       the provider number of the user creating the tickler
     * @param message      the tickler message/task description
     */
    public void createTickler(LoggedInInfo loggedInInfo, String demoNo, String provNo, String message) {
        // Prevent duplicate ticklers by checking if an active one with the same message already exists
        if (!ticklerExists(loggedInInfo, demoNo, message)) {
            Tickler t = new Tickler();
            t.setDemographicNo(Integer.parseInt(demoNo));
            t.setMessage(message);
            
            // Set the creator and automatically assign it back to the creator
            t.setCreator(provNo);
            t.setTaskAssignedTo(provNo);
            
            ticklerManager.addTickler(loggedInInfo, t);
        }
    }


    /**
     * Creates a new tickler and explicitly assigns it to a specific provider.
     * Note: Unlike the overloaded method, this does NOT check for duplicates before creating,
     * potentially allowing multiple identical ticklers to be assigned.
     *
     * @param loggedInInfo the context of the currently logged-in user
     * @param demoNo       the demographic number (patient ID)
     * @param provNo       the provider number of the user creating the tickler
     * @param message      the tickler message/task description
     * @param assignedTo   the provider number of the user who the task is assigned to
     */
    public void createTickler(LoggedInInfo loggedInInfo, String demoNo, String provNo, String message, String assignedTo) {
        Tickler t = new Tickler();
        t.setDemographicNo(Integer.parseInt(demoNo));
        t.setMessage(message);
        
        // Track both who created the task and who is responsible for completing it
        t.setCreator(provNo);
        t.setTaskAssignedTo(assignedTo);
        
        ticklerManager.addTickler(loggedInInfo, t);
    }


    /**
     * Checks if an active tickler already exists for a specific patient with a specific message.
     * Used primarily to prevent duplicate task creation.
     *
     * @param loggedInInfo the context of the currently logged-in user
     * @param demoNo       the demographic number (patient ID) as a String
     * @param message      the exact message text to search for
     * @return true if at least one active tickler matches the criteria, false otherwise
     */
    public boolean ticklerExists(LoggedInInfo loggedInInfo, String demoNo, String message) {
        // Build a filter to find active ticklers matching the demo and message
        CustomFilter filter = new CustomFilter();
        filter.setDemographicNo(demoNo);
        filter.setMessage(message);
        filter.setStatus("A"); // "A" stands for Active status
        
        List<Tickler> ticklers = ticklerManager.getTicklers(loggedInInfo, filter);
        
        // If the list is not empty, a matching tickler exists
        return !ticklers.isEmpty();
    }

    /**
     * Bulk resolves ticklers for multiple patients that contain a specific message string.
     * This is useful for clearing automated reminders (like chronic disease management follow-ups)
     * once the corresponding action has been taken.
     *
     * @param loggedInInfo  the context of the currently logged-in user
     * @param providerNo    the provider number initiating the resolution
     * @param cdmPatientNos a list of patient demographic numbers to check for ticklers
     * @param remString     the specific reminder text that identifies the ticklers to resolve
     */
    public void resolveTicklers(LoggedInInfo loggedInInfo, String providerNo, List<String> cdmPatientNos, String remString) {
        // Delegate to the TicklerManager which handles the underlying bulk update operations
        ticklerManager.resolveTicklers(loggedInInfo, providerNo, cdmPatientNos, remString);
    }
}
