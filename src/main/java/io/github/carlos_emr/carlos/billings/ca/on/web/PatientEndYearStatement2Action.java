/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Initial-render gate for {@code /billing/CA/ON/endYearStatement}. Renders
 * the empty form (with optional first/last-name echoes) and clears any
 * stale {@code summary} from the session so the JSP doesn't show a
 * leftover invoice table from a previous run.
 *
 * <p>The three workflow paths the legacy action used to dispatch on
 * (search / pdf / demosearch) now have their own action classes and
 * URLs:</p>
 * <ul>
 *   <li>{@link SearchEndYearStatement2Action} —
 *       {@code endYearStatement/search}</li>
 *   <li>{@link PrintEndYearStatementPdf2Action} —
 *       {@code endYearStatement/pdf}</li>
 *   <li>{@link DemoSearchEndYearStatement2Action} —
 *       {@code endYearStatement/demosearch}</li>
 * </ul>
 *
 * @since 2026-04-26 (refactor)
 */
public class PatientEndYearStatement2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public PatientEndYearStatement2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        PatientEndYearStatements.echoNames(request);
        // Clear any stale summary so the JSP renders the empty form.
        request.getSession().setAttribute("summary", null); // nosemgrep: tainted-session-from-http-request -- clearing session attribute with null
        return SUCCESS;
    }
}
