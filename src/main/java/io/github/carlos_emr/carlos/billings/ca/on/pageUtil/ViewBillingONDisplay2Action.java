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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONDisplayViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code billing/CA/ON/billingONDisplay.jsp}. Enforces {@code _billing}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/} location, then delegates to
 * {@link BillingONDisplayDataAssembler} to produce a
 * {@link BillingONDisplayViewModel} exposed on the request as the
 * {@code displayModel} attribute (so the JSP body can be 100% EL/JSTL).
 *
 * <p>Created as part of the ON billing migration to gate direct-access paths
 * behind Struts2 actions (same pattern as PR #1632 for BC billing).</p>
 *
 * @since 2026-04-13
 */
public final class ViewBillingONDisplay2Action extends ActionSupport {

    // Dual-constructor DI: SpringUtils.getBean confined to the no-arg ctor.
    private final SecurityInfoManager securityInfoManager;
    private final BillingONDisplayDataAssembler assembler;

    private BillingONDisplayViewModel displayModel;

    /** Production constructor used by Struts2's Spring object factory. */
    public ViewBillingONDisplay2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
             new BillingONDisplayDataAssembler());
    }

    /** Test-friendly constructor — call with mocks. Package-private. */
    ViewBillingONDisplay2Action(SecurityInfoManager securityInfoManager,
                                BillingONDisplayDataAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        this.displayModel = assembler.assemble(request, loggedInInfo);
        request.setAttribute("displayModel", this.displayModel);
        return SUCCESS;
    }

    public BillingONDisplayViewModel getDisplayModel() {
        return displayModel;
    }
}
