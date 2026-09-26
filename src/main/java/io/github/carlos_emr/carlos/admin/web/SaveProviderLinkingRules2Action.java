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
 *
 * Provider linking rules were first implemented by Deval Italiya in
 * open-osp/Open-O pull request #196 (GPL); this CARLOS implementation is
 * adapted from that work.
 */
package io.github.carlos_emr.carlos.admin.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.lab.service.ProviderLinkingRulesService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Saves the clinic-wide Provider Linking Rules switch.
 *
 * <p>POST only: GET and HEAD answer 405 before authorization or any write, because CSRFGuard does
 * not protect GET and the switch changes who receives lab and HRM results clinic-wide. Requires
 * {@code _admin} write (the service checks again and audits the change). The form sends
 * {@code enabled=true} when the switch is on and nothing when it is off, as an unchecked HTML
 * checkbox does. On success the browser is redirected back to the view (post/redirect/get), so a
 * reload cannot resubmit.</p>
 *
 * @since 2026-09-26
 */
public class SaveProviderLinkingRules2Action extends ActionSupport {

    // transient: ActionSupport implements Serializable; Spring-managed beans are not serializable.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient ProviderLinkingRulesService providerLinkingRulesService;

    public SaveProviderLinkingRules2Action(SecurityInfoManager securityInfoManager,
                                           ProviderLinkingRulesService providerLinkingRulesService) {
        this.securityInfoManager = securityInfoManager;
        this.providerLinkingRulesService = providerLinkingRulesService;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        providerLinkingRulesService.setEnabled(loggedInInfo, "true".equals(request.getParameter("enabled")));
        response.sendRedirect(request.getContextPath() + "/admin/providerLinkingRules?saved=true");
        return NONE;
    }
}
