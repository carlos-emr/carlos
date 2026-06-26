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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Authenticated facility-selection endpoint for providers who belong to multiple facilities.
 *
 * <p>GET/HEAD renders the selector view. POST applies the selected facility after CSRFGuard has
 * validated the request token, keeping facility changes out of CSRF-exempt login handling. Retryable
 * form mistakes stay on this endpoint; authorization or data-integrity failures end the staged
 * login session so {@link io.github.carlos_emr.carlos.sec.LoginFilter}'s pending-facility gate
 * cannot loop indefinitely.</p>
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

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "UNVALIDATED_REDIRECT"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
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
            return redirectToFacilitySelection(request, response);
        }

        String nextResult = request.getParameter("nextPage");
        if (nextResult != null && !nextResult.isEmpty() && !ALLOWED_NEXT_RESULTS.contains(nextResult)) {
            // Validate navigation intent before mutating facility state; invalid values are retryable.
            if (LOGGER.isWarnEnabled()) {
                String safeProviderNo = LogSafe.sanitize(providerNo);
                String safeNextResult = LogSafe.sanitize(nextResult);
                String safeRemoteAddr = LogSafe.sanitize(request.getRemoteAddr());
                LOGGER.warn("Rejected /select_facility nextPage before facility mutation: provider={}, nextPage={}, remote={}", // NOSONAR javasecurity:S5145 (SonarCloud alert #26206) — false positive: every user-controlled arg is sanitized via LogSafe (OWASP Encode.forJava escapes CR/LF/control chars and caps length), neutralizing log injection; Sonar does not model the LogSafe wrapper as a sanitizer
                        safeProviderNo, safeNextResult, safeRemoteAddr);
            }
            return redirectToFacilitySelection(request, response);
        }

        int facilityId = Integer.parseInt(facilityIdString);
        List<Integer> allowedFacilityIds = providerDao.getFacilityIds(providerNo);
        if (allowedFacilityIds == null || !allowedFacilityIds.contains(facilityId)) {
            LOGGER.warn("Rejected /select_facility: unauthorized facility provider={}, facilityId={}, remote={}",
                    LogSafe.sanitize(providerNo), facilityId, LogSafe.sanitize(request.getRemoteAddr()));
            return redirectToLogoutAfterInvalidating(session, request, response);
        }

        Facility facility = facilityDao.find(facilityId);
        if (facility == null) {
            LOGGER.warn("Rejected /select_facility: missing facility provider={}, facilityId={}, remote={}",
                    LogSafe.sanitize(providerNo), facilityId, LogSafe.sanitize(request.getRemoteAddr()));
            return redirectToLogoutAfterInvalidating(session, request, response);
        }

        session.setAttribute(SessionConstants.CURRENT_FACILITY, facility); // nosemgrep: tainted-session-from-http-request -- facility entity is DAO-loaded after provider/facility authorization
        session.removeAttribute(SessionConstants.PENDING_FACILITY_SELECTION);
        LoggedInInfo loggedInInfo = LoggedInUserFilter.generateLoggedInInfoFromSession(request);
        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);
        LogAction.addLog(providerNo, LogConst.LOGIN, LogConst.CON_LOGIN,
                "facilityId=" + facilityId, LogSafe.sanitize(request.getRemoteAddr()));

        if (nextResult == null || nextResult.isEmpty()) {
            return "provider";
        }
        return nextResult;
    }

    @Override
    protected String requiredSessionAttribute() {
        return "user";
    }

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    private String redirectToFacilitySelection(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendRedirect(request.getContextPath() + "/select_facility");
        return NONE;
    }

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    private String redirectToLogoutAfterInvalidating(HttpSession session, HttpServletRequest request,
                                                     HttpServletResponse response) throws IOException {
        session.invalidate();
        response.sendRedirect(request.getContextPath() + "/logoutPage");
        return NONE;
    }
}
