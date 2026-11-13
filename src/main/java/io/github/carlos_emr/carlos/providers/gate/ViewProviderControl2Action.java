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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.logging.log4j.Logger;

/**
 * View gate for {@code provider/providercontrol.jsp} (the provider dashboard /
 * schedule landing page). Enforces {@code _appointment} {@code r} privilege
 * before Struts renders the JSP. Entry point referenced from the login redirect
 * ({@code struts-login.xml}), {@code LoginFilter} refresh list,
 * {@code PersonaService} navbar, and numerous intra-module links.
 *
 * <p>This action should remain the schedule landing target after successful login. Forwarding the
 * JSP from a public filter would skip this provider gate and make unauthenticated behavior depend
 * on JSP scriptlets instead of the shared Struts security boundary.</p>
 *
 * <p>Because login lands here without the user asking for anything, a caller without the
 * schedule privilege gets a specific 403 page instead of the generic security exception (issue
 * #2689): it says whether the account has no role at all or a role without schedule access, and
 * what to ask an administrator for. It is still a refusal; nothing of the schedule renders.</p>
 *
 * @since 2026-04-13
 */
public final class ViewProviderControl2Action extends BaseProviderViewGate2Action {

    /** Result naming the schedule-unavailable page. */
    static final String NO_SCHEDULE_ACCESS = "noScheduleAccess";

    /** Request attribute: {@code true} when the account holds no active role at all. */
    static final String NO_ROLE_ATTRIBUTE = "noRoleAssigned";

    private static final Logger LOGGER = MiscUtils.getLogger();

    public ViewProviderControl2Action() {
        super();
    }

    ViewProviderControl2Action(SecurityInfoManager securityInfoManager) {
        super(securityInfoManager);
    }

    @Override
    protected String getSecurityObject() {
        return "_appointment";
    }

    @Override
    protected String getAccessRight() {
        return "r";
    }

    @Override
    protected String renderPrivilegeDenied(HttpServletRequest request, HttpServletResponse response,
                                           LoggedInInfo loggedInInfo) {
        boolean noRole = securityInfoManager().getRoles(loggedInInfo).isEmpty();
        // Same facts the generic refusal logs (action, path, provider, object), without the query
        // string; the role state tells an administrator which fix the user needs.
        if (LOGGER.isWarnEnabled()) {
            LOGGER.warn("Authorization refused in action provider/providercontrol ({} {}) provider={}: "
                            + "missing required sec object (_appointment); {}",
                    LogSafe.sanitize(request.getMethod()),
                    LogSafe.sanitizeUri(request.getRequestURI()),
                    LogSafe.sanitize(loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo()),
                    noRole ? "no active role assigned" : "active roles lack schedule access");
        }
        request.setAttribute(NO_ROLE_ATTRIBUTE, noRole);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return NO_SCHEDULE_ACCESS;
    }
}
