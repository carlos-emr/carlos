/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.gate;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code oscarMDS/CreateLab.jsp}. Enforces {@code _lab}
 * {@code w} privilege before forwarding to the JSP. Separates the GET
 * (display form) path from the POST (submit form) path handled by
 * {@code SubmitLabByForm2Action} at {@code oscarMDS/SubmitLab}.
 *
 * @since 2026-05-01
 */
public final class ViewCreateLab2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    
    /**
     * Enforces the create-lab view gate and forwards to the configured success result.
     *
     * `@return` {`@link` `#SUCCESS`} when the caller has required lab write privilege
     * `@throws` Exception when action processing fails
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing LoggedInInfo");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        return SUCCESS;
    }
}
