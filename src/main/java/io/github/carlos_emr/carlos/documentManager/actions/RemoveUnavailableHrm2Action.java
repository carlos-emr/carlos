/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.documentManager.UnavailableHrmAttachmentService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import java.io.IOException;

/** CSRFGuard-protected native endpoint; tokens identify relationships only in the current session. */
public class RemoveUnavailableHrm2Action extends ActionSupport {
    @Override
    public String execute() throws IOException {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST"); response.sendError(405); return NONE;
        }
        var info = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (info == null) { response.sendError(403); return NONE; }
        try {
            SpringUtils.getBean(UnavailableHrmAttachmentService.class).remove(info,
                    request.getParameter("selection"), request.getSession(false));
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":true}");
        } catch (SecurityException denied) {
            response.sendError(403);
        } catch (IllegalArgumentException expired) {
            response.sendError(409);
        } catch (RuntimeException failure) {
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error("Unavailable attachment removal failed", failure);
            response.sendError(500);
        }
        return NONE;
    }
}
