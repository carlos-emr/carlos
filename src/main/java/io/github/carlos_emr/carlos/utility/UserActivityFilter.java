/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.apache.logging.log4j.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.sec.LoginFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Logs out an authenticated session whose user has made no request of their own for longer than
 * the session's maximum inactive interval, even while background polls keep the container session
 * alive.
 *
 * <p>Requests that are not the user's own do not count as activity: any request carrying
 * {@code autoRefresh=true} (pages that refresh themselves must add it), every path LoginFilter
 * exempts from its own inactivity timer ({@link LoginFilter#isExemptFromInactivityTimer}: the
 * heartbeat, the tab-alert and message polls, the schedule's self-refresh, static assets, the
 * login and logout pages) and the CSRFGuard script servlet. The timer starts at the session's
 * first authenticated request of any kind, so a session that only ever polls still times out.</p>
 *
 * <p>Contract, kept by the {@code web.xml} mapping and this class (issue #5305):</p>
 * <ul>
 *   <li>The filter is mapped after {@code LoginFilter} and before Struts prepare/execute, for the
 *       REQUEST dispatcher only. Struts executes an action without calling the rest of the
 *       chain, so a mapping after {@code struts2-execute} never sees an action.</li>
 *   <li>It never creates a session, and it judges only authenticated sessions. An anonymous or
 *       half-finished login session (MFA, forced password reset) is left alone; LoginFilter
 *       governs what those may reach.</li>
 *   <li>The logout flow and the public login/error pages are never redirected: the logout page
 *       posts to {@code /logout}, so redirecting either would loop.</li>
 *   <li>An inactive session is recorded in the audit log once. Later requests from the same
 *       session, such as the heartbeat each minute, are redirected without another audit row
 *       until {@code /logout} invalidates the session.</li>
 * </ul>
 *
 * <p>{@code LoginFilter}'s {@code INACTIVITY_LIMIT_MINS} timer is separate and normally fires
 * first; this filter is the backstop measured against the session's own timeout.</p>
 */
public final class UserActivityFilter implements Filter {

    private static final Logger logger = MiscUtils.getLogger();

    /** Session attribute holding the time, in epoch milliseconds, of the user's last own request. */
    static final String LAST_USER_ACTIVITY = "LAST_USER_ACTIVITY";

    /** Session attribute set once this filter has audited the session's inactivity logout. */
    static final String INACTIVITY_LOGOUT_AUDITED = "LAST_USER_ACTIVITY_LOGOUT_AUDITED";

    /** Context-relative target of the inactivity redirect, with its query string already encoded. */
    static final String INACTIVITY_LOGOUT_PATH =
            "/logoutPage?autoLogout=true&errorMessage=logged%20out%20due%20to%20inactivity";

    /** Audit {@code contentId} written for an inactivity logout; audit reports match this text. */
    static final String INACTIVITY_AUDIT_TEXT = "logged out due to inactivity";

    /**
     * Context-relative paths that are never redirected. They are the logout flow and the public
     * login/error pages, matching LoginFilter's timeout-redirect exemptions.
     */
    private static final Set<String> TIMEOUT_REDIRECT_EXEMPT_PATHS =
            Set.of("/logout", "/logoutPage", "/index", "/loginfailed");

    /**
     * Context-relative paths whose requests are never the user's own, beyond LoginFilter's
     * timer-exempt list: the CSRFGuard script servlet (mapped at {@code /csrfguard};
     * {@code /JavaScriptServlet} is its pre-4.x name). The heartbeat is listed in both.
     */
    private static final Set<String> NON_USER_ACTIVITY_PATHS =
            Set.of("/status/SessionHeartbeat", "/csrfguard", "/JavaScriptServlet");

    /**
     * Striped locks for the per-session check-and-set steps. They are keyed by session id, so every
     * request of one session takes the same lock whatever session object the container hands it;
     * a lock is held only for an attribute read and write, so sessions sharing a stripe barely
     * contend.
     */
    private static final Object[] SESSION_LOCKS = new Object[64];

    static {
        Arrays.setAll(SESSION_LOCKS, i -> new Object());
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No configuration.
    }

    @Override
    public void destroy() {
        // Nothing to release.
    }

    /**
     * Redirects an inactive authenticated session to the logout page; otherwise records user
     * activity when the request is the user's own and continues the chain.
     */
    // FindSecBugs UNVALIDATED_REDIRECT: the redirect target is the fixed same-origin logout page; no request data reaches it.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "the redirect target is the fixed same-origin logout page; no request data reaches it")
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        // getSession(false): judging activity must never create a session for an anonymous request.
        HttpSession session = httpRequest.getSession(false);
        String providerNo = session == null ? null : authenticatedProviderNo(session);
        String path = contextRelativePath(httpRequest);
        if (providerNo == null || TIMEOUT_REDIRECT_EXEMPT_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        Object lastActivity = session.getAttribute(LAST_USER_ACTIVITY);
        if (lastActivity instanceof Long last && isInactive(now, last, session.getMaxInactiveInterval())) {
            auditInactivityOnce(session, providerNo, httpRequest.getRemoteAddr());
            httpResponse.sendRedirect(httpRequest.getContextPath() + INACTIVITY_LOGOUT_PATH);
            return;
        }

        // The first authenticated request starts the timer whatever it is; after that only the
        // user's own requests move it.
        if (!(lastActivity instanceof Long) || isUserRequest(httpRequest, path)) {
            recordActivity(session, now);
        }
        chain.doFilter(request, response);
    }

    /**
     * Stores {@code now} unless a concurrent request of the same session already stored a later
     * time, so an older request finishing last cannot move the timer backwards.
     */
    private static void recordActivity(HttpSession session, long now) {
        synchronized (SESSION_LOCKS[lockIndex(session)]) {
            Object current = session.getAttribute(LAST_USER_ACTIVITY);
            if (!(current instanceof Long last) || last < now) {
                // nosemgrep: tainted-session-from-http-request -- now is System.currentTimeMillis(), a server-generated timestamp
                session.setAttribute(LAST_USER_ACTIVITY, now);
            }
        }
    }

    /**
     * Returns the provider number of an authenticated session, or {@code null} when the session
     * is not authenticated. Uses the same marker as LoginFilter: a present, non-blank {@code user}
     * attribute. The provider number of the session's LoggedInInfo is preferred for the audit row.
     */
    private static String authenticatedProviderNo(HttpSession session) {
        Object user = session.getAttribute("user");
        // LoginFilter's rule: only a missing or blank user marks the session unauthenticated.
        if (user == null || (user instanceof String userValue && userValue.isBlank())) {
            return null;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(session);
        String providerNo = loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo();
        return providerNo == null || providerNo.isBlank() ? String.valueOf(user) : providerNo;
    }

    private static int lockIndex(HttpSession session) {
        return Math.floorMod(session.getId().hashCode(), SESSION_LOCKS.length);
    }

    /**
     * A zero or negative interval means the session never times out (Servlet specification),
     * so it is never inactive.
     */
    static boolean isInactive(long now, long lastActivity, int maxInactiveIntervalSeconds) {
        return maxInactiveIntervalSeconds > 0 && now - lastActivity > maxInactiveIntervalSeconds * 1000L;
    }

    private static void auditInactivityOnce(HttpSession session, String providerNo, String remoteAddr) {
        // Check-and-mark under the session's lock so two requests arriving together (a click and a
        // heartbeat) write one row; nothing else is held while it is taken.
        synchronized (SESSION_LOCKS[lockIndex(session)]) {
            if (session.getAttribute(INACTIVITY_LOGOUT_AUDITED) != null) {
                return;
            }
            session.setAttribute(INACTIVITY_LOGOUT_AUDITED, Boolean.TRUE);
        }
        LogAction.addLog(providerNo, LogConst.LOGOUT, LogConst.CON_LOGIN, INACTIVITY_AUDIT_TEXT, remoteAddr);
        if (logger.isWarnEnabled()) {
            logger.warn("User providerNo={} logged out due to inactivity", LogSafe.sanitize(providerNo));
        }
    }

    private static boolean isUserRequest(HttpServletRequest httpRequest, String path) {
        if (NON_USER_ACTIVITY_PATHS.contains(path)
                || LoginFilter.isExemptFromInactivityTimer(httpRequest.getRequestURI(), httpRequest.getContextPath())) {
            return false;
        }
        return !Boolean.parseBoolean(httpRequest.getParameter("autoRefresh"));
    }

    /**
     * The request path below the context root as the container mapped it: servlet path plus path
     * info, which the container has already decoded and stripped of dot segments and path
     * parameters ({@code ;jsessionid=}). A trailing slash is dropped, so an exemption cannot be
     * dodged by decorating the URI.
     */
    static String contextRelativePath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        String path = (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
