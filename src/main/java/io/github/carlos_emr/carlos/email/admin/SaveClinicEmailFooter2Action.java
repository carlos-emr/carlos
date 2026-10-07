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
package io.github.carlos_emr.carlos.email.admin;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Saves the clinic's default email footer (Administration, Emails, Configure Email; issue #4093,
 * follow-up to #3981).
 *
 * <p>POST only, with {@code _admin} write; GET and HEAD get 405 before anything is read or saved.
 * Saving also applies the new default to users' own footers (see
 * {@link EmailFooterService#saveClinicDefault}); the users concerned are told on their next email,
 * so the administrator is not asked to confirm. A footer over the limit is refused and shown again
 * for editing. The audit entry records who changed the default, not the text.</p>
 *
 * @since 2026-10-07
 */
public final class SaveClinicEmailFooter2Action extends ActionSupport {

    static final String FOOTER_PARAM = "clinicFooter";

    private final SecurityInfoManager securityInfoManager;
    private final EmailFooterService emailFooterService;

    public SaveClinicEmailFooter2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(EmailFooterService.class));
    }

    // Package-private so tests can supply the collaborators.
    SaveClinicEmailFooter2Action(SecurityInfoManager securityInfoManager, EmailFooterService emailFooterService) {
        this.securityInfoManager = securityInfoManager;
        this.emailFooterService = emailFooterService;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        // HTTP method names are case-sensitive and upper case.
        if (!"POST".equals(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        String footer = request.getParameter(FOOTER_PARAM);
        try {
            emailFooterService.saveClinicDefault(footer);
        } catch (EmailFooterService.FooterTooLongException e) {
            request.setAttribute(FOOTER_PARAM, footer);
            request.setAttribute("clinicFooterTooLong", true);
            return INPUT;
        }
        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.UPDATE, "emailFooterClinicDefault", "",
                request.getRemoteAddr());
        response.sendRedirect(request.getContextPath() + "/admin/ViewConfigureEmail?clinicFooterSaved=true");
        return NONE;
    }
}
