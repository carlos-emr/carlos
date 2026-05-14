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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

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

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    /** Security object name (e.g. {@code "_appointment"}, {@code "_admin"}). */
    protected abstract String getSecurityObject();

    /** Access right ({@code "r"} or {@code "w"}). */
    protected abstract String getAccessRight();

    /** Subclasses override to require POST-only access. */
    protected boolean requirePost() {
        return false;
    }

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
            response.sendRedirect(request.getContextPath() + "/logoutPage");
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
