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
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.waitinglist.bean.WLPatientWaitingListBeanHandler;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action that prepares session data for displaying a patient's waiting list memberships.
 *
 * <p>Loads demographic information and all waiting list entries for the specified
 * patient, storing them in the session for the patient waiting list JSP view.
 * Requires {@code _demographic} read privilege.</p>
 *
 * @since 2026-03-17
 */
public final class WLSetupDisplayPatientWaitingList2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Loads the patient's demographic summary and waiting list entries into the session.
     *
     * <p>Reads the {@code demographic_no} request parameter, retrieves the patient's
     * name, sex, and age for display, then loads all waiting list memberships
     * via {@link WLPatientWaitingListBeanHandler}.</p>
     *
     * @return String "continue" on success
     * @throws RuntimeException if the logged-in user lacks {@code _demographic} read privilege
     * @throws Exception if an unexpected error occurs
     */
    public String execute()
            throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", null)) {
            throw new RuntimeException("missing required sec object (_demographic)");
        }

        String demographicNo = request.getParameter("demographic_no");
        DemographicData demoData = new DemographicData();
        Demographic demo = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demographicNo);
        String demoInfo = demo.getLastName() + ", " + demo.getFirstName() + " " + demo.getSex() + " " + demo.getAge();
        WLPatientWaitingListBeanHandler hd = new WLPatientWaitingListBeanHandler(demographicNo);
        HttpSession session = request.getSession();
        session.setAttribute("demoInfo", demoInfo);
        session.setAttribute("patientWaitingList", hd);
        session.setAttribute("demographicNo", demographicNo);

        return "continue";
    }
}
