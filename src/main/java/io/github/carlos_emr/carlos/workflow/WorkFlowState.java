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
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.WorkFlow;
import io.github.carlos_emr.carlos.commn.dao.WorkFlowDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * @author jay
 */
public class WorkFlowState {
    public final static String RHWORKFLOW = "RH";
    public final static String INIT_STATE = "1";

    private WorkFlowDao dao = SpringUtils.getBean(WorkFlowDao.class);


    public WorkFlowState() {
    }

    //TODO: need to add which providers added it  OR i could just logg it as well
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

    public void updateWorkFlowState(String workflowId, String state) {
        WorkFlow wf = dao.find(Integer.parseInt(workflowId));
        if (wf != null) {
            wf.setCurrentState(state);
            dao.merge(wf);
        }
    }

    public void updateWorkFlowState(String workflowId, String state, Date date) {
        WorkFlow wf = dao.find(Integer.parseInt(workflowId));
        if (wf != null) {
            wf.setCurrentState(state);
            wf.setCompletionDate(date);
            dao.merge(wf);
        }
    }


    private static List<Map<String, Object>> toMapList(List<WorkFlow> ws) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (WorkFlow w : ws) {
            Map<String, Object> h = new Hashtable<String, Object>();
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

    public List<Map<String, Object>> getWorkFlowList(String workflowType) {
        return toMapList(dao.findByWorkflowType(workflowType));
    }

    public List<Map<String, Object>> getActiveWorkFlowList(String workflowType) {
        return toMapList(dao.findActiveByWorkflowType(workflowType));
    }

    public List<Map<String, Object>> getActiveWorkFlowList(String workflowType, String demographicNo) {
        return toMapList(dao.findActiveByWorkflowTypeAndDemographicNo(workflowType, demographicNo));
    }

    private static final Map<String, String> RH_STATES = Map.of(
            "1", "No Appt made",
            "2", "Appt Booked",
            "3", "Injection 28",
            "4", "Requires Another Injection",
            "5", "Missed Appt",
            "C", "Closed"
    );

    public static String rhState(Object s) {
        return RH_STATES.get((String) s);
    }
}
