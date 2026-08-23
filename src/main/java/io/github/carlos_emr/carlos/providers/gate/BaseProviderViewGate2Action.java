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
package io.github.carlos_emr.carlos.providers.gate;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sec.UnauthenticatedRejectionResolver;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Base class for provider-module view gates. Subclasses declare a security
 * object + right pair; this class enforces the privilege check and, when
 * {@link #requirePost()} returns {@code true}, also enforces POST-only access
 * (returning HTTP 405 for other methods).
 *
 * <p>Subclasses cannot override {@link #execute()} (it is {@code final}).
 * Configure behavior via {@link #getSecurityObject()},
 * {@link #getAccessRight()}, and {@link #requirePost()}.
 *
 * <p>Unauthenticated requests are rejected before {@link LoggedInInfo} is loaded. The servlet
 * {@code LoginFilter} is the canonical gate, but this class keeps the same check as
 * defence-in-depth so direct action invocation and future filter-order changes cannot surface as
 * server errors or JSP execution.
 *
 * <p>Provider-module convention: {@code _appointment r} is the default
 * provider-area entry privilege and is used for most view gates regardless of
 * topical relevance (encounter history, vaccine registry, signature edit,
 * preferences, etc.). This reflects that any practicing provider holds
 * {@code _appointment r} — it is effectively "logged-in-as-a-provider."
 * Only gates that mutate admin-level state ({@code _admin w}) or require
 * demographic writes ({@code _demographic w}) deviate.
 *
 * @since 2026-04-13
 */
public abstract class BaseProviderViewGate2Action extends ActionSupport {
    private static final Logger LOGGER = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager;

    protected BaseProviderViewGate2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    protected BaseProviderViewGate2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    /** Security object name (e.g. {@code "_appointment"}, {@code "_admin"}). */
    protected abstract String getSecurityObject();

    /** Access right ({@code "r"} or {@code "w"}). */
    protected abstract String getAccessRight();

    /**
     * Indicates that a gated action is a mutating endpoint and should reject non-POST methods.
     *
     * <p>Provider view gates default to GET-friendly behavior. Override only for endpoints whose
     * Struts result performs a state change or whose legacy route was POST-only.</p>
     */
    protected boolean requirePost() {
        return false;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public final String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (requirePost() && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            try {
                UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);
            } catch (IOException e) {
                LOGGER.warn(
                        "Unable to reject unauthenticated provider request: method={}, uri={}, remote={}",
                        LogSafe.sanitize(request.getMethod()),
                        LogSafe.sanitizeUri(request.getRequestURI()),
                        LogSafe.sanitize(request.getRemoteAddr()),
                        e);
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                } else {
                    LOGGER.error(
                            "Unable to reject unauthenticated provider request after response commit: "
                                    + "method={}, uri={}, remote={}",
                            LogSafe.sanitize(request.getMethod()),
                            LogSafe.sanitizeUri(request.getRequestURI()),
                            LogSafe.sanitize(request.getRemoteAddr()),
                            e);
                }
            }
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(
                loggedInInfo, getSecurityObject(), getAccessRight(), null)) {
            throw new SecurityException(
                    "missing required sec object (" + getSecurityObject() + ")");
        }

        return SUCCESS;
    }
}
