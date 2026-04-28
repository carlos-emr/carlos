/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingCorrectionLineCommand;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingCorrectionReviewViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingCorrectionValidationCommand;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionReviewPreparationService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Struts boundary for preparing the ON billing correction review.
 */
public class BillingCorrectionValid2Action extends ActionSupport {

    static final String REVIEW = "review";

    private final SecurityInfoManager securityInfoManager;
    private final BillingCorrectionReviewPreparationService preparationService;

    public BillingCorrectionValid2Action(SecurityInfoManager securityInfoManager,
                                         BillingCorrectionReviewPreparationService preparationService) {
        this.securityInfoManager = securityInfoManager;
        this.preparationService = preparationService;
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

        BillingCorrectionReviewViewModel model = preparationService.prepareReview(toCommand(request));
        request.setAttribute("reviewModel", model);
        return REVIEW;
    }

    private static BillingCorrectionValidationCommand toCommand(HttpServletRequest request) {
        return new BillingCorrectionValidationCommand(
                request.getParameter("xml_diagnostic_detail"),
                request.getParameter("rd"),
                nullToEmpty(request.getParameter("roster")),
                request.getParameter("m_review") != null,
                request.getParameter("rdohip"),
                request.getParameter("referral") != null,
                request.getParameter("hc_type"),
                request.getParameter("hc_sex"),
                request.getParameter("specialty"),
                xmlParameters(request),
                serviceLines(request),
                request.getParameter("xml_billing_no"),
                request.getParameter("hin"),
                request.getParameter("dob"),
                request.getParameter("visittype"),
                request.getParameter("xml_vdate"),
                request.getParameter("status"),
                request.getParameter("clinic_ref_code"),
                request.getParameter("provider_no"),
                request.getParameter("xml_appointment_date"),
                request.getParameter("update_date"),
                request.getParameter("demo_name"),
                request.getParameter("demo_address"),
                request.getParameter("demo_province"),
                request.getParameter("demo_city"),
                request.getParameter("demo_postal"),
                request.getParameter("demo_sex"));
    }

    private static Map<String, String> xmlParameters(HttpServletRequest request) {
        Map<String, String> values = new LinkedHashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.contains("xml_")) {
                values.put(name, request.getParameter(name));
            }
        }
        return values;
    }

    private static List<BillingCorrectionLineCommand> serviceLines(HttpServletRequest request) {
        List<BillingCorrectionLineCommand> lines = new ArrayList<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!name.startsWith("servicecode")) {
                continue;
            }
            String suffix = name.substring("servicecode".length());
            lines.add(new BillingCorrectionLineCommand(
                    request.getParameter(name),
                    request.getParameter("billingunit" + suffix),
                    request.getParameter("billingamount" + suffix)));
        }
        return lines;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
