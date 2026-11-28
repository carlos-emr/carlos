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
package io.github.carlos_emr.carlos.dashboard.admin;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.commn.model.Dashboard;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Native administration of indicator dashboards at {@code /web/dashboard/admin/DashboardManager}.
 * GET/HEAD list dashboards or load an edit form. A POST with {@code operation=save} creates or edits
 * metadata; {@code operation=status} explicitly activates or inactivates an unlocked dashboard.
 * Viewing requires {@code _dashboardManager} read, and every POST also requires write access.
 * The application CSRF filter protects both POST forms.
 *
 * @since 2026-11-03
 */
public class DashboardManager2Action extends ActionSupport {
    private static final String MANAGER_PATH = "/web/dashboard/admin/DashboardManager";
    private final transient DashboardManager dashboardManager;
    private final transient SecurityInfoManager securityInfoManager;

    /** Create a request-scoped action using the application's dashboard and security beans. */
    public DashboardManager2Action() {
        this(SpringUtils.getBean(DashboardManager.class), SpringUtils.getBean(SecurityInfoManager.class));
    }

    DashboardManager2Action(DashboardManager dashboardManager, SecurityInfoManager securityInfoManager) {
        this.dashboardManager = dashboardManager;
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Authorize the request, validate its intent, and render or save the dashboard form.
     *
     * @return {@link #SUCCESS} to render the page, the shared submission-refusal result, or
     *         {@link #NONE} after a redirect or HTTP refusal
     * @throws SecurityException when the caller lacks the required dashboard privilege
     * @throws IOException when an HTTP refusal or redirect cannot be written
     */
    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        requireDashboardPrivilege(loggedInInfo, SecurityInfoManager.READ, "Dashboard read access denied");

        String method = request.getMethod();
        boolean post = "POST".equals(method);
        if (post) {
            requireDashboardPrivilege(loggedInInfo, SecurityInfoManager.WRITE, "Dashboard write access denied");
        }

        HttpServletResponse response = ServletActionContext.getResponse();
        String operation = request.getParameter("operation");
        if ((!post && !"GET".equals(method) && !"HEAD".equals(method)) || (!post && operation != null)) {
            response.setHeader("Allow", operation == null ? "GET, HEAD, POST" : "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        List<Dashboard> dashboards = dashboardManager.getDashboards(loggedInInfo);
        if (dashboards == null) {
            throw new SecurityException("missing required sec object (_dashboardManager)");
        }
        request.setAttribute("dashboards", dashboards);
        request.setAttribute("dashboardCanWrite", securityInfoManager.hasPrivilege(
                loggedInInfo, "_dashboardManager", SecurityInfoManager.WRITE, null));

        Dashboard selected = null;
        String rawId = request.getParameter("dashboardId");
        if (rawId != null && !rawId.isEmpty()) {
            Integer id = positiveId(rawId);
            if (id == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            selected = dashboards.stream().filter(d -> id.equals(d.getId())).findFirst().orElse(null);
            if (selected == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return NONE;
            }
        }
        request.setAttribute("editedDashboard", selected);
        if (!post) {
            return SUCCESS;
        }
        if (!"save".equals(operation) && !"status".equals(operation)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        if (selected != null && selected.isLocked()) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return NONE;
        }
        String revision = request.getParameter("dashboardRevision");
        if (selected != null && (revision == null || revision.isEmpty())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        if ("status".equals(operation)) {
            String active = request.getParameter("active");
            if (selected == null || (!"true".equals(active) && !"false".equals(active))) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            if (!dashboardManager.toggleDashboardActive(loggedInInfo, selected.getId(), Boolean.valueOf(active), revision)) {
                response.sendError(HttpServletResponse.SC_CONFLICT);
                return NONE;
            }
            return redirectToDashboardList(request, response);
        }
        if (selected == null) {
            return createDashboard(request, response, loggedInInfo);
        }
        return saveDashboard(request, response, loggedInInfo, selected.getId(), revision, null);
    }

    private String createDashboard(HttpServletRequest request, HttpServletResponse response, LoggedInInfo loggedInInfo)
            throws IOException {
        FormSubmissionGuard.Attempt attempt = FormSubmissionGuard.attempt(request.getSession(),
                request.getParameter(FormSubmissionGuard.PARAMETER), FormSubmissionGuard.Scope.DASHBOARD_CREATE,
                null, FormSubmissionGuard.DEFAULT_WAIT_MILLIS);
        if (attempt.verdict() == FormSubmissionGuard.Verdict.ALREADY_SAVED) {
            return redirectToDashboardList(request, response);
        }
        if (attempt.verdict() != FormSubmissionGuard.Verdict.PROCEED) {
            return FormSubmissionGuard.refuse(request, response, attempt.verdict());
        }
        try (FormSubmissionGuard.Claim claim = attempt.claim()) {
            return saveDashboard(request, response, loggedInInfo, null, null, claim);
        }
    }

    private String saveDashboard(HttpServletRequest request, HttpServletResponse response, LoggedInInfo loggedInInfo,
            Integer dashboardId, String revision, FormSubmissionGuard.Claim claim) throws IOException {
        // Redisplayed fields still belong to the submitted snapshot, even when the list loaded
        // a newer row. Giving them that row's revision would let a retry overwrite its changes.
        request.setAttribute("dashboardRevision", revision);
        String name = request.getParameter("name");
        String description = request.getParameter("description");
        name = name == null ? "" : name.trim();
        description = description == null ? "" : description.trim();
        request.setAttribute("dashboardName", name);
        request.setAttribute("dashboardDescription", description);
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 255
                || description.codePointCount(0, description.length()) > 255) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("dashboardManagerError", "dashboard.dashboardmanager.invalid");
            return SUCCESS;
        }

        // Pass only editable fields to the manager; never mutate a loaded entity in the action.
        Dashboard dashboard = new Dashboard();
        dashboard.setId(dashboardId);
        dashboard.setName(name);
        dashboard.setDescription(description);
        if (claim != null) {
            claim.storageStarted();
            if (dashboardManager.addDashboard(loggedInInfo, dashboard)) {
                claim.saved(MANAGER_PATH);
                return redirectToDashboardList(request, response);
            }
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } else {
            if (dashboardManager.addDashboard(loggedInInfo, dashboard, revision)) {
                return redirectToDashboardList(request, response);
            }
            response.setStatus(HttpServletResponse.SC_CONFLICT);
        }
        request.setAttribute("dashboardManagerError", claim == null
                ? "dashboard.dashboardmanager.conflict" : "dashboard.dashboardmanager.saveFailed");
        return SUCCESS;
    }

    // Redirects use only the container context path and the constant dashboard route.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "UNVALIDATED_REDIRECT",
            justification = "Container context path plus a constant application route; no request parameter determines the destination")
    private static String redirectToDashboardList(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + MANAGER_PATH);
        return NONE;
    }

    private void requireDashboardPrivilege(LoggedInInfo loggedInInfo, String privilege, String auditMessage) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardManager", privilege, null)) {
            if (loggedInInfo != null) {
                LogAction.addLog(loggedInInfo, "DashboardManager2Action", "Dashboard", null, null, auditMessage);
            }
            throw new SecurityException("missing required sec object (_dashboardManager)");
        }
    }

    private static Integer positiveId(String raw) {
        try {
            int id = Integer.parseInt(raw);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
