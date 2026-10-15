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
package io.github.carlos_emr.carlos.prescript.gate;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData;
import io.github.carlos_emr.carlos.prescript.data.RxInteractionWarnings;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver;
import io.github.carlos_emr.carlos.prescription.util.DrugrefUtil;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Returns patient-scoped interaction warnings as JSON for the staged prescription cards.
 * Lookup failures are explicit; no executable response fragments or clinical details are logged.
 */
public final class RxInteractionWarnings2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public RxInteractionWarnings2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    RxInteractionWarnings2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Admits the request to its view only with global {@code _rx} read and, when the request names a
     * patient ({@code demographicNo} / {@code demographic_no}), the same privilege for that patient plus access to
     * the patient's record ({@link RxRequestedPatientAccess#require}). A malformed or conflicting patient is refused.
     *
     * @return {@code NONE} after the JSON response or explicit error
     * @throws SecurityException when the caller may not view the module or the named patient
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        if (RxSessionBeanResolver.requestedDemographicNo(request) <= 0) {
            ServletActionContext.getResponse().sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        RxRequestedPatientAccess.require(securityInfoManager, loggedInInfo, request, "_rx", "r");

        HttpServletResponse response = ServletActionContext.getResponse();
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        RxSessionBean bean = RxRequestedPatientAccess.resolveAuthorised(request, "_rx", "r");
        if (bean == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        Map<String, Object> result;
        try {
            int minimum = new DrugrefUtil().getWarningLevel(loggedInInfo, bean.getDemographicNo());
            Vector<String> codes = new Vector<>();
            for (Object value : bean.getAtcCodes()) {
                if (value instanceof String code && RxInteractionWarnings.usableCode(code) && !codes.contains(code)) {
                    codes.add(code);
                }
            }
            RxDrugData.Interaction[] interactions = minimum == 4 || codes.size() < 2
                    ? new RxDrugData.Interaction[0] : new RxDrugData().getInteractions(codes);
            result = Map.of("warnings", RxInteractionWarnings.forStash(bean.getStash(), interactions, minimum),
                    "checkFailed", false);
        } catch (Exception failure) {
            MiscUtils.getLogger().warn("Drug interaction check failed; failureType={}", failure.getClass().getSimpleName());
            result = Map.of("warnings", List.of(), "checkFailed", true);
        }
        new ObjectMapper().writeValue(response.getWriter(), result);
        return NONE;
    }
}
