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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONMRIViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code billing/CA/ON/billingONMRI.jsp}, the "Generate OHIP
 * File" page (Medical Records Interchange / OHIP claim diskette — not
 * magnetic resonance imaging).
 *
 * <p>Enforces {@code _billing r} privilege, then assembles a
 * {@link BillingONMRIViewModel} via {@link BillingONMRIDataAssembler}
 * so the JSP can read pre-resolved records instead of doing 4 inline
 * {@code SpringUtils.getBean} lookups.</p>
 *
 * @since 2026-04-13
 */
public final class ViewBillingONMRI2Action extends ActionSupport {

    // Dual-constructor DI: SpringUtils.getBean confined to the no-arg ctor.
    private final SecurityInfoManager securityInfoManager;

    /** Production constructor used by Struts2's Spring object factory. */
    public ViewBillingONMRI2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    /** Test-friendly constructor — call with mock. Package-private. */
    ViewBillingONMRI2Action(SecurityInfoManager securityInfoManager) {
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

        BillingONMRIViewModel model = new BillingONMRIDataAssembler().assemble(request, loggedInInfo);
        request.setAttribute("mriModel", model);

        return SUCCESS;
    }
}
