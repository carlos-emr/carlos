//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.form.pageUtil;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.encounter.oscarMeasurements.util.WriteNewMeasurements;
import ca.openosp.openo.workflow.WorkFlowState;
import ca.openosp.openo.util.UtilDateUtilities;

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
        MiscUtils.getLogger().debug("FrmFormRHPrevention Action");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "w", null)) {
            throw new SecurityException("missing required sec object (_form)");
        }

        String providerNo = (String) request.getSession().getAttribute("user");
        String demographicNo = request.getParameter("demographic_no");
        String dateToParse = request.getParameter("end_date");
        String state = request.getParameter("state");
        String workflowId = request.getParameter("workflowId");

        WorkFlowState wfs = new WorkFlowState();


        if (workflowId != null) {
            //LOG CHANGE NOW
            MiscUtils.getLogger().debug("Changing workflow for  " + demographicNo + " to " + state);
            wfs.updateWorkFlowState(workflowId, state);

        } else {
            MiscUtils.getLogger().debug("New workflow for " + demographicNo + " EDD " + dateToParse);
            Date endDate = UtilDateUtilities.StringToDate(dateToParse);
            wfs.addToWorkFlow(WorkFlowState.RHWORKFLOW, providerNo, demographicNo, endDate, WorkFlowState.INIT_STATE);
        }

        String bloodType = request.getParameter("motherABO");
        String rhType = request.getParameter("motherRHtype");

        WriteNewMeasurements measurement = new WriteNewMeasurements();

        if (bloodType != null) {
            measurement.write("BLDT", bloodType, demographicNo, providerNo, new Date(), "");
        }
        if (rhType != null) {
            measurement.write("RHT", rhType, demographicNo, providerNo, new Date(), "");
        }


        request.setAttribute("demographic_no", demographicNo);
        return SUCCESS;
    }
}
