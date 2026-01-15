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


package ca.openosp.openo.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ca.openosp.openo.commn.dao.MeasurementGroupStyleDao;
import ca.openosp.openo.commn.model.MeasurementGroupStyle;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctDefineNewMeasurementGroup2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private MeasurementGroupStyleDao dao = SpringUtils.getBean(MeasurementGroupStyleDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {

        if (securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null) || securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.measurements", "w", null)) {
            EctValidation validate = new EctValidation();
            String regExp = validate.getRegCharacterExp();
            String contextPath = request.getContextPath();

            if (!validate.matchRegExp(regExp, groupName)) {
                addActionError(getText("errors.invalid", new String[]{groupName}));
                response.sendRedirect(contextPath + "/oscarEncounter/oscarMeasurements/DefineNewMeasurementGroup.jsp");
                return NONE;
            }

            //Write the new groupName to the database if there's no duplication
            if (!write2Database(groupName, styleSheet)) {
                addActionError(getText("error.oscarEncounter.addNewMeasurementGroup.duplicateGroupName", new String[]{groupName}));
                response.sendRedirect(contextPath + "/oscarEncounter/oscarMeasurements/DefineNewMeasurementGroup.jsp");
            }

            HttpSession session = request.getSession();
            session.setAttribute("groupName", groupName);

            return "continue";

        } else {
            throw new SecurityException("Access Denied!"); //missing required sec object (_admin)
        }
    }

    /*****************************************************************************************
     * Write the new groupName to the database if there's no duplication
     *
     * @return boolean
     ******************************************************************************************/
    private boolean write2Database(String inputGroupName, String styleSheet) {
        boolean isWrite2Database = true;

        for (MeasurementGroupStyle mgs : dao.findAll()) {
            String groupName = mgs.getGroupName();
            if (inputGroupName.compareTo(groupName) == 0) {
                isWrite2Database = false;
                break;
            }
        }

        if (isWrite2Database) {
            MeasurementGroupStyle mgs = new MeasurementGroupStyle();
            mgs.setGroupName(inputGroupName);
            try {
                mgs.setCssId(Integer.parseInt(styleSheet));
            } catch (NumberFormatException e) {
                //nothing
            }
            dao.persist(mgs);

        }

        return isWrite2Database;
    }

    private String groupName;

    public String getGroupName() {
        return this.groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    private String styleSheet;

    public String getStyleSheet() {
        return this.styleSheet;
    }

    public void setStyleSheet(String styleSheet) {
        this.styleSheet = styleSheet;
    }
}
