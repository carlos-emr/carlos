/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.dashboard.admin;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.commn.model.Dashboard;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Native administration of indicator dashboards, without the retired Angular application. */
public class DashboardManager2Action extends ActionSupport {
    private final transient DashboardManager dashboardManager;
    private final transient SecurityInfoManager securityInfoManager;

    public DashboardManager2Action() {
        this(SpringUtils.getBean(DashboardManager.class), SpringUtils.getBean(SecurityInfoManager.class));
    }

    DashboardManager2Action(DashboardManager dashboardManager, SecurityInfoManager securityInfoManager) {
        this.dashboardManager = dashboardManager;
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "UNVALIDATED_REDIRECT",
            justification = "Container context path plus a constant application route; no request parameter determines the destination")
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        String method = request.getMethod();
        String operation = request.getParameter("operation");
        boolean post = "POST".equals(method);
        if ((!post && !"GET".equals(method) && !"HEAD".equals(method)) || (!post && operation != null)) {
            response.setHeader("Allow", operation == null ? "GET, HEAD, POST" : "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // Saves also need read access: the edit must load and preserve the existing metadata.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardManager", SecurityInfoManager.READ, null)
                || (post && !securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardManager", SecurityInfoManager.WRITE, null))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }
        List<Dashboard> dashboards = dashboardManager.getDashboards(loggedInInfo);
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
        if ("status".equals(operation)) {
            String active = request.getParameter("active");
            if (selected == null || (!"true".equals(active) && !"false".equals(active))) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            dashboardManager.toggleDashboardActive(loggedInInfo, selected.getId(), Boolean.valueOf(active));
        } else {
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
            dashboard.setId(selected == null ? null : selected.getId());
            dashboard.setName(name);
            dashboard.setDescription(description);
            if (!dashboardManager.addDashboard(loggedInInfo, dashboard)) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                request.setAttribute("dashboardManagerError", "dashboard.dashboardmanager.saveFailed");
                return SUCCESS;
            }
        }
        response.sendRedirect(request.getContextPath() + "/web/dashboard/admin/DashboardManager");
        return NONE;
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
