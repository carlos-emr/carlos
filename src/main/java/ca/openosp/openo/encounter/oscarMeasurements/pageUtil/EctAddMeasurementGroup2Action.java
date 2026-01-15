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
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.MeasurementGroupDao;
import ca.openosp.openo.commn.model.MeasurementGroup;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctAddMeasurementGroup2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private MeasurementGroupDao dao = SpringUtils.getBean(MeasurementGroupDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    public String execute()
            throws ServletException, IOException {

        if (securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null) || securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.measurements", "w", null)) {

            String groupName = this.getGroupName();
            request.getSession().setAttribute("groupName", groupName);

            String requestId = "";

            if (this.getForward() != null) {


                if (this.getForward().compareTo("add") == 0) {
                    MiscUtils.getLogger().debug("the add button is pressed");
                    String[] selectedAddTypes = this.getSelectedAddTypes();
                    if (selectedAddTypes != null) {
                        for (int i = 0; i < selectedAddTypes.length; i++) {
                            MiscUtils.getLogger().debug(selectedAddTypes[i]);
                            MeasurementGroup mg = new MeasurementGroup();
                            mg.setName(groupName);
                            mg.setTypeDisplayName(selectedAddTypes[i]);
                            dao.persist(mg);
                            requestId = mg.getId().toString();
                        }
                    }
                } else if (this.getForward().compareTo("delete") == 0) {
                    MiscUtils.getLogger().debug("the delete button is pressed");
                    String[] selectedDeleteTypes = this.getSelectedDeleteTypes();
                    if (selectedDeleteTypes != null) {
                        for (int i = 0; i < selectedDeleteTypes.length; i++) {
                            MiscUtils.getLogger().debug(selectedDeleteTypes[i]);
                            List<MeasurementGroup> mts = dao.findByNameAndTypeDisplayName(groupName, selectedDeleteTypes[i]);
                            for (MeasurementGroup mt : mts) {
                                dao.remove(mt.getId());
                            }
                        }
                    }
                }

            }

            return SUCCESS;

        } else {
            throw new SecurityException("Access Denied!"); //missing required sec object (_admin)
        }
    }
    private String[] selectedAddTypes;
    private String[] selectedDeleteTypes;
    private String forward;
    private String groupName;

    public String[] getSelectedAddTypes() {
        return selectedAddTypes;
    }

    public void setSelectedAddTypes(String[] selectedAddTypes) {
        this.selectedAddTypes = selectedAddTypes;
    }

    public String[] getSelectedDeleteTypes() {
        return selectedDeleteTypes;
    }

    public void setSelectedDeleteTypes(String[] selectedDeleteTypes) {
        this.selectedDeleteTypes = selectedDeleteTypes;
    }

    public String getForward() {
        return forward;
    }

    public void setForward(String forward) {
        this.forward = forward;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
}
