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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Supports the legacy "Search Again" flow from the Rx print preview by
 * re-running a surname-only patient search and re-rendering Print.jsp with
 * results for explicit patient selection.
 *
 * @since 2026-04-19
 */
public final class RxSearchPatient2Action extends ActionSupport {

    static final String ATTR_SEARCH_PERFORMED = "searchPerformed";
    static final String ATTR_SEARCH_RESULTS = "searchResults";
    static final String ATTR_SEARCH_SURNAME = "searchSurname";

    private final HttpServletRequest request = ServletActionContext.getRequest();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new RuntimeException("missing required sec object (_demographic)");
        }

        String surname = StringUtils.trimToEmpty(request.getParameter("surname"));
        RxPatientData.Patient[] results = new RxPatientData.Patient[0];

        if (!surname.isEmpty()) {
            results = RxPatientData.PatientSearch(loggedInInfo, surname, "");
        }

        request.setAttribute(ATTR_SEARCH_PERFORMED, Boolean.TRUE);
        request.setAttribute(ATTR_SEARCH_SURNAME, surname);
        request.setAttribute(ATTR_SEARCH_RESULTS, results);

        return SUCCESS;
    }
}
