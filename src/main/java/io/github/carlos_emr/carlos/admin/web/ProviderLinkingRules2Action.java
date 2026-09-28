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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.lab.service.ProviderLinkingRulesService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for Administration &rarr; Labs/Inbox &rarr; Provider Linking Rules.
 *
 * <p>Requires {@code _admin} read, publishes the current switch state and whether the viewer may
 * change it, and forwards to {@code WEB-INF/jsp/admin/providerLinkingRules.jsp}. Changes are
 * posted to {@link SaveProviderLinkingRules2Action}; this route never writes, so it answers only
 * GET and HEAD.</p>
 *
 * @since 2026-09-26
 */
public class ProviderLinkingRules2Action extends ActionSupport {

    // transient: ActionSupport implements Serializable; Spring-managed beans are not serializable.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient ProviderLinkingRulesService providerLinkingRulesService;

    public ProviderLinkingRules2Action(SecurityInfoManager securityInfoManager,
                                       ProviderLinkingRulesService providerLinkingRulesService) {
        this.securityInfoManager = securityInfoManager;
        this.providerLinkingRulesService = providerLinkingRulesService;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            HttpServletResponse response = ServletActionContext.getResponse();
            response.setHeader("Allow", "GET, HEAD");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        request.setAttribute("providerLinkingRulesEnabled", providerLinkingRulesService.isEnabled());
        request.setAttribute("providerLinkingRulesCanWrite",
                securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null));
        request.setAttribute("providerLinkingRulesSaved", "true".equals(request.getParameter("saved")));
        return SUCCESS;
    }
}
