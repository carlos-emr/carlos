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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.eform.EFormUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action that creates a new eForm group. Creates the group by adding
 * a marker entry (fid=0) to the group table. Requires {@code _eform} write privilege.
 *
 * @see EFormUtil#addEFormToGroup(String, String)
 * @since 2006-05-25
 */
public class AddGroup2Action extends ActionSupport {
    private HttpServletRequest request = ServletActionContext.getRequest();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Creates a new eForm group with the specified {@code groupName}.
     *
     * @return String {@code SUCCESS} result name
     * @throws SecurityException if the user lacks {@code _eform} write privilege
     */
    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }
        EFormUtil.addEFormToGroup(groupName, "0");  //marker for group
        request.setAttribute("group_view", groupName);
        return SUCCESS;
    }

    private String groupName;

    @StrutsParameter
    public java.lang.String getGroupName() {
        return groupName;
    }

    @StrutsParameter
    public void setGroupName(java.lang.String groupName) {
        this.groupName = groupName;
    }
}
