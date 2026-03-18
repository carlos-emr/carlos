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


package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class EctEditMeasurementStyle2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private MeasurementGroupStyleDao dao = SpringUtils.getBean(MeasurementGroupStyleDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {

        if (securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null) || securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.measurements", "w", null)) {

            changeCSS(groupName, styleSheet);

            MiscUtils.getLogger().debug("The selected style sheet is: " + styleSheet);
            HttpSession session = request.getSession();
            session.setAttribute("groupName", groupName);

            return "continue";

        } else {
            throw new SecurityException("Access Denied!"); //missing required sec object (_admin)
        }

    }

    /*****************************************************************************************
     * change CSSID to the associated group
     *
     * @return
     ******************************************************************************************/
    private void changeCSS(String inputGroupName, String styleSheet) {

        for (MeasurementGroupStyle m : dao.findByGroupName(inputGroupName)) {
            m.setId(Integer.parseInt(styleSheet));
            dao.merge(m);
        }

    }


    private String groupName;

    public String getGroupName() {
        return this.groupName;
    }

    @StrutsParameter
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    private String styleSheet;

    public String getStyleSheet() {
        return this.styleSheet;
    }

    @StrutsParameter
    public void setStyleSheet(String styleSheet) {
        this.styleSheet = styleSheet;
    }

}
