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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code billing/CA/ON/billingONUpload.jsp}. Enforces {@code _admin.billing}
 * w privilege then forwards to the JSP, which renders the file-picker form and
 * broader MOH file-management navigation.
 *
 * <p>The form's {@code onSubmit} JavaScript reroutes the multipart POST to either
 * {@code /servlet/io.github.carlos_emr.DocumentUploadServlet} (for "P"/"S"-prefixed
 * MOH diskette files) or {@code /oscarBilling/DocumentErrorReportUpload} (for error
 * reports). The error-report upload endpoint intentionally enforces the lower
 * {@code _billing w} privilege because billing-write operators reconcile MOH
 * return files directly. This action never receives the upload itself, so a POST
 * gate would just break the GET that renders the form.
 *
 * @since 2026-04-13
 */
public class BillingOnUpload2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public BillingOnUpload2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        return SUCCESS;
    }
}
