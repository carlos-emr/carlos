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

import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Mutation gate for {@code billing/CA/ON/billingResearchCodeUpdate.jsp}. Enforces {@code _billing}
 * w privilege AND POST-only before forwarding to the JSP. GET requests return
 * 405 Method Not Allowed.
 *
 * <p>The legacy JSP body iterated submitted form parameters whose names began
 * with {@code code_} and assembled up to three uppercase code suffixes that
 * the popup then injected back into the opener's form. That parameter scan
 * is now executed here so the JSP body can render pure EL/JSTL.</p>
 *
 * <p>Request attributes set:</p>
 * <ul>
 *   <li>{@code researchCode0} / {@code researchCode1} / {@code researchCode2}
 *       — the three code suffixes (uppercased) extracted from {@code code_*}
 *       parameter names. Empty strings when no value was selected for that
 *       slot.</li>
 *   <li>{@code researchCodeCount} — the number of {@code code_*} parameters
 *       found (0 means render the "No input selected" placeholder).</li>
 * </ul>
 *
 * @since 2026-04-13
 */
public class BillingResearchCodeUpdate2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public BillingResearchCodeUpdate2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // Mirror the legacy parameter-scan logic: collect all "code_*" names
        // (case-sensitive prefix match like the original .indexOf("code_")
        // != -1 test) and uppercase the suffix, capping at three slots.
        String[] codes = new String[] { "", "", "" };
        int count = 0;
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.indexOf("code_") == -1) {
                continue;
            }
            if (count < 3) {
                codes[count] = name.substring(5).toUpperCase();
            }
            count++;
        }

        request.setAttribute("researchCode0", codes[0]);
        request.setAttribute("researchCode1", codes[1]);
        request.setAttribute("researchCode2", codes[2]);
        request.setAttribute("researchCodeCount", count);

        return SUCCESS;
    }
}
