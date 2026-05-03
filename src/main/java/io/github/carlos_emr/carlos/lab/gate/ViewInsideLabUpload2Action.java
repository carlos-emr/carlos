/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * View gate for {@code lab/CA/ALL/testUploader.jsp} (the HL7 Lab Upload
 * page). Enforces {@code _lab w} privilege and forwards to the JSP on
 * GET/HEAD.
 *
 * <p>Separates the page-display path from the multipart upload submit
 * path handled by {@link io.github.carlos_emr.carlos.lab.ca.all.pageUtil.InsideLabUpload2Action}
 * at {@code lab/CA/ALL/insideLabUpload}. Routing plain page loads through
 * the upload action caused intermittent failures because every GET fell
 * through that action's "no files uploaded" branch and was forwarded as
 * an INPUT result of a multipart-stack action — same separation pattern
 * applied for the Create Lab page in PR #2037 (issue #2008).
 *
 * @since 2026-05-03
 */
public final class ViewInsideLabUpload2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Enforces the HL7 lab upload view gate and forwards to the configured
     * success result.
     *
     * @return {@link #SUCCESS} when the caller is authenticated and has
     *         {@code _lab w}; {@link #NONE} after sending {@code 405} when
     *         the request is not GET/HEAD
     * @throws Exception when action processing fails
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "w", null)) {
            throw new SecurityException("missing required security object for lab/CA/ALL/insideLabUpload");
        }
        return SUCCESS;
    }
}
