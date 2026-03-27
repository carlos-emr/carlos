/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.security;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This class handles actions related to Multi-Factor Authentication (MFA).
 * It provides functionality to reset the MFA secret for a user.
 */
public final class MfaActions2Action extends ActionSupport {

    private HttpServletRequest request = ServletActionContext.getRequest();
    private HttpServletResponse response = ServletActionContext.getResponse();

    public static final String METHOD_RESET_MFA = "resetMfa";

    private final SecurityManager securityManager = SpringUtils.getBean(SecurityManager.class);
    private final MfaManager mfaManager = SpringUtils.getBean(MfaManager.class);

    public String execute() {
        String method = request.getParameter("method");
        if (METHOD_RESET_MFA.equals(method)) {
            return resetMfa();
        }
        return SUCCESS;
    }

    /**
     * Resets the MFA secret for a specified user.
     *
     * @return null, as this action does not forward to another page.
     */
    public String resetMfa() {
        String securityId = request.getParameter("securityId");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Security security = this.securityManager.find(loggedInInfo, Integer.valueOf(securityId));
        this.mfaManager.resetMfaSecret(loggedInInfo, security);
        return null;
    }
}