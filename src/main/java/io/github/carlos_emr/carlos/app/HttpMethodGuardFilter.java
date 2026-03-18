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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 *   <li><strong>.jsp pages</strong>: The JSP filename is checked against an explicit set of
 *       known mutator JSPs identified by auditing form POST targets in the codebase.</li>
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
     *
     * <p>Includes abbreviated forms: "del" (for delGroup, DelService) and
     * "rem" (for remFromGroup) which are used in some struts action names.</p>
     */
    private static final Set<String> MUTATOR_ACTION_PREFIXES = Set.of(
            "add", "delete", "del",
            "remove", "rem",
            "save", "submit", "create",
            "assign", "complete", "process", "archive",
            "merge", "transfer", "approve", "reject", "toggle",
            "cancel", "close", "resubmit"
    );

    /**
     * Specific action names (lowercased) that are known mutators but whose URL names
     * do not match any prefix in {@link #MUTATOR_ACTION_PREFIXES}. These are struts
     * action mappings where the URL name differs significantly from the class name.
     *
     * <p>Identified by auditing struts.xml for class names like Save*, Delete*, Add*
     * mapped to non-matching URL action names.</p>
     */
    private static final Set<String> MUTATOR_ACTION_NAMES = Set.of(
            "oncallclinic",           // SaveOnCallClinic2Action
            "billingaddcode",         // BillingAddCode2Action (starts with "billing", not "add")
            "reprocessbill",          // BillingReProcessBill2Action
            "movemohfiles",           // ArchiveMOHFile2Action
            "newmeasurementmap",      // EctAddMeasurementMap2Action
            "remapmeasurementmap",    // EctRemoveMeasurementMap2Action
            "setupaddmeasurementgroup",      // EctSetupAddMeasurementGroup2Action
            "setupaddmeasurementtype",       // EctSetupAddMeasurementType2Action
            "setupaddmeasuringinstruction",  // EctSetupAddMeasuringInstruction2Action
            "rbtaddtogroup",          // RBTAddToGroup2Action (starts with "rbt", not "add")
            "reportreassign",         // ReportReassign2Action (reassigns lab reports to other providers)
            "forward"                 // ReportReassign2Action (lab/MDS forwarding — same mutator class, 4 URLs)
    );

    /**
     * Values of the {@code method} request parameter that indicate mutator operations.
     * This catches mixed-method actions (e.g., SystemMessage2Action) where
     * {@code ?method=save} routes to a save method within a read/write action.
     *
     * <p>Note: "add" is intentionally excluded. Many actions use {@code method=add} to
     * load an "add new" form (e.g., FacilityManager.do?method=add), not to perform a
     * mutation. The actual save goes through a separate POST. Actions named "Add*"
     * are still caught by {@link #MUTATOR_ACTION_PREFIXES}.</p>
     */
    private static final Set<String> MUTATOR_METHOD_PARAMS = Set.of(
            "save", "delete", "update", "create", "remove",
            "submit", "merge", "archive", "assign", "transfer",
            "approve", "reject", "toggle", "complete", "process",
            "cancel", "close"
    );

    /**
     * Action names (lowercased) that match mutator prefixes but are actually read-only
     * operations that legitimately use GET. For example, {@code createBillingReportAction}
     * generates PDF/CSV file downloads (read-only) despite starting with "create".
     */
    private static final Set<String> READ_ONLY_ACTION_NAMES = Set.of(
            "createbillingreportaction"   // PDF/CSV download — GET is correct for file downloads
    );

    /**
     * JSP filenames (without path, lowercased) that are POST-only mutator targets.
     * GET requests to these JSPs are always blocked — they have no form display
     * function and should never be accessed directly via GET.
     */
    private static final Set<String> PURE_MUTATOR_JSP_NAMES = Set.of(
            // Admin mutators (POST-only handlers)
            "securityaddsecurity.jsp",
            "securitydelete.jsp",
            "securityupdate.jsp",
            "provideraddarecord.jsp",
            "providerupdate.jsp",
            "providerupdatepreference.jsp",
            "fixrolesonnotes.jsp",
            "lotnraddrecord.jsp",
            "lotnrdeleterecord.jsp",
            "manageflowsheetsupload.jsp",
            "dbmanageprovider.jsp",
            "adminsavemygroup.jsp",
            // Appointment mutators
            "appointmentdeletearecord.jsp",
            // Demographic mutators
            "demographicaddarecord.jsp",
            // Billing mutators (POST-only)
            "billingonsave.jsp",
            "billingshortcutpg2.jsp",
            "billingreportcontrol.jsp",
            "gensimulation.jsp",
            "deleteprivatecode.jsp",
            "deleteservices.jsp",
            "ongenreport.jsp",
            // Schedule mutators (POST-only)
            "scheduledatesave.jsp",
            "scheduledatefinal.jsp",
            // Messenger mutators
            "postitems.jsp",
            "adjustattachments.jsp",
            // Encounter mutators
            "measurementgroupdscomplete.jsp",
            // Tickler mutators (POST-only)
            "dbticklerdemomain.jsp",
            "dbticklermain.jsp",
            "dbtickleradd.jsp",
            // Lab mutators (POST-only)
            "createlabtest.jsp",
            // Other POST-only mutators
            "groupnoteselectaction.jsp",
            "preference_action.jsp",
            "providersavedemographicaccessory.jsp",
            "formsaveandexit.jsp",
            "add2waitinglist.jsp",
            "removefromwaitinglist.jsp",
            "adddemotopatientset.jsp",
            "providerpreferencequicklinksaction.jsp",
            "dbmanagebillingform_add.jsp"
    );

    /**
     * JSP filenames (without path, lowercased) that are dual-purpose: they display a form
     * on GET and process mutations on POST. GET is only blocked when a mutator parameter
     * (dboperation, submit, submitFrm, formAction) is present — which indicates an attempt
     * to trigger a mutation via GET instead of POST.
     *
     * <p>Common mutator parameter patterns in these JSPs:</p>
     * <ul>
     *   <li>{@code dboperation=Save|Delete} — schedule, admin template, billing settings</li>
     *   <li>{@code submit=Add|Delete|Save} — provider privilege/role, billing, antenatal</li>
     *   <li>{@code submitFrm=Save|Add Service Code} — billing service codes</li>
     *   <li>{@code formAction=update|custom} — prevention manager</li>
     * </ul>
     */
    private static final Set<String> DUAL_PURPOSE_JSP_NAMES = Set.of(
            // Admin forms (GET loads form, POST saves)
            "provideraddrole.jsp",
            "resourcebaseurl.jsp",
            "unlock.jsp",
            "providerprivilege.jsp",
            "providerrole.jsp",
            "providertemplate.jsp",
            "billingsettings.jsp",
            // Billing forms
            "billingcorrection.jsp",
            "billingcorrectionsubmit.jsp",
            "billingonfavourite.jsp",
            "addeditservicecode.jsp",
            "onaddedit3rdaddr.jsp",
            "settlebg.jsp",
            "billingdeletenoappt.jsp",
            "billingdeletewithbillno.jsp",
            "billingdeletewithoutno.jsp",
            "billingondisplay.jsp",
            "billingoneditprivatecode.jsp",
            // Schedule forms
            "scheduleholidaysetting.jsp",
            "scheduletemplatecodesetting.jsp",
            "schedulecreatedate.jsp",
            // Prevention forms
            "preventionmanager.jsp",
            "preventionlistmanager.jsp",
            // Lab forms
            "createlab.jsp",
            // Decision support forms
            "checklistedit.jsp",
            "riskedit.jsp",
            // Report forms
            "reportformconfig.jsp",
            "reportformdemoconfig.jsp",
            "reportformorder.jsp",
            "reportformrecord.jsp",
            // Other dual-purpose forms
            "patientlettermanager.jsp",
            "managepharmacy.jsp",
            "uploadmultidocument.jsp",
            "antenatalplanner.jsp",
            "setprovideravailability.jsp"
    );

    /**
     * Request parameter names that indicate a mutation action in dual-purpose JSPs.
     * If any of these parameters are present on a GET request to a dual-purpose JSP,
     * the request is blocked. These parameters are only sent by form POST submissions,
     * so their presence on GET indicates a CSRF or manipulation attempt.
     */
    private static final Set<String> JSP_MUTATOR_PARAMS = Set.of(
            "dboperation", "submit", "submitFrm", "formAction"
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

        // Only inspect GET and HEAD requests — all other methods pass through.
        // HEAD is semantically identical to GET (RFC 7231 §4.3.2) and must be blocked
        // on mutator endpoints for the same reason as GET.
        String method = httpRequest.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // Use getRequestURI() which is already URL-decoded by the container.
        // Normalize the path to prevent encoding bypasses:
        // - Reject null bytes (path traversal attack vector)
        // - Normalize path separators and resolve /../ sequences
        String uri = httpRequest.getRequestURI();
        if (uri.indexOf('\0') >= 0) {
            LOGGER.warn("Blocked request with null byte in URI: {} (remote: {})",
                    uri.replace('\0', '?'), httpRequest.getRemoteAddr());
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request URI");
            return;
        }
        String contextPath = httpRequest.getContextPath();
        // Strip context path for pattern matching
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        // Normalize ../ sequences to prevent path traversal bypasses
        // (e.g., /safe/../admin/mutator.jsp resolving to /admin/mutator.jsp)
        path = normalizePath(path);

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

        // Check read-only actions that match mutator prefixes but are safe for GET
        if (READ_ONLY_ACTION_NAMES.contains(actionNameLower)) {
            return false;
        }

        // Check action name against explicit mutator action names
        if (MUTATOR_ACTION_NAMES.contains(actionNameLower)) {
            return true;
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
     * <p>Uses a two-tier approach:</p>
     * <ul>
     *   <li><strong>Pure mutators</strong>: Always blocked on GET — these JSPs are POST-only
     *       handlers with no form display function.</li>
     *   <li><strong>Dual-purpose JSPs</strong>: Only blocked on GET when a mutator parameter
     *       ({@code dboperation}, {@code submit}, {@code submitFrm}, {@code formAction}) is
     *       present. This allows the form to load via GET while blocking GET-based mutation
     *       attempts (CSRF attacks).</li>
     * </ul>
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

        // Pure mutators: always block GET
        if (PURE_MUTATOR_JSP_NAMES.contains(jspNameLower)) {
            return true;
        }

        // Dual-purpose JSPs: only block GET when a mutator parameter is present.
        // These pages load forms via GET and process mutations via POST.
        // A mutator parameter on GET indicates a CSRF or manipulation attempt.
        if (DUAL_PURPOSE_JSP_NAMES.contains(jspNameLower)) {
            return hasMutatorParam(request);
        }

        return false;
    }

    /**
     * Checks whether the request contains any mutator parameter that indicates
     * a state-changing operation. Performs case-insensitive matching on both
     * parameter names and values to prevent encoding-based bypasses.
     *
     * @param request the HTTP request
     * @return {@code true} if a mutator parameter is present
     */
    private boolean hasMutatorParam(HttpServletRequest request) {
        java.util.Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement().toLowerCase(Locale.ROOT).trim();
            if (JSP_MUTATOR_PARAMS.contains(paramName)) {
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

        LOGGER.warn("Blocked {} request on mutator endpoint: {} (remote: {}, session: {})",
                request.getMethod(),
                detail,
                request.getRemoteAddr(),
                request.getRequestedSessionId() != null ? "present" : "none");

        response.setHeader("Allow", "POST");
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "GET requests are not allowed on this endpoint. Use POST.");
    }

    /**
     * Normalizes a URL path by resolving {@code ..} and {@code .} segments,
     * collapsing consecutive slashes, and stripping trailing path parameters.
     * This prevents bypass attempts such as {@code /safe/../admin/mutator.jsp}
     * or {@code /admin/mutator.jsp;jsessionid=...}.
     *
     * @param path the raw request path
     * @return the normalized path
     */
    static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        // Strip path parameters (;jsessionid=...) which could be used to
        // obscure the real path from pattern matching
        int semiIdx = path.indexOf(';');
        if (semiIdx >= 0) {
            path = path.substring(0, semiIdx);
        }

        // Collapse consecutive slashes (e.g., //admin///mutator.jsp)
        path = path.replaceAll("/+", "/");

        // Resolve . and .. segments
        String[] segments = path.split("/");
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        for (String seg : segments) {
            if (seg.isEmpty() || ".".equals(seg)) {
                continue;
            } else if ("..".equals(seg)) {
                if (!stack.isEmpty()) {
                    stack.removeLast();
                }
            } else {
                stack.addLast(seg);
            }
        }

        StringBuilder normalized = new StringBuilder("/");
        java.util.Iterator<String> it = stack.iterator();
        while (it.hasNext()) {
            normalized.append(it.next());
            if (it.hasNext()) {
                normalized.append('/');
            }
        }
        return normalized.toString();
    }

    @Override
    public void destroy() {
        // No cleanup required
    }
}
