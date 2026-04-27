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

package io.github.carlos_emr.carlos.billings.ca.on.administration;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.GstReportDataAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.data.GstReportViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for the GST Report page. Enforces {@code _admin.billing w}
 * privilege, then delegates to {@link GstReportDataAssembler} to build
 * the view model the JSP renders. The model is exposed as request
 * attribute {@code gstReportModel}; the JSP body is 100% EL.
 *
 * @since 2026-04-05
 */
public final class GstReport2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final GstReportDataAssembler assembler;

    public GstReport2Action(SecurityInfoManager securityInfoManager,
                            GstReportDataAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        GstReportViewModel model = assembler.assemble(request, loggedInInfo);
        request.setAttribute("gstReportModel", model);
        return SUCCESS;
    }
}
