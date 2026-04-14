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
package io.github.carlos_emr.carlos.decision.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Privilege + HTTP-method gate for clinical decision-support JSPs (annual
 * review and antenatal planners, checklists, risk editors).
 *
 * Every JSP in {@code /WEB-INF/jsp/decision/} is routed through this action.
 * All execution paths require {@code _forms r}. When the request carries a
 * {@code submit} parameter starting with "Save" (indicating the user is
 * committing a checklist/risk/planner update), the action additionally
 * requires the request be POST and {@code _forms w} -- this closes the
 * GET-triggered mutation vector the original JSPs presented, where scriptlet
 * code would persist {@code DesAnnualReviewPlan}/{@code Desaprisk} rows or
 * overwrite XML config files based solely on query-string parameters.
 *
 * The actual save logic remains in the JSP scriptlets (pending a future
 * refactor into dedicated save actions); this gate prevents the scriptlets
 * from running on an unauthenticated GET.
 */
public final class ViewDecision2Action extends ActionSupport {

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        SecurityInfoManager sim = SpringUtils.getBean(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !sim.hasPrivilege(loggedInInfo, "_forms", "r", null)) {
            throw new SecurityException("missing required sec object (_forms r)");
        }

        String submit = request.getParameter("submit");
        if (submit != null && submit.trim().toLowerCase().startsWith("save")) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
            if (!sim.hasPrivilege(loggedInInfo, "_forms", "w", null)) {
                throw new SecurityException("missing required sec object (_forms w)");
            }
        }

        return SUCCESS;
    }
}
