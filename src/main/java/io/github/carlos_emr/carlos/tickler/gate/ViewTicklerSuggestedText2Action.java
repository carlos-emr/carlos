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
package io.github.carlos_emr.carlos.tickler.gate;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code tickler/ticklerSuggestedText.jsp}. Enforces {@code _tickler}
 * {@code w} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/tickler/} location. Part of the tickler-module JSP-gating migration (PR #1670).
 *
 * @since 2026-04-13
 */
public final class ViewTicklerSuggestedText2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Executes the privilege check and forwards to the gated JSP.
     *
     * @return {@code SUCCESS} when the caller holds {@code _tickler w};
     *         the package-level {@code global-exception-mappings} in
     *         {@code struts-scheduling.xml} routes the {@link SecurityException}
     *         thrown on denial to {@code /securityError.jsp}.
     * @throws SecurityException when the caller lacks {@code _tickler w}
     * @throws Exception if the underlying {@code ActionSupport.execute} contract
     *         declares one (not thrown by this implementation)
     * @since 2026-04-13
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "w", null)) {
            throw new SecurityException("missing required sec object (_tickler)");
        }

        return SUCCESS;
    }
}
