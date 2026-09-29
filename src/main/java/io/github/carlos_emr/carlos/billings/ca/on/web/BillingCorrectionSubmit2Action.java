/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionSubmitCommand;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionSubmitItemCommand;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionSubmissionService;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Struts boundary for committing a reviewed Ontario billing correction.
 */
public class BillingCorrectionSubmit2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final BillingCorrectionSubmissionService submissionService;

    public BillingCorrectionSubmit2Action(SecurityInfoManager securityInfoManager,
                                          BillingCorrectionSubmissionService submissionService) {
        this.securityInfoManager = securityInfoManager;
        this.submissionService = submissionService;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        try {
            submissionService.submit(loggedInInfo, toCommand(request));
            return SUCCESS;
        } catch (BillingValidationException e) {
            // Surface the validation message to the JSP, but do not log the
            // raw message because malformed hidden fields can contain PHI.
            MiscUtils.getLogger().warn("Billing correction submit rejected by validation: {}",
                    e.getClass().getSimpleName());
            request.setAttribute("correctionError", Boolean.TRUE);
            request.setAttribute("correctionErrorMessage", e.getMessage());
            return ERROR;
        }
    }

    private static BillingCorrectionSubmitCommand toCommand(HttpServletRequest request) {
        return new BillingCorrectionSubmitCommand(
                request.getParameter("billingNo"),
                request.getParameter("content"),
                request.getParameter("total"),
                request.getParameter("hin"),
                request.getParameter("dob"),
                request.getParameter("visitType"),
                request.getParameter("visitDate"),
                request.getParameter("status"),
                request.getParameter("clinicRefCode"),
                request.getParameter("providerNo"),
                request.getParameter("billingDate"),
                itemCommands(request));
    }

    private static List<BillingCorrectionSubmitItemCommand> itemCommands(HttpServletRequest request) {
        int count = parseItemCount(request.getParameter("itemCount"));
        List<BillingCorrectionSubmitItemCommand> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new BillingCorrectionSubmitItemCommand(
                    request.getParameter("serviceCode_" + i),
                    request.getParameter("description_" + i),
                    request.getParameter("serviceValue_" + i),
                    request.getParameter("diagCode_" + i),
                    request.getParameter("quantity_" + i)));
        }
        return items;
    }

    private static int parseItemCount(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new BillingValidationException("Billing correction rejected: invalid item count ["
                    + LogSafe.sanitizeForDisplay(raw) + "]", e);
        }
    }
}
