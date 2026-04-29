/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionSubmitCommand;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionSubmitItemCommand;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionSubmissionService;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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
            MiscUtils.getLogger().warn("Billing correction submit rejected", e);
            request.setAttribute("correctionError", Boolean.TRUE);
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
            throw new BillingValidationException("Billing correction rejected: invalid item count [" + raw + "]", e);
        }
    }
}
