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


package io.github.carlos_emr.carlos.eform.actions;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.eform.EFormUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Struts2 action that adds an eForm to a named group. Requires {@code _eform}
 * write privilege.
 *
 * @see EFormUtil#addEFormToGroup(String, String)
 * @since 2006-05-25
 */
public class AddToGroup2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Adds the eForm identified by {@code fid} to the group identified by
     * {@code groupName}, then sets the group view attribute for the result page.
     *
     * @return String {@code SUCCESS} result name
     * @throws SecurityException if the user lacks {@code _eform} write privilege
     */
    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        //String fid = fm.getFid();
        //String groupName = fm.getGroupName();
        if (fid != null) {
            EFormUtil.addEFormToGroup(groupName, fid);
        }
        request.setAttribute("group_view", groupName);
        return SUCCESS;
    }
    private String fid;
    private String groupName;

    public String getFid() {
        return fid;
    }

    @StrutsParameter
    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getGroupName() {
        return groupName;
    }

    @StrutsParameter
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
}
