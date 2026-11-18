/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.report.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Keeps report previews readable while authorizing the day sheet's appointment mutation. */
public final class ViewReportDaySheet2Action extends ActionSupport {
    private final ViewReport2Action reportGate = new ViewReport2Action();
    private final SecurityInfoManager security = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        String result = reportGate.execute();
        if (!SUCCESS.equals(result)) return result;
        var request = ServletActionContext.getRequest();
        if ("POST".equals(request.getMethod())
                && "true".equals(request.getParameter("acknowledge"))
                && !"all".equals(request.getParameter("dsmode"))
                && !security.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request),
                        "_appointment", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_appointment w)");
        }
        return SUCCESS;
    }
}
