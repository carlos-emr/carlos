/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Protects the role-changing operations on the billed-visit provider settings page. */
public final class ViewBilledVisitProvider2Action extends ActionSupport {
    private final SecurityInfoManager security = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo login = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (login == null || !security.hasPrivilege(login, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report r)");
        }

        boolean changesRoles = hasValue(request, "buttonUpdate") || hasValue(request, "submit");
        String method = request.getMethod();
        if ((changesRoles && !"POST".equals(method))
                || (!"GET".equals(method) && !"HEAD".equals(method) && !"POST".equals(method))) {
            response.setHeader("Allow", changesRoles ? "POST" : "GET, HEAD, POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        if (changesRoles && !security.hasPrivilege(login, "_admin", "w", null)
                && !security.hasPrivilege(login, "_admin.userAdmin", "w", null)) {
            throw new SecurityException("missing required write privilege (_admin or _admin.userAdmin)");
        }
        return SUCCESS;
    }

    private static boolean hasValue(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value != null && !value.isEmpty();
    }
}
