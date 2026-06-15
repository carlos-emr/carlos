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

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Deletes an eForm template from the library.
 *
 * <p>Two privilege tiers govern deletion:
 * <ul>
 *   <li><b>Admin</b> — providers with the {@code _eform} delete privilege ("d") may delete
 *       any template, including shared templates with no recorded creator.</li>
 *   <li><b>Provider</b> — providers with the {@code _eform} write privilege ("w") may delete
 *       only templates they personally created (matched by {@code form_creator}).</li>
 * </ul>
 * Shared templates — those whose {@code form_creator} is null or blank — are org-wide
 * assets and are intentionally restricted to admin-level deletion.
 *
 * @since 2026-06-15
 */
public class DelEForm2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final EFormDao eFormDao;

    public DelEForm2Action(SecurityInfoManager securityInfoManager, EFormDao eFormDao) {
        this.securityInfoManager = securityInfoManager;
        this.eFormDao = eFormDao;
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
        if (StringUtils.isBlank(fid) || !StringUtils.isNumeric(fid)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid fid");
            return NONE;
        }
        int formId = Integer.parseInt(fid);

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        boolean isAdmin = securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.DELETE, null);

        if (!isAdmin) {
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.WRITE, null)) {
                throw new SecurityException("missing required sec object (_eform)");
            }
            EForm eform = eFormDao.findById(formId);
            if (eform == null) {
                throw new SecurityException("missing required sec object (_eform)");
            }
            String creator = eform.getCreator();
            String providerNo = loggedInInfo.getLoggedInProviderNo();
            // Shared templates (no creator) and other providers' forms are admin-only
            if (StringUtils.isBlank(creator) || !creator.equals(providerNo)) {
                throw new SecurityException("missing required sec object (_eform)");
            }
        }

        EFormUtil.delEForm(fid);
        return SUCCESS;
    }
}
