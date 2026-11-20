/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.eform.actions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.eform.EFormUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Restores a previously deleted eForm template to the live library.
 *
 * <p>POST-only; requires the {@code _eform} write privilege. The {@code fid} parameter must be a
 * positive integer, otherwise the request is rejected with 400 rather than silently restoring
 * nothing (issue #3571).
 */
public class RestoreEForm2Action extends ActionSupport {

    // transient: ActionSupport implements Serializable; Spring-managed beans are not serializable.
    private final transient SecurityInfoManager securityInfoManager;

    public RestoreEForm2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an HTTP method constant; not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an HTTP method constant; not a security or authorization decision")
    @Override
    public String execute() throws java.io.IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        // Restoring an eForm puts it back in the live library, so it is a mutation and must not
        // be reachable by GET. CSRFGuard validates the token on the POST body / XHR header and
        // does not cover GET, so without this an <img src=".../eform/restoreEForm?fid=42"> on any
        // page an eForm administrator loads silently restores that form. Giving the JSP its CSRF
        // token was only half the fix: the token protects the intended path, this closes the one
        // that bypasses it. The delete sibling (DelEForm2Action) has carried this guard since it
        // was fixed; restore was missed.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        // EFormUtil.restoreEForm parses fid with a lenient converter that maps non-numeric input
        // to 0, so an unvalidated "abc" would restore nothing yet report success. Require a
        // positive integer here so a malformed request fails loudly.
        String fid = request.getParameter("fid");
        Integer formId = parsePositiveInteger(fid);
        if (formId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid fid");
            return NONE;
        }

        EFormUtil.restoreEForm(String.valueOf(formId));
        return SUCCESS;
    }

    /** @return the parsed id, or null when the value is blank, non-numeric, overflows, or is not positive */
    private static Integer parsePositiveInteger(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
