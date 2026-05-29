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
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Mutation gate for {@code rx/AddRxComment.jsp}. Enforces {@code _rx} w
 * privilege AND POST-only before forwarding to the JSP. GET returns 405.
 *
 * @since 2026-04-13
 */
public final class ViewAddRxComment2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private PrescriptionDao prescriptionDao = SpringUtils.getBean(PrescriptionDao.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "w", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        String scriptNo = normalizePositiveId(request.getParameter("scriptNo"));
        String comment = request.getParameter("comment");
        if (comment == null || "null".equalsIgnoreCase(comment)) {
            return SUCCESS;
        }
        if (scriptNo == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        Prescription prescription = prescriptionDao.find(Integer.valueOf(scriptNo));
        if (prescription == null || !belongsToSessionPatient(request, prescription.getDemographicId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        prescriptionDao.updatePrescriptionsByScriptNo(Integer.valueOf(scriptNo), comment);
        return SUCCESS;
    }

    private static boolean belongsToSessionPatient(HttpServletRequest request, Integer demographicId) {
        Object sessionBean = request.getSession().getAttribute("RxSessionBean");
        return sessionBean instanceof RxSessionBean rxSessionBean
                && demographicId != null
                && demographicId == rxSessionBean.getDemographicNo();
    }

    private static String normalizePositiveId(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        try {
            int parsedValue = Integer.parseInt(rawValue.trim());
            return parsedValue > 0 ? Integer.toString(parsedValue) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
