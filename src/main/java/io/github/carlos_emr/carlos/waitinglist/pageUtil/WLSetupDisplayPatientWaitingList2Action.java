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


package io.github.carlos_emr.carlos.waitinglist.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.waitinglist.bean.WLPatientWaitingListBeanHandler;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class WLSetupDisplayPatientWaitingList2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", null)) {
            throw new RuntimeException("missing required sec object (_demographic)");
        }

        String rawDemographicNo = request.getParameter("demographic_no");
        if (rawDemographicNo == null || rawDemographicNo.trim().isEmpty()) {
            MiscUtils.getLogger().warn("WLSetupDisplayPatientWaitingList2Action: demographic_no parameter is missing");
            addActionError("demographic_no is required");
            return ERROR;
        }

        int demographicNoInt;
        try {
            demographicNoInt = Integer.parseInt(rawDemographicNo.trim());
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().warn("WLSetupDisplayPatientWaitingList2Action: non-numeric demographic_no='{}'",
                    LogSanitizer.sanitize(rawDemographicNo.trim()));
            addActionError("Invalid demographic_no: must be numeric");
            return ERROR;
        }

        if (demographicNoInt <= 0) {
            MiscUtils.getLogger().warn("WLSetupDisplayPatientWaitingList2Action: non-positive demographic_no={}",
                    demographicNoInt);
            addActionError("Invalid demographic_no: must be a positive integer");
            return ERROR;
        }

        // Use the validated integer string for all subsequent operations
        String demographicNo = String.valueOf(demographicNoInt);

        DemographicData demoData = new DemographicData();
        Demographic demo = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demographicNo);
        if (demo == null) {
            MiscUtils.getLogger().warn("WLSetupDisplayPatientWaitingList2Action: demographic not found for demographicNo={}",
                    demographicNoInt);
            addActionError("Demographic record not found");
            return ERROR;
        }

        String demoInfo = demo.getLastName() + ", " + demo.getFirstName() + " " + demo.getSex() + " " + demo.getAge();
        WLPatientWaitingListBeanHandler hd = new WLPatientWaitingListBeanHandler(demographicNo);
        HttpSession session = request.getSession();
        session.setAttribute("demoInfo", demoInfo);
        session.setAttribute("patientWaitingList", hd);
        session.setAttribute("demographicNo", demographicNo);

        return "continue";
    }
}
