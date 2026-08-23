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
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform.actions;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Deletes an eForm template from the library.
 *
 * <p>Deletion requires the {@code _admin.eform} write privilege, which is held by
 * admin-level providers. The doctor role carries only {@code _eform} write and
 * cannot delete templates.
 *
 * @since 2026-06-15
 */
public class DelEForm2Action extends ActionSupport {

    // transient: ActionSupport implements Serializable; Spring-managed beans are not serializable.
    // Actions are prototype-scoped and never actually serialized, but transient satisfies the contract.
    private final transient SecurityInfoManager securityInfoManager;

    public DelEForm2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an HTTP method constant; not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an HTTP method constant; not a security or authorization decision")
    @Override
    public String execute() throws java.io.IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        String fid = request.getParameter("fid");
        if (StringUtils.isBlank(fid)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid fid");
            return NONE;
        }
        try {
            Integer.parseInt(fid);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid fid");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.eform", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin.eform)");
        }

        EFormUtil.delEForm(fid);
        return SUCCESS;
    }
}
