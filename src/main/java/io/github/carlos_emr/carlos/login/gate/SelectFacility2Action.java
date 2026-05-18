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
package io.github.carlos_emr.carlos.login.gate;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.login.Login2Action;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LoggedInUserFilter;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;

/**
 * Authenticated facility-selection endpoint for providers who belong to multiple facilities.
 *
 * <p>GET/HEAD renders the selector view. POST applies the selected facility after CSRFGuard has
 * validated the request token, keeping facility changes out of CSRF-exempt login handling.</p>
 *
 * @since 2026-05-17
 */
public final class SelectFacility2Action extends BaseLoginPageView2Action {
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final Set<String> ALLOWED_NEXT_RESULTS =
            Set.of("provider", "caisiPMM", "programLocation", "failure");

    private final ProviderDao providerDao;
    private final FacilityDao facilityDao;

    public SelectFacility2Action() {
        this((ProviderDao) SpringUtils.getBean(ProviderDao.class),
                (FacilityDao) SpringUtils.getBean(FacilityDao.class));
    }

    SelectFacility2Action(ProviderDao providerDao, FacilityDao facilityDao) {
        this.providerDao = providerDao;
        this.facilityDao = facilityDao;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        String method = request.getMethod();

        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            if (request.getParameter(Login2Action.SELECTED_FACILITY_ID) != null) {
                LOGGER.warn("Rejected /select_facility: GET/HEAD mutation intent, remote={}",
                        LogSafe.sanitize(request.getRemoteAddr()));
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
            return super.execute();
        }
        if (!"POST".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            LOGGER.info("Rejected /select_facility: missing authenticated session, remote={}",
                    LogSafe.sanitize(request.getRemoteAddr()));
            response.sendRedirect(request.getContextPath() + "/logoutPage");
            return NONE;
        }

        String providerNo = String.valueOf(session.getAttribute("user"));
        String facilityIdString = request.getParameter(Login2Action.SELECTED_FACILITY_ID);
        if (facilityIdString == null || !facilityIdString.matches("\\d{1,9}")) {
            LOGGER.warn("Rejected /select_facility: invalid facility id for provider={}, remote={}",
                    LogSafe.sanitize(providerNo), LogSafe.sanitize(request.getRemoteAddr()));
            return redirectToLoginFailed(request, response);
        }

        int facilityId = Integer.parseInt(facilityIdString);
        List<Integer> allowedFacilityIds = providerDao.getFacilityIds(providerNo);
        if (allowedFacilityIds == null || !allowedFacilityIds.contains(facilityId)) {
            LOGGER.warn("Rejected /select_facility: unauthorized facility provider={}, facilityId={}, remote={}",
                    LogSafe.sanitize(providerNo), facilityId, LogSafe.sanitize(request.getRemoteAddr()));
            return redirectToLoginFailed(request, response);
        }

        Facility facility = facilityDao.find(facilityId);
        if (facility == null) {
            LOGGER.warn("Rejected /select_facility: missing facility provider={}, facilityId={}, remote={}",
                    LogSafe.sanitize(providerNo), facilityId, LogSafe.sanitize(request.getRemoteAddr()));
            return redirectToLoginFailed(request, response);
        }

        session.setAttribute(SessionConstants.CURRENT_FACILITY, facility); // nosemgrep: tainted-session-from-http-request -- facility entity is DAO-loaded after provider/facility authorization
        session.removeAttribute(SessionConstants.PENDING_FACILITY_SELECTION);
        LoggedInInfo loggedInInfo = LoggedInUserFilter.generateLoggedInInfoFromSession(request);
        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);
        LogAction.addLog(providerNo, LogConst.LOGIN, LogConst.CON_LOGIN,
                "facilityId=" + facilityId, request.getRemoteAddr());

        String nextResult = request.getParameter("nextPage");
        if (nextResult == null || !ALLOWED_NEXT_RESULTS.contains(nextResult)) {
            LOGGER.warn("Rejected /select_facility nextPage: provider={}, nextPage={}, remote={}",
                    LogSafe.sanitize(providerNo), LogSafe.sanitize(nextResult),
                    LogSafe.sanitize(request.getRemoteAddr()));
            return "provider";
        }
        return nextResult;
    }

    @Override
    protected String requiredSessionAttribute() {
        return "user";
    }

    private String redirectToLoginFailed(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendRedirect(request.getContextPath() + "/loginfailed");
        return NONE;
    }
}
