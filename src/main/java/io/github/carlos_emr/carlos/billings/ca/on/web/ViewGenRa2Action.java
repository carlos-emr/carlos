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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.DocumentBean;
import io.github.carlos_emr.carlos.billings.ca.on.service.OnRaImportService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Legacy URL alias for {@code billing/CA/ON/ViewOnGenRA} (admin menu's
 * 'genRA' link). Enforces {@code _billing r} privilege, then the struts
 * mapping chains to the canonical reconciliation action. Preserves the
 * {@code ViewGenRA} URL contract carried forward from the pre-migration era.
 *
 * @since 2026-04-13
 */
public class ViewGenRa2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final OnRaImportService importService;

    public ViewGenRa2Action(SecurityInfoManager securityInfoManager,
                            OnRaImportService importService) {
        this.securityInfoManager = securityInfoManager;
        this.importService = importService;
    }
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (request.getAttribute("documentBean") instanceof DocumentBean) {
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
                throw new SecurityException("missing required sec object (_billing)");
            }
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                HttpServletResponse response = ServletActionContext.getResponse();
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
            OnRaImportService.ImportOutcome outcome = importService.importDocumentBeanFileOutcome(request);
            if (!outcome.ok()) {
                request.setAttribute("raImportFailed", Boolean.TRUE);
                request.setAttribute("raImportOutcome", outcome.name());
            }
            return SUCCESS;
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        return SUCCESS;
    }
}
