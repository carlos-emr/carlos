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
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Objects;

/**
 * Mutation gate for prescription Additional Notes. Enforces {@code _rx} w,
 * prescription ownership, and POST-only. GET returns 405.
 *
 * @since 2026-04-13
 */
public final class ViewAddRxComment2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private PrescriptionDao prescriptionDao = SpringUtils.getBean(PrescriptionDao.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        // Establish the coarse role before parsing attacker-controlled parameters. Unauthorized
        // callers must receive the same denial whether those parameters are valid or malformed.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        int scriptNo;
        try {
            scriptNo = Integer.parseInt(request.getParameter("scriptNo"));
        } catch (NumberFormatException | NullPointerException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        String comment = request.getParameter("comment");
        if (scriptNo <= 0 || comment == null || "null".equalsIgnoreCase(comment)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        Prescription prescription = prescriptionDao.find(scriptNo);
        if (prescription == null || prescription.getDemographicId() == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        String loggedInProvider = loggedInInfo.getLoggedInProviderNo();
        // Additional Notes are rendered beneath the persisted prescriber's signature. Patient-level
        // _rx write alone must not let a covering provider alter somebody else's signed document.
        if (!Objects.equals(loggedInProvider, prescription.getProviderNo())
                || !securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE,
                        String.valueOf(prescription.getDemographicId()))) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        if (prescriptionDao.updatePrescriptionsByScriptNo(scriptNo, comment) != 1) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        return NONE;
    }
}
