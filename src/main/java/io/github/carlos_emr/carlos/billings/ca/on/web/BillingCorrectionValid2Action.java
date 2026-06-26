/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCorrectionReviewViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionLineCommand;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionValidationCommand;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionReviewPreparationService;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCorrectionReviewDraft;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCorrectionReviewViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Struts boundary for preparing the ON billing correction review.
 */
public class BillingCorrectionValid2Action extends ActionSupport {

    static final String REVIEW = "review";

    private final SecurityInfoManager securityInfoManager;
    private final BillingCorrectionReviewPreparationService preparationService;
    private final BillingCorrectionReviewViewModelAssembler reviewViewModelAssembler;

    public BillingCorrectionValid2Action(SecurityInfoManager securityInfoManager,
                                         BillingCorrectionReviewPreparationService preparationService,
                                         BillingCorrectionReviewViewModelAssembler reviewViewModelAssembler) {
        this.securityInfoManager = securityInfoManager;
        this.preparationService = preparationService;
        this.reviewViewModelAssembler = reviewViewModelAssembler;
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
            BillingCorrectionReviewDraft draft = preparationService.prepareReviewDraft(toCommand(request));
            BillingCorrectionReviewViewModel model = reviewViewModelAssembler.assemble(draft);
            request.setAttribute("reviewModel", model);
            return REVIEW;
        } catch (BillingValidationException e) {
            MiscUtils.getLogger().warn("Billing correction review rejected by validation: {}",
                    e.getClass().getSimpleName());
            request.setAttribute("correctionError", Boolean.TRUE);
            request.setAttribute("correctionErrorMessage", e.getMessage());
            return ERROR;
        }
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
                firstNonBlank(request.getParameter("xml_dob"), request.getParameter("dob")),
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
            if (name.startsWith("xml_")) {
                values.put(name, request.getParameter(name));
            }
        }
        return values;
    }

    private static List<BillingCorrectionLineCommand> serviceLines(HttpServletRequest request) {
        Map<Integer, String> serviceCodeParams = new TreeMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!name.startsWith("servicecode")) {
                continue;
            }
            String suffix = name.substring("servicecode".length());
            try {
                serviceCodeParams.put(Integer.parseInt(suffix), suffix);
            } catch (NumberFormatException ignored) {
                // Ignore non-indexed helper params; the JSP emits numeric suffixes.
            }
        }
        List<BillingCorrectionLineCommand> lines = new ArrayList<>();
        for (String suffix : serviceCodeParams.values()) {
            String serviceCode = request.getParameter("servicecode" + suffix);
            String billingUnit = request.getParameter("billingunit" + suffix);
            String billingAmount = request.getParameter("billingamount" + suffix);
            if (isBlank(serviceCode) && isBlank(billingUnit) && isBlank(billingAmount)) {
                continue;
            }
            lines.add(new BillingCorrectionLineCommand(serviceCode, billingUnit, billingAmount));
        }
        return lines;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return isBlank(primary) ? fallback : primary;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
