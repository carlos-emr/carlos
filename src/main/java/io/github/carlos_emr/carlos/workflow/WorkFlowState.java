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

import io.github.carlos_emr.carlos.commn.model.WorkFlow;
import io.github.carlos_emr.carlos.commn.dao.WorkFlowDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Manages the persistence and retrieval of workflow instance states in the database.
 *
 * <p>Provides methods to create new workflow instances, update their states, and query
 * for active or all workflow instances by type and demographic number. Uses
 * {@link WorkFlowDao} for database access.</p>
 *
 * @see WorkFlowDao
 * @see WorkFlow
 * @since 2026-03-17
 */
public class WorkFlowState {
    public final static String RHWORKFLOW = "RH";
    public final static String INIT_STATE = "1";

    private WorkFlowDao dao = SpringUtils.getBean(WorkFlowDao.class);


    public WorkFlowState() {
    }

    //TODO: need to add which providers added it  OR i could just logg it as well
    /**
     * Creates a new workflow instance with the specified parameters.
     *
     * @param workflowType String the workflow type identifier (e.g., "RH")
     * @param providerNo String the provider initiating the workflow
     * @param demographicNo String the patient's demographic number
     * @param endDate Date the expected completion date
     * @param current_state String the initial state key
     * @return int the generated workflow instance identifier
     */
    public int addToWorkFlow(String workflowType, String providerNo, String demographicNo, Date endDate, String current_state) {
        WorkFlow wf = new WorkFlow();
        wf.setWorkflowType(workflowType);
        wf.setProviderNo(providerNo);
        wf.setDemographicNo(demographicNo);
        wf.setCompletionDate(endDate);
        wf.setCurrentState(current_state);
        wf.setCreateDateTime(new Date());
        dao.persist(wf);

        return wf.getId();
    }

    /**
     * Updates the current state of a workflow instance.
     *
     * @param workflowId String the workflow instance identifier
     * @param state String the new state key to set
     */
    public void updateWorkFlowState(String workflowId, String state) {
        WorkFlow wf = dao.find(Integer.parseInt(workflowId));
        if (wf != null) {
            wf.setCurrentState(state);
            dao.merge(wf);
        }
    }

    /**
     * Updates the current state and completion date of a workflow instance.
     *
     * @param workflowId String the workflow instance identifier
     * @param state String the new state key to set
     * @param date Date the new completion date
     */
    public void updateWorkFlowState(String workflowId, String state, Date date) {
        WorkFlow wf = dao.find(Integer.parseInt(workflowId));
        if (wf != null) {
            wf.setCurrentState(state);
            wf.setCompletionDate(date);
            dao.merge(wf);
        }
    }


    /**
     * Returns all workflow instances of the specified type.
     *
     * @param workflowType String the workflow type identifier
     * @return ArrayList of Hashtable workflow data maps
     */
    public ArrayList getWorkFlowList(String workflowType) {
        ArrayList list = new ArrayList();

        List<WorkFlow> ws = dao.findByWorkflowType(workflowType);
        for (WorkFlow w : ws) {
            Hashtable h = new Hashtable();
            h.put("ID", w.getId().toString());
            h.put("workflow_type", w.getWorkflowType());
            h.put("create_date_time", w.getCreateDateTime());
            h.put("demographic_no", w.getDemographicNo());
            if (w.getCompletionDate() != null) {
                h.put("completion_date", w.getCompletionDate());
            }
            h.put("current_state", w.getCurrentState());
            list.add(h);
        }

        return list;
    }

    /**
     * Returns all active (non-closed) workflow instances of the specified type.
     *
     * @param workflowType String the workflow type identifier
     * @return ArrayList of Hashtable workflow data maps
     */
    public ArrayList getActiveWorkFlowList(String workflowType) {
        ArrayList list = new ArrayList();

        List<WorkFlow> ws = dao.findActiveByWorkflowType(workflowType);
        for (WorkFlow w : ws) {
            Hashtable h = new Hashtable();
            h.put("ID", w.getId().toString());
            h.put("workflow_type", w.getWorkflowType());
            h.put("create_date_time", w.getCreateDateTime());
            h.put("demographic_no", w.getDemographicNo());
            if (w.getCompletionDate() != null) {
                h.put("completion_date", w.getCompletionDate());
            }
            h.put("current_state", w.getCurrentState());
            list.add(h);
        }

        return list;
    }

    /**
     * Returns active workflow instances of the specified type for a specific patient.
     *
     * @param workflowType String the workflow type identifier
     * @param demographicNo String the patient's demographic number
     * @return ArrayList of Hashtable workflow data maps
     */
    public ArrayList getActiveWorkFlowList(String workflowType, String demographicNo) {
        ArrayList list = new ArrayList();
        List<WorkFlow> ws = dao.findActiveByWorkflowTypeAndDemographicNo(workflowType, demographicNo);
        for (WorkFlow w : ws) {
            Hashtable h = new Hashtable();
            h.put("ID", w.getId().toString());
            h.put("workflow_type", w.getWorkflowType());
            h.put("create_date_time", w.getCreateDateTime());
            h.put("demographic_no", w.getDemographicNo());
            if (w.getCompletionDate() != null) {
                h.put("completion_date", w.getCompletionDate());
            }
            h.put("current_state", w.getCurrentState());
            list.add(h);
        }
        return list;
    }


    /**
     * Translates an RH workflow state key to its human-readable display name.
     *
     * @param s Object the state key as a String
     * @return String the human-readable state name, or {@code null} if the key is not recognized
     */
    public static String rhState(Object s) {
        Hashtable h = new Hashtable();
        h.put("1", "No Appt made");
        h.put("2", "Appt Booked");
        h.put("3", "Injection 28");
        h.put("4", "Missed Appt");
        h.put("C", "Closed");
        String ss = (String) s;
        return (String) h.get(ss);
    }
}
