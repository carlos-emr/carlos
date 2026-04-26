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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.data.ManageBillingformViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.ManageBillingformDataAssembler;

/**
 * View gate for {@code billing/CA/ON/manageBillingform.jsp}. Enforces {@code _admin.billing}
 * {@code w} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/} location. Created as part of the ON billing migration
 * to gate direct-access paths behind Struts2 actions (same pattern as
 * PR #1632 for BC billing).
 *
 * <p>Also assembles the {@link ManageBillingformViewModel} on the request as
 * {@code manageBillingformModel} so the JSP body can render in pure EL
 * (drained from 16 scriptlets in the round-2 billing-form refactor).</p>
 *
 * @since 2026-04-13
 */
public final class ManageBillingform2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final ManageBillingformDataAssembler assembler;

    /** Production constructor used by Struts2's Spring object factory. */
    public ManageBillingform2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
             new ManageBillingformDataAssembler());
    }

    /** Test-friendly constructor — call with mocks. Package-private. */
    ManageBillingform2Action(SecurityInfoManager securityInfoManager,
                             ManageBillingformDataAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        request.setAttribute("manageBillingformModel", assembler.assemble(request));
        return SUCCESS;
    }
}
