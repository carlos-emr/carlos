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

import java.util.Date;
import java.util.Hashtable;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * Data transfer object representing the current state of a workflow instance, used as
 * a Drools fact object for rule evaluation.
 *
 * <p>Contains workflow metadata (type, state, dates, demographic) and rule-evaluated
 * properties such as {@code colour} that indicate the urgency or status of the workflow.
 * The {@link #getGestationAge()} method calculates gestational age from the completion
 * date for prenatal workflow rules.</p>
 *
 * @see WorkFlowDS
 * @see WorkFlow
 * @since 2026-03-17
 */
public class WorkFlowInfo {

    private String ID = null;
    private String workflowType = null;
    private Date createDateTime = null;
    private String demographicNo = null;
    private Date completionDate = null;
    private String currentState = null;
    private String colour = null;

    /**
     * Creates a new instance of WorkFlowInfo
     */
    public WorkFlowInfo() {
    }

    /**
     * Constructs a WorkFlowInfo populated from a workflow data map.
     *
     * @param h Hashtable the workflow data containing keys "ID", "workflow_type",
     *          "create_date_time", "demographic_no", "completion_date", "current_state"
     */
    public WorkFlowInfo(Hashtable h) {
        MiscUtils.getLogger().debug("loading data...");
        this.setID((String) h.get("ID"));
        this.setWorkflowType((String) h.get("workflow_type"));
        this.setCreateDateTime((Date) h.get("create_date_time"));
        this.setDemographicNo((String) h.get("demographic_no"));
        this.setCompletionDate((Date) h.get("completion_date"));
        this.setCurrentState((String) h.get("current_state"));
        MiscUtils.getLogger().debug("data loaded...");
    }

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getWorkflowType() {
        return workflowType;
    }

    public void setWorkflowType(String workflowType) {
        this.workflowType = workflowType;
    }

    public Date getCreateDateTime() {
        return createDateTime;
    }

    public void setCreateDateTime(Date createDateTime) {
        this.createDateTime = createDateTime;
    }

    public String getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    public Date getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(Date completionDate) {
        this.completionDate = completionDate;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String currentState) {
        this.currentState = currentState;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    /**
     * Checks whether this workflow instance is in the specified state.
     *
     * @param state String the state key to compare against
     * @return boolean {@code true} if the current state matches the specified state
     */
    public boolean isCurrentState(String state) {
        boolean is = false;
        if (state != null && currentState != null) {
            if (state.equals(currentState)) {
                is = true;
            }
        }
        return is;
    }

    /**
     * Calculates the gestational age in weeks based on the completion (due) date.
     *
     * <p>Used by prenatal workflow Drools rules to determine appropriate actions
     * based on gestational timing. Returns -1 if no completion date is set.</p>
     *
     * @return int the gestational age in weeks, or -1 if the completion date is null
     */
    public int getGestationAge() {
        //TODO: WHAT HAPPENS WITH NO EDD???
        int ret = -1;
        MiscUtils.getLogger().debug("GEST " + this.completionDate);
        if (this.completionDate != null) {
            ret = UtilDateUtilities.calculateGestationAge(new Date(), this.completionDate);
        }
        return ret;
    }


}
