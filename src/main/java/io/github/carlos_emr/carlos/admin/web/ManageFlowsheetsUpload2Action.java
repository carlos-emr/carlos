/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.FlowsheetDao;
import io.github.carlos_emr.carlos.commn.model.Flowsheet;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Admin action for uploading a new flowsheet XML definition.
 *
 * <p>Requires any of {@code _admin r}, {@code _admin.misc r}, or {@code _admin.flowsheet r}
 * privilege and POST method. After the security and method checks, forwards to the JSP which
 * handles the multipart file upload. On completion the JSP redirects to the flowsheet list
 * (PRG pattern).</p>
 *
 * @since 2026-05-01
 */
public class ManageFlowsheetsUpload2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private FlowsheetDao flowsheetDao = SpringUtils.getBean(FlowsheetDao.class);

    @Override
    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "r", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.flowsheet", "r", null)) {
            throw new SecurityException("missing required sec object (_admin, _admin.misc, or _admin.flowsheet)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(405);
            return NONE;
        }

        return SUCCESS;
    }
}
