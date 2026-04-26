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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.data.OnGenRAViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code billing/CA/ON/onGenRA.jsp}. Enforces {@code _billing}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/} location. Created as part of the ON billing migration
 * to gate direct-access paths behind Struts2 actions (same pattern as
 * PR #1632 for BC billing).
 *
 * <p>Also assembles the {@link OnGenRAViewModel} the JSP renders (RA-header
 * row list filtered by {@code _team_billing_only}, {@code _team_access_privacy},
 * or {@code _site_access_privacy} privacy flags). The view model is exposed
 * as request attribute {@code onGenRAModel}; the JSP body became 100% EL on
 * 2026-04-25.</p>
 *
 * @since 2026-04-13
 */
public final class ViewOnGenRA2Action extends ActionSupport {

    // Dual-constructor DI: SpringUtils.getBean confined to the no-arg ctor.
    private final SecurityInfoManager securityInfoManager;

    /** Production constructor used by Struts2's Spring object factory. */
    public ViewOnGenRA2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    /** Test-friendly constructor — call with mock. Package-private. */
    ViewOnGenRA2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Reject sessionless requests up front. SecurityInfoManagerImpl.hasPrivilege
        // dereferences loggedInInfo and emits an internal ERROR log on null, which
        // pollutes the log signal for real privilege denials.
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        OnGenRAViewModel model = new OnGenRADataAssembler(securityInfoManager).assemble(request);
        request.setAttribute("onGenRAModel", model);

        return SUCCESS;
    }
}
