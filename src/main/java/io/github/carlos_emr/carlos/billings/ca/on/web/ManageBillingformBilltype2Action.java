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
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.data.ManageBillingformBilltypeViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.ManageBillingformBilltypeDataAssembler;

/**
 * View gate for {@code billing/CA/ON/manageBillingform_billtype.jsp}. Enforces {@code _admin.billing}
 * {@code w} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/} location. Created as part of the ON billing migration
 * to gate direct-access paths behind Struts2 actions (same pattern as
 * PR #1632 for BC billing).
 *
 * <p>Also assembles the {@link ManageBillingformBilltypeViewModel} on the
 * request as {@code billtypeModel} so the JSP body can render in pure EL
 * (drained from 15 scriptlets in the round-2 billing-form refactor).</p>
 *
 * @since 2026-04-13
 */
public class ManageBillingformBilltype2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final ManageBillingformBilltypeDataAssembler assembler;

    /** Constructor injection used by Spring + Struts2's SpringObjectFactory. */
    @Autowired
    public ManageBillingformBilltype2Action(SecurityInfoManager securityInfoManager,
                                     ManageBillingformBilltypeDataAssembler assembler) {
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

        request.setAttribute("billtypeModel", assembler.assemble(request, loggedInInfo));
        return SUCCESS;
    }
}
