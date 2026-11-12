/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.service.ReportProviderRoleService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Read-only report access never grants permission to change provider security roles.
 *
 * @since 2026-10-17
 */
public class ReportProviderRoles2Action extends ActionSupport {
    private final SecurityInfoManager security = SpringUtils.getBean(SecurityInfoManager.class);
    private final ReportProviderRoleService roles = SpringUtils.getBean(ReportProviderRoleService.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo user = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (user == null || !security.hasPrivilege(user, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report r)");
        }
        boolean update = request.getParameter("buttonUpdate") != null;
        boolean add = request.getParameter("submit") != null;
        if (update || add) {
            if (!"POST".equals(request.getMethod())) {
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
                return NONE;
            }
            // POST is validated by the application's blocking CSRFGuard filter.
            if (!roles.canManage(user)) throw new SecurityException("missing required sec object (_admin.userAdmin w)");
            try {
                if (update && !add) {
                    String provider = request.getParameter("providerId");
                    roles.update(user, provider, request.getParameter("name" + provider));
                } else if (add && !update && ResourceBundle.getBundle("oscarResources", request.getLocale())
                        .getString("report.reportonbilledvisitprovider.btnAddRoles").equals(request.getParameter("submit"))) {
                    Map<String, String> requested = new LinkedHashMap<>();
                    for (String name : request.getParameterMap().keySet()) {
                        if (name.startsWith("type")) requested.put(name.substring(4), request.getParameter(name));
                    }
                    roles.add(user, requested);
                } else {
                    throw new IllegalArgumentException("Invalid role operation.");
                }
            } catch (IllegalArgumentException invalid) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid provider role selection");
                return NONE;
            }
        } else if (!java.util.Set.of("GET", "HEAD", "POST").contains(request.getMethod())) {
            response.setHeader("Allow", "GET, HEAD, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        request.setAttribute("canManageProviderRoles", roles.canManage(user));
        return SUCCESS;
    }
}
