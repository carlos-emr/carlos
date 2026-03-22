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


package io.github.carlos_emr.carlos.workflow;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Implementation of the Rh immunoglobulin (RhIg) clinical workflow for managing
 * Rh-negative pregnant patients.
 *
 * <p>Tracks patients through states such as "No Appt made", "Appt Booked",
 * "Injection 28" (28-week injection), "Requires Another Injection", "Missed Appt",
 * and "Closed". Uses Drools rules ({@code Rh_workflow.drl}) for automated state
 * transition decisions based on gestational age and appointment status.</p>
 *
 * @see WorkFlow
 * @see WorkFlowDS
 * @see WorkFlowDSFactory
 * @since 2026-03-17
 */
public class RHWorkFlow implements WorkFlow {

    /**
     * Creates a new instance of RHWorkFlow
     */
    static final String WORKFLOWTYPE = "RH";
    static Hashtable<String, WFState> states = null;
    static ArrayList<WFState> stateList = null;

    public RHWorkFlow() {
        states = new Hashtable<String, WFState>();
        //TODO: CLEAN THIS UP WHEN THIS IS PROCESSED IN A XML FILE

        WFState wf1 = new WFState("1", "No Appt made", "");
        WFState wf2 = new WFState("2", "Appt Booked", "");
        WFState wf3 = new WFState("3", "Injection 28", "");
        WFState wf4 = new WFState("4", "Requires Another Injection", "");
        WFState wf5 = new WFState("5", "Missed Appt", "");
        // WFState wf6 = new WFState("6","Follow up Appt Booked","");
        WFState wfC = new WFState("C", "Closed", "");

        states.put("1", wf1);
        states.put("2", wf2);
        states.put("3", wf3);
        states.put("4", wf4);
        states.put("5", wf5);
        states.put("C", wfC);

        stateList = new ArrayList<WFState>();
        stateList.add(wf1);
        stateList.add(wf2);
        stateList.add(wf3);
        stateList.add(wf4);
        stateList.add(wf5);
        stateList.add(wfC);
    }


    /** {@inheritDoc} */
    public ArrayList getActiveWorkFlowList(String demographicNo) {
        WorkFlowState wfs = new WorkFlowState();
        return wfs.getActiveWorkFlowList(RHWorkFlow.WORKFLOWTYPE, demographicNo);
    }

    /** {@inheritDoc} */
    public ArrayList getActiveWorkFlowList() {
        WorkFlowState wfs = new WorkFlowState();
        return wfs.getActiveWorkFlowList(RHWorkFlow.WORKFLOWTYPE);
    }

    /** {@inheritDoc} */
    public String getState(String state) {
        MiscUtils.getLogger().debug("state: " + state);
        WFState wf = states.get(state);
        MiscUtils.getLogger().debug("wf " + wf);

        String ret = "None";
        if (wf != null) {
            ret = wf.getName();
        }
        return ret;
    }

    /** {@inheritDoc} */
    public List<WFState> getStates() {
        return stateList;
    }

    /** {@inheritDoc} */
    public int addToWorkFlow(String providerNo, String demographicNo, Date endDate) {
        WorkFlowState wfs = new WorkFlowState();
        return wfs.addToWorkFlow(WorkFlowState.RHWORKFLOW, providerNo, demographicNo, endDate, WorkFlowState.INIT_STATE);
    }

    /** {@inheritDoc} */
    public WorkFlowInfo executeRules(Hashtable hashtable) {
        WorkFlowInfo wfi = new WorkFlowInfo(hashtable);
        WorkFlowDS wfDS = WorkFlowDSFactory.getWorkFlowDS("Rh_workflow.drl");
        try {
            wfi = wfDS.getMessages(wfi);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return wfi;
    }

    /** {@inheritDoc} */
    public WorkFlowInfo executeRules(WorkFlowDS wfDS, Hashtable hashtable) {
        WorkFlowInfo wfi = new WorkFlowInfo(hashtable);
        try {
            wfi = wfDS.getMessages(wfi);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return wfi;
    }


    /** {@inheritDoc} */
    public WorkFlowDS getWorkFlowDS() {
        return WorkFlowDSFactory.getWorkFlowDS("Rh_workflow.drl");
    }

    /** {@inheritDoc} */
    public String getLink(String demographicNo, String workFlowId) {
        return "../form/forwardshortcutname.do?formname=RH Form&amp;demographic_no=" + demographicNo;
    }


}
