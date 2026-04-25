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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONStatusViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code billing/CA/ON/billingONStatus.jsp}. Enforces
 * {@code _billing r} for page access and exposes a
 * {@link BillingONStatusViewModel} on the request at attribute
 * {@code statusModel} so the JSP can read request parameter echoes +
 * default-value resolution via EL.
 *
 * <p>{@code _team_billing_only} is consulted separately as a *filter flag*
 * (does this user see only their team's bills?) and is captured on the view
 * model rather than being a page-access gate. Earlier docs/code on this gate
 * incorrectly used {@code _team_billing_only} as the access privilege; that
 * was relaxed to {@code _billing r} to match the companion mutation action
 * {@code BillingONStatusERUpdateStatus2Action}.</p>
 *
 * <p>Parameter parsing + default resolution is delegated to
 * {@link BillingONStatusDataAssembler} so this action stays a thin gate.</p>
 *
 * @since 2026-04-13
 */
public final class ViewBillingONStatus2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    private BillingONStatusViewModel statusModel;

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Reject sessionless requests up front. SecurityInfoManagerImpl.hasPrivilege
        // dereferences loggedInInfo and emits an internal ERROR log on null, so
        // null-checking here keeps the log signal clean for real privilege denials.
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }

        // _team_billing_only is a filter flag in the JSP (does the user see only
        // their team's bills?), NOT a page-access gate. The page access privilege
        // matches the companion BillingONStatusERUpdateStatus2Action: _billing.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        this.statusModel = new BillingONStatusDataAssembler().assemble(request, loggedInInfo);
        request.setAttribute("statusModel", this.statusModel);
        return SUCCESS;
    }

    public BillingONStatusViewModel getStatusModel() {
        return statusModel;
    }
}
