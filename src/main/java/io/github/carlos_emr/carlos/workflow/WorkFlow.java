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
 * WorkFlow.java
 *
 * Created on November 7, 2006, 10:14 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.workflow;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

/**
 * Interface defining the contract for clinical workflow implementations in CARLOS EMR.
 *
 * <p>Workflows track patient-specific processes (such as Rh immunoglobulin management)
 * through a series of states. Implementations provide state definitions, Drools-based
 * rule execution for state transitions, and links to associated clinical forms.</p>
 *
 * @see RHWorkFlow
 * @see WorkFlowDS
 * @see WorkFlowInfo
 * @since 2026-03-17
 */
public interface WorkFlow {
    /**
     * Returns active workflow instances for a specific patient.
     *
     * @param demographicNo String the patient's demographic number
     * @return ArrayList of workflow data maps for active instances
     */
    public ArrayList getActiveWorkFlowList(String demographicNo);

    /**
     * Returns all active workflow instances of this type.
     *
     * @return ArrayList of workflow data maps for all active instances
     */
    public ArrayList getActiveWorkFlowList();

    /**
     * Returns the human-readable name for a given state key.
     *
     * @param state String the state key identifier
     * @return String the display name for the state
     */
    public String getState(String state);

    /**
     * Returns all defined states for this workflow type.
     *
     * @return List of {@link WFState} instances representing all possible states
     */
    public List getStates();

    /**
     * Executes Drools decision rules against the provided workflow data using the default rule set.
     *
     * @param hashtable Hashtable the workflow data to evaluate
     * @return WorkFlowInfo the result after rule execution, potentially with updated state and colour
     */
    public WorkFlowInfo executeRules(Hashtable hashtable);

    /**
     * Executes Drools decision rules against the provided workflow data using a specific rule engine.
     *
     * @param wfDS WorkFlowDS the decision support engine to use
     * @param hashtable Hashtable the workflow data to evaluate
     * @return WorkFlowInfo the result after rule execution
     */
    public WorkFlowInfo executeRules(WorkFlowDS wfDS, Hashtable hashtable);

    /**
     * Returns the URL link to the clinical form associated with this workflow.
     *
     * @param demographic String the patient's demographic number
     * @param workFlowId String the workflow instance identifier
     * @return String the URL path to the associated form
     */
    public String getLink(String demographic, String workFlowId);

    /**
     * Creates a new workflow instance for a patient.
     *
     * @param providerNo String the provider initiating the workflow
     * @param demographicNo String the patient's demographic number
     * @param endDate Date the expected completion date
     * @return int the generated workflow instance identifier
     */
    int addToWorkFlow(String providerNo, String demographicNo, Date endDate);

    /**
     * Returns the Drools decision support engine for this workflow type.
     *
     * @return WorkFlowDS the decision support engine loaded with the appropriate DRL rules
     */
    public WorkFlowDS getWorkFlowDS();

}
