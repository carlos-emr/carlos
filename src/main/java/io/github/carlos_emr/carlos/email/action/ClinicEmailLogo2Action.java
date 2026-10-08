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
package io.github.carlos_emr.carlos.email.action;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.EmailFooterLogo;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Serves the clinic's email footer logo (issue #3981) to the pages that show it beside a footer:
 * the email screen's Edit footer window ({@code _email} read) and Configure Email ({@code _admin}
 * read). Read only; GET and HEAD only. Answers 404 when the clinic has no logo.
 *
 * <p>Emails never link here: they carry the logo inside themselves.</p>
 *
 * @since 2026-10-08
 */
public class ClinicEmailLogo2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final EmailFooterLogoService logoService;

    /** Used by Struts, which needs a no-argument constructor. */
    public ClinicEmailLogo2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(EmailFooterLogoService.class));
    }

    // Package-private for tests.
    ClinicEmailLogo2Action(SecurityInfoManager securityInfoManager, EmailFooterLogoService logoService) {
        this.securityInfoManager = securityInfoManager;
        this.logoService = logoService;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_email)");
        }
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        EmailFooterLogo logo = logoService.currentLogo();
        if (logo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        byte[] image = logo.getImageData();
        // Only image/png or image/jpeg reach the table: the logo service re-saves every upload.
        response.setContentType(logo.getContentType());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "private, no-cache");
        response.setContentLength(image.length);
        if (!"HEAD".equals(request.getMethod())) {
            response.getOutputStream().write(image);
        }
        return NONE;
    }
}
