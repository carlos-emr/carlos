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


package io.github.carlos_emr.carlos.form.pageUtil;

import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.WriteNewMeasurements;
import io.github.carlos_emr.carlos.workflow.WorkFlowState;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * @author jay
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class FrmFormAddRHWorkFlow2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        String demographicNo = RhFormRequestGuard.authorize(request, response, securityInfoManager);
        if (demographicNo == null) return NONE;
        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromSession(request);
        try {
            var transaction = new org.springframework.transaction.support.TransactionTemplate(
                    SpringUtils.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> saveInTransaction(demographicNo, info.getLoggedInProviderNo()));
            request.setAttribute("demographic_no", demographicNo);
            return SUCCESS;
        } catch (IllegalArgumentException invalid) {
            RhFormRequestGuard.reject(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid RH workflow selection. Reload the patient chart and try again.");
            return NONE;
        } catch (RuntimeException failure) {
            MiscUtils.getLogger().error("RH workflow save failed", failure);
            RhFormRequestGuard.reject(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "The RH form or workflow was not saved. Reload the patient chart before retrying.");
            return NONE;
        }
    }

    private void saveInTransaction(String demographicNo, String providerNo) {
        String workflowId = request.getParameter("workflowId");
        String state = request.getParameter("state");
        WorkFlowState workflows = new WorkFlowState();
        if (workflowId != null) {
            if (state == null || !java.util.Set.of("1", "2", "3", "4", "5", "C").contains(state)) {
                throw new IllegalArgumentException("Invalid RH state");
            }
            boolean owned = false;
            for (Object candidate : workflows.getActiveWorkFlowList(WorkFlowState.RHWORKFLOW, demographicNo)) {
                if (workflowId.equals(((java.util.Hashtable) candidate).get("ID"))) owned = true;
            }
            if (!owned) throw new IllegalArgumentException("RH workflow does not belong to this patient's active pregnancy");
            workflows.updateWorkFlowState(workflowId, state);
        } else {
            Date endDate = UtilDateUtilities.StringToDate(request.getParameter("end_date"));
            if (workflows.addToWorkFlow(WorkFlowState.RHWORKFLOW, providerNo, demographicNo,
                    endDate, WorkFlowState.INIT_STATE) <= 0) {
                throw new IllegalStateException("RH workflow was not created");
            }
        }
        String bloodType = request.getParameter("motherABO");
        String rhType = request.getParameter("motherRHtype");
        if (bloodType == null && rhType == null) return;
        WriteNewMeasurements measurements = new WriteNewMeasurements();
        if (bloodType != null) measurements.write("BLDT", bloodType, demographicNo, providerNo, new Date(), "");
        if (rhType != null) measurements.write("RHT", rhType, demographicNo, providerNo, new Date(), "");
    }
}
