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


package io.github.carlos_emr.carlos.providers.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ProEditPhoneNum2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    // IMPROPER_UNICODE: case-insensitive comparison of the literal HTTP method name, not user-identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-insensitive comparison of the literal HTTP method name, not user-identity folding")
    public String execute() throws Exception {
        // Mutator: this action persists the provider's rxPhone (propertyDao.saveProp below), so it
        // MUST reject GET/HEAD before any side-effect fires (GET/HEAD Rejection Contract). CSRFGuard
        // does not validate GET, so without this a CSRF-via-GET could change the stored value.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            // No authenticated session — eject rather than NPE on the privilege/provider access below.
            return "eject";
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_pref", "w", null)) {
            throw new SecurityException("missing required sec object (_pref)");
        }

        // Reuse the session lookup above (a second getLoggedInInfoFromSession call could NPE here).
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        if (providerNo == null)
            return "eject";

        // Server-side validation. The browser-side validate() is bypassable via a direct POST, so the
        // value MUST be constrained here before it is stored and later rendered back into the page.
        // Allow only telephone punctuation and bound the length; reject anything else (e.g. markup),
        // which is both a data-quality and a stored-XSS defense.
        if (faxNumber != null && !faxNumber.matches("[0-9+()\\-.\\sxX]{0,40}")) {
            request.setAttribute("phoneError", Boolean.TRUE);
            return SUCCESS;
        }

        UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop = propertyDao.getProp(providerNo, "rxPhone");
        if (prop != null) {
            prop.setValue(faxNumber);
        } else {
            prop = new UserProperty();
            prop.setName("rxPhone");
            prop.setProviderNo(providerNo);
            prop.setValue(faxNumber);
        }
        propertyDao.saveProp(prop);
        request.setAttribute("status", "complete");
        return SUCCESS;
    }

    private String faxNumber;

    public String getFaxNumber() {
        return faxNumber;
    }

    @StrutsParameter
    public void setFaxNumber(String faxNumber) {
        this.faxNumber = faxNumber;
    }
}
