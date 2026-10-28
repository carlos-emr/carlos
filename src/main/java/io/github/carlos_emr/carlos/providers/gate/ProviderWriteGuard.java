/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.providers.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Protects group-writing JSPs even when the legacy controller includes them directly. */
public final class ProviderWriteGuard {
    public static final String STATUS_ATTRIBUTE = ProviderWriteGuard.class.getName() + ".status";

    private ProviderWriteGuard() { }

    /** Requires POST and the same administrative write privilege as the dedicated group actions. */
    public static boolean requireAdminPost(HttpServletRequest request, HttpServletResponse response) {
        if (!"POST".equals(request.getMethod())) {
            return reject(request, response, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
        LoggedInInfo login = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (login == null || !SpringUtils.getBean(SecurityInfoManager.class)
                .hasPrivilege(login, "_admin", "w", null)) {
            return reject(request, response, HttpServletResponse.SC_FORBIDDEN);
        }
        return true;
    }

    private static boolean reject(HttpServletRequest request, HttpServletResponse response, int status) {
        // Servlet includes ignore status/header changes; the outer controller propagates this value.
        request.setAttribute(STATUS_ATTRIBUTE, status);
        response.setStatus(status);
        if (status == HttpServletResponse.SC_METHOD_NOT_ALLOWED) {
            response.setHeader("Allow", "POST");
        }
        return false;
    }
}
