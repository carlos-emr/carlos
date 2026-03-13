/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Blocks HTTP GET requests on mutator endpoints (actions and JSPs that perform state changes).
 *
 * <p>This filter enforces the principle that state-changing operations must use POST (or other
 * non-GET methods). Combined with CSRFGuard 4.5 (which validates tokens on POST/PUT/DELETE/PATCH),
 * this prevents attackers from triggering mutations via crafted GET URLs that bypass CSRF
 * protection.</p>
 *
 * <h3>Detection Strategy</h3>
 * <ul>
 *   <li><strong>.do actions</strong>: Action name is extracted from the URL and checked against
 *       mutator prefixes (Add, Delete, Save, Submit, Create, etc.). The {@code method} request
 *       parameter is also checked for mutator values (save, delete, update, etc.) to catch
 *       mixed-method actions that route via parameter.</li>
 *   <li><strong>.jsp pages</strong>: The JSP filename is checked against known mutator JSP
 *       patterns (names containing save, delete, add, submit, create, merge, control, etc.).</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <p>Supports an {@code allowList} init-param (comma-separated) for action names or JSP paths
 * that have mutator-sounding names but legitimately need GET access.</p>
 *
 * <p>Must be mapped <strong>after CSRFGuard but before Struts</strong> in the filter chain.</p>
 *
 * @since 2026-03-13
 */
public class HttpMethodGuardFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpMethodGuardFilter.class);

    /**
     * Action name prefixes that indicate mutator operations (case-insensitive).
     * "Edit" and "Update" are intentionally excluded — they commonly load forms via GET,
     * and their actual save operations go through separate Save/Add actions or use
     * {@code ?method=save} (which IS caught by {@link #MUTATOR_METHOD_PARAMS}).
     */
    private static final Set<String> MUTATOR_ACTION_PREFIXES = Set.of(
            "add", "delete", "remove", "save", "submit", "create",
            "assign", "complete", "process", "archive",
            "merge", "transfer", "approve", "reject", "toggle",
            "cancel", "close", "resubmit"
    );

    /**
     * Values of the {@code method} request parameter that indicate mutator operations.
     * This catches mixed-method actions (e.g., SystemMessage2Action) where
     * {@code ?method=save} routes to a save method within a read/write action.
     */
    private static final Set<String> MUTATOR_METHOD_PARAMS = Set.of(
            "save", "delete", "update", "add", "create", "remove",
            "submit", "merge", "archive", "assign", "transfer",
            "approve", "reject", "toggle", "complete", "process",
            "cancel", "close"
    );

    /**
     * Keywords in JSP filenames that indicate mutator JSPs (case-insensitive).
     * These are JSPs that receive form POST submissions and perform state changes.
     */
    private static final Set<String> MUTATOR_JSP_KEYWORDS = Set.of(
            "save", "delete", "submit", "create", "merge"
    );

    /**
     * Specific JSP filenames (without path, lowercased) known to be mutators.
     * These are JSPs whose names don't match the keyword patterns above but are
     * confirmed POST-only targets from the codebase analysis.
     */
    private static final Set<String> MUTATOR_JSP_NAMES = Set.of(
            // Admin mutators
            "securityaddsecurity.jsp",
            "securitydelete.jsp",
            "securityupdate.jsp",
            "provideraddarecord.jsp",
            "providerupdate.jsp",
            "providerupdatepreference.jsp",
            "provideraddrole.jsp",
            "resourcebaseurl.jsp",
            "unlock.jsp",
            "fixrolesonnotes.jsp",
            "lotnraddrecord.jsp",
            "lotnrdeleterecord.jsp",
            "manageflowsheetsupload.jsp",
            "dbmanageprovider.jsp",
            // Appointment mutators
            "addappointment.jsp",
            "appointmentcontrol.jsp",
            // Demographic mutators
            "demographicaddarecord.jsp",
            "demographicmergerecord.jsp",
            // Billing mutators
            "billingonsave.jsp",
            "billingcorrection.jsp",
            "billingcorrectionsubmit.jsp",
            "billingshortcutpg2.jsp",
            "billingreportcontrol.jsp",
            "billingonfavourite.jsp",
            "addeditservicecode.jsp",
            "onaddedit3rdaddr.jsp",
            "settlebg.jsp",
            "gensimulation.jsp",
            // Schedule mutators
            "scheduledatesave.jsp",
            "scheduledatefinal.jsp",
            "scheduleholidaysetting.jsp",
            "scheduletemplatecodesetting.jsp",
            // Prevention mutators
            "preventionmanager.jsp",
            "preventionlistmanager.jsp",
            // Messenger mutators
            "postitems.jsp",
            "adjustattachments.jsp",
            // Encounter mutators
            "measurementgroupdscomplete.jsp",
            // Tickler mutators
            "dbticklerdemomain.jsp",
            "dbticklermain.jsp",
            // Other mutators
            "annotation.jsp",
            "groupnoteselectaction.jsp",
            "preference_action.jsp",
            "patientlettermanager.jsp",
            "checklistedit.jsp",
            "riskedit.jsp",
            "providercontrol.jsp",
            "providerprivilege.jsp",
            "providerrole.jsp",
            "providertemplate.jsp"
    );

    private Set<String> allowList = Collections.emptySet();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String allowListParam = filterConfig.getInitParameter("allowList");
        if (allowListParam != null && !allowListParam.isBlank()) {
            allowList = Arrays.stream(allowListParam.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            LOGGER.info("HttpMethodGuardFilter initialized with allow-list: {}", allowList);
        } else {
            LOGGER.info("HttpMethodGuardFilter initialized (no allow-list configured)");
        }
    }

    /**
     * Checks GET requests against mutator detection rules and blocks with 405 if matched.
     *
     * @param request  the servlet request
     * @param response the servlet response
     * @param chain    the filter chain
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Only inspect GET requests — all other methods pass through
        if (!"GET".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String uri = httpRequest.getRequestURI();
        String contextPath = httpRequest.getContextPath();
        // Strip context path for pattern matching
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;

        if (path.endsWith(".do")) {
            if (isMutatorAction(path, httpRequest)) {
                blockRequest(httpRequest, httpResponse, path);
                return;
            }
        } else if (path.endsWith(".jsp")) {
            if (isMutatorJsp(path, httpRequest)) {
                blockRequest(httpRequest, httpResponse, path);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Determines if a {@code .do} action URL targets a mutator action.
     *
     * <p>Checks the action name against mutator prefixes and the {@code method}
     * request parameter against mutator method values.</p>
     *
     * @param path    the request path (without context path), e.g. {@code /tickler/addTickler.do}
     * @param request the HTTP request (for parameter access)
     * @return {@code true} if this is a mutator action on GET
     */
    private boolean isMutatorAction(String path, HttpServletRequest request) {
        String actionName = extractActionName(path);
        if (actionName == null || actionName.isEmpty()) {
            return false;
        }

        String actionNameLower = actionName.toLowerCase(Locale.ROOT);

        // Check allow-list first
        if (allowList.contains(actionNameLower)) {
            return false;
        }

        // Check action name against mutator prefixes
        for (String prefix : MUTATOR_ACTION_PREFIXES) {
            if (actionNameLower.startsWith(prefix)) {
                return true;
            }
        }

        // Check the 'method' request parameter for mixed-method actions
        String methodParam = request.getParameter("method");
        if (methodParam != null && !methodParam.isEmpty()) {
            return MUTATOR_METHOD_PARAMS.contains(methodParam.toLowerCase(Locale.ROOT));
        }

        return false;
    }

    /**
     * Determines if a {@code .jsp} URL targets a mutator JSP.
     *
     * <p>Checks the JSP filename against known mutator JSP names and keyword patterns.</p>
     *
     * @param path    the request path (without context path)
     * @param request the HTTP request (for parameter access)
     * @return {@code true} if this is a mutator JSP on GET
     */
    private boolean isMutatorJsp(String path, HttpServletRequest request) {
        String jspName = extractFilename(path);
        if (jspName == null || jspName.isEmpty()) {
            return false;
        }

        String jspNameLower = jspName.toLowerCase(Locale.ROOT);

        // Check allow-list first
        if (allowList.contains(jspNameLower)) {
            return false;
        }

        // Check against known mutator JSP names
        if (MUTATOR_JSP_NAMES.contains(jspNameLower)) {
            // Special case: PreventionManager.jsp is only a mutator when formAction=update|custom
            if ("preventionmanager.jsp".equals(jspNameLower) || "preventionlistmanager.jsp".equals(jspNameLower)) {
                String formAction = request.getParameter("formAction");
                return formAction != null && ("update".equalsIgnoreCase(formAction) || "custom".equalsIgnoreCase(formAction));
            }
            return true;
        }

        // Check JSP name against mutator keywords
        for (String keyword : MUTATOR_JSP_KEYWORDS) {
            if (jspNameLower.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extracts the simple action name from a {@code .do} URL path.
     *
     * <p>For example, {@code /tickler/addTickler.do} returns {@code addTickler},
     * and {@code /web/dashboard/display/AssignTickler.do} returns {@code AssignTickler}.</p>
     *
     * @param path the request path
     * @return the action name, or {@code null} if not extractable
     */
    static String extractActionName(String path) {
        if (path == null || !path.endsWith(".do")) {
            return null;
        }
        // Remove .do suffix
        String withoutSuffix = path.substring(0, path.length() - 3);
        // Get the last path segment (the action name)
        int lastSlash = withoutSuffix.lastIndexOf('/');
        return lastSlash >= 0 ? withoutSuffix.substring(lastSlash + 1) : withoutSuffix;
    }

    /**
     * Extracts the filename from a URL path.
     *
     * @param path the request path
     * @return the filename, or {@code null} if not extractable
     */
    static String extractFilename(String path) {
        if (path == null) {
            return null;
        }
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Sends a 405 Method Not Allowed response and logs the blocked request.
     */
    private void blockRequest(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        String methodParam = request.getParameter("method");
        String detail = methodParam != null ? path + "?method=" + methodParam : path;

        LOGGER.warn("Blocked GET request on mutator endpoint: {} (remote: {}, session: {})",
                detail,
                request.getRemoteAddr(),
                request.getRequestedSessionId() != null ? "present" : "none");

        response.setHeader("Allow", "POST");
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "GET requests are not allowed on this endpoint. Use POST.");
    }

    @Override
    public void destroy() {
        // No cleanup required
    }
}
