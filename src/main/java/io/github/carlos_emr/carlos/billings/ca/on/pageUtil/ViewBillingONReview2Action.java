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
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONReviewViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Mutation gate for {@code billing/CA/ON/billingONReview.jsp}. The JSP
 * historically performed a {@code dxresearchDao.save(dx)} side-effect inside
 * its top scriptlet when {@code addToPatientDx=yes} was posted; that write is
 * now done by {@link BillingONReviewDataAssembler} (driven from this action),
 * so the JSP remains a presentation layer over the resulting view model.
 *
 * <p>Enforces {@code _billing} w privilege AND POST-only before forwarding to
 * the JSP. GET requests return 405. The class name retains the {@code View...}
 * prefix for consistency with sibling gate actions in this migration, even
 * though the behavior is mutation-gate.</p>
 *
 * @since 2026-04-13
 *        2026-04-24 (view-model assembly + dx side-effect migration)
 */
public final class ViewBillingONReview2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    private BillingONReviewViewModel reviewModel;

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // The assembler may persist a Dxresearch row when addToPatientDx=yes.
        // Refuse to proceed if we can't attach that row to a real provider —
        // an empty providerNo creates an audit-trail gap for clinical data.
        String userNo = loggedInInfo.getLoggedInProviderNo();
        if (userNo == null || userNo.isEmpty()) {
            throw new SecurityException("missing provider in session");
        }
        this.reviewModel = new BillingONReviewDataAssembler().assemble(request, userNo);
        request.setAttribute("reviewModel", this.reviewModel);

        return SUCCESS;
    }

    public BillingONReviewViewModel getReviewModel() {
        return reviewModel;
    }
}
