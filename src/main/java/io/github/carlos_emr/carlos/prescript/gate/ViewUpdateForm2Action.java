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

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Gate for {@code rx/updateForm.jsp}, the drug-form editor DisplayRxRecord opens for one saved
 * drug ({@code id}).
 *
 * <p>The drug id is request input, so the drug is loaded here and the caller must be allowed to
 * read that drug's patient (patient-level {@code _rx} read and record access) before anything about
 * it is rendered. Changing the form ({@code action=update}) is POST-only (405 otherwise) and needs
 * {@code _rx} write, globally and for that patient. The JSP only renders the request attributes set
 * here ({@code drugId}, {@code drugForm}, {@code drugFormUpdated}) (#3908).</p>
 *
 * @since 2026-04-13
 */
public final class ViewUpdateForm2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Loads and authorises the drug, applies a POSTed form change, and renders the editor.
     *
     * @return {@code success}, or {@code NONE} after an error response (400 malformed id, 404 no
     *         such drug, 405 a non-POST update)
     * @throws SecurityException when the caller may not read (or, to update, write) the drug's patient
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        boolean update = "update".equals(request.getParameter("action"));
        // CSRFGuard does not check GET: a change must be a POST, refused before anything is read.
        if (update && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", update ? "w" : "r", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        String id = request.getParameter("id");
        if (id == null || !id.matches("\\d{1,9}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        DrugDao drugDao = SpringUtils.getBean(DrugDao.class);
        Drug drug = drugDao.find(Integer.parseInt(id));
        if (drug == null || drug.getDemographicId() == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        // Authorised against the patient the drug actually belongs to, never a request-supplied one.
        RxRequestedPatientAccess.requirePatient(securityInfoManager, loggedInInfo, drug.getDemographicId(),
                "_rx", update ? "w" : "r");

        if (update) {
            drug.setDrugForm(request.getParameter("drugForm"));
            drugDao.merge(drug);
            request.setAttribute("drugFormUpdated", Boolean.TRUE);
        }
        request.setAttribute("drugId", drug.getId());
        request.setAttribute("drugForm", drug.getDrugForm());
        return SUCCESS;
    }
}
