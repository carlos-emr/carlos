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


package ca.openosp.openo.encounter.immunization.config.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.encounter.immunization.config.data.EctImmImmunizationSetData;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctImmInitConfigDeleteImmuSet2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        EctImmImmunizationSetData aSet = new EctImmImmunizationSetData();

        String[] strId = this.getChkSetId();
        int stat = this.getAction().equals("Delete") ? 2 : 0; // 2-delete, 0-undelete
        if (strId != null && strId.length > 0) {
            for (int i = 0; i < strId.length; i++) {
                aSet.updateImmunizationSetStatus(strId[i], stat);
            }
        }

        if (stat == 0) request.setAttribute("stat", "2"); // like ?stat=2

        return SUCCESS;
    }

    String action;
    String[] chkSetId;

    /**
     * @return Returns the action.
     */
    public String getAction() {
        if (action == null) action = "";
        return action;
    }

    public String[] getChkSetId() {
        if (chkSetId == null)
            chkSetId = new String[]{
            };
        return chkSetId;
    }

    /**
     * @param action The action to set.
     */
    public void setAction(String action) {
        this.action = action;
    }

    public void setChkSetId(String[] str) {
        chkSetId = str;
    }
}
