/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Helpers for the {@code scheduleNav} request flag that keeps a page inside the
 * schedule navigation shell.
 *
 * <p>When a provider's schedule navigation mode is {@code tab} or {@code focused},
 * menu links carry {@code scheduleNav=1} and the destination page renders the
 * shared header ({@code /WEB-INF/jsp/provider/mainMenu.jsp}) instead of opening a
 * bare popup. The flag lives only in the request, so any action that answers with
 * a <em>redirect</em> starts a new request and drops it unless it is re-appended:
 * the user lands on the same screen with the navigation tabs gone and no way back
 * except the browser's Back button.
 *
 * <p>Forwards keep the flag on their own (same request, same parameters), so only
 * redirect targets and cross-request form posts need these helpers.
 *
 * @since 2026-09-06
 */
public final class ScheduleNav {

    /** Request parameter carrying the flag. */
    public static final String PARAM = "scheduleNav";

    /** The only value that enables the shell; anything else is treated as absent. */
    public static final String ENABLED = "1";

    private ScheduleNav() {
        // static utility
    }

    /**
     * Returns {@code true} when the current request is being rendered inside the
     * schedule navigation shell.
     *
     * @param request the current servlet request; {@code null} is treated as "not active"
     */
    public static boolean isActive(HttpServletRequest request) {
        return request != null && ENABLED.equals(request.getParameter(PARAM));
    }

    /**
     * Returns {@code ENABLED} when the shell is active, otherwise {@code null}.
     *
     * <p>Shaped for the {@code documentManager} redirect builders, which skip
     * null-valued parameters, so a caller can add the flag unconditionally.
     */
    public static String paramValue(HttpServletRequest request) {
        return isActive(request) ? ENABLED : null;
    }

    /**
     * Appends {@code scheduleNav=1} to {@code url} when the shell is active, choosing
     * {@code ?} or {@code &} from the URL already built. Returns {@code url} unchanged
     * when the shell is not active, so a caller never has to branch.
     *
     * @param url     an application-relative redirect target
     * @param request the current servlet request
     */
    public static String append(String url, HttpServletRequest request) {
        if (url == null || !isActive(request)) {
            return url;
        }
        return url + (url.indexOf('?') >= 0 ? "&" : "?") + PARAM + "=" + ENABLED;
    }
}
