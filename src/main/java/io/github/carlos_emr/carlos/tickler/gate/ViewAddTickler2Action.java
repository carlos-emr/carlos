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
 * View gate for {@code tickler/ticklerAdd.jsp} (the Add Tickler form page).
 * Enforces {@code _tickler w} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/tickler/} location. Distinct from {@code AddTickler2Action}
 * which is the mutating submit handler; this action only renders the form.
 *
 * <p>The {@code w} privilege here deliberately matches the mutating handler:
 * there's no value in letting a read-only user render a form they can't submit.
 * The form rendered by {@code ticklerAdd.jsp} still POSTs to
 * {@code /tickler/AddTickler} (the existing mutating handler) — that endpoint
 * remains in place and is unchanged. This gate exists solely to serve GET
 * requests that open the add-tickler popup/form. See PR #1670.
 *
 * @since 2026-04-13
 */
public final class ViewAddTickler2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Executes the privilege check and forwards to the gated JSP.
     *
     * @return {@code SUCCESS} when the caller holds {@code _tickler w};
     *         the package-level {@code global-exception-mappings} in
     *         {@code struts-scheduling.xml} routes the {@link SecurityException}
     *         thrown on denial to {@code /securityError}.
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
            throw new SecurityException("missing required security object: _tickler");
        }

        return SUCCESS;
    }
}
