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

import java.io.IOException;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Privilege + HTTP-method gate for clinical decision-support JSPs (annual
 * review and antenatal planners, checklists, risk editors).
 *
 * Every JSP in {@code /WEB-INF/jsp/decision/} is routed through this action.
 * All execution paths require {@code _form r}. When the request carries a
 * {@code submit} parameter whose trimmed value (case-insensitive, {@link
 * Locale#ROOT}) starts with "save", the action additionally requires the
 * request be POST and {@code _form w} -- this closes the GET-triggered
 * mutation vector the original JSPs presented, where scriptlet code would
 * persist {@code DesAnnualReviewPlan}/{@code Desaprisk} rows or overwrite
 * XML config files based solely on query-string parameters.
 *
 * The actual save logic remains in the JSP scriptlets (pending a future
 * refactor into dedicated save actions); this gate prevents the scriptlets
 * from running on an unauthenticated GET.
 *
 * <p><b>Coupling note:</b> the gate's "save" prefix detection must stay in
 * sync with the scriptlet-side {@code submit} checks (currently
 * {@code equals(" Save ")} and {@code equals("Save and Exit")}). If a JSP
 * is added that mutates under a different {@code submit} value -- or under
 * no {@code submit} value at all -- the gate will NOT block it. Prefer
 * extracting new save logic into a dedicated {@code *2Action} rather than
 * relying on this prefix heuristic.
 */
public final class ViewDecision2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null
                || !securityInfoManager.hasPrivilege(loggedInInfo, "_form", "r", null)) {
            throw new SecurityException("missing required sec object (_form r)");
        }

        String submit = request.getParameter("submit");
        if (submit != null && submit.trim().toLowerCase(Locale.ROOT).startsWith("save")) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                try {
                    response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                } catch (IOException e) {
                    // Response may already be committed (broken pipe, client abort).
                    // Log for observability; the gate has already prevented the scriptlet
                    // save path by returning NONE below.
                    logger.warn("Failed to send 405 on non-POST save attempt", e);
                }
                return NONE;
            }
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", "w", null)) {
                throw new SecurityException("missing required sec object (_form w)");
            }
        }

        return SUCCESS;
    }
}
