/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import io.github.carlos_emr.carlos.integration.patientportal.PortalEmailDelivery;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Staff recovery of password publication; this action never sends an email. */
public class PortalEmailDelivery2Action extends ActionSupport {
    @Override
    public String execute() throws Exception {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        if (!"GET".equals(request.getMethod()) && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "GET, POST");
            response.sendError(405);
            return NONE;
        }
        var user = LoggedInInfo.getLoggedInInfoFromSession(request);
        var security = SpringUtils.getBean(SecurityInfoManager.class);
        if (!security.hasPrivilege(user, "_email", SecurityInfoManager.READ, null)) {
            response.sendError(403);
            return NONE;
        }
        int id;
        try {
            id = Integer.parseInt(request.getParameter("emailLogId"));
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            response.sendError(400);
            return NONE;
        }
        var delivery = SpringUtils.getBean(PortalEmailDelivery.class);
        try {
            if ("POST".equals(request.getMethod())) {
                delivery.recover(user, id, request.getParameter("operation"),
                        "true".equals(request.getParameter("confirmed")));
            }
            request.setAttribute("emailLog", delivery.findForRecovery(user, id));
        } catch (SecurityException denied) {
            response.sendError(403);
            return NONE;
        } catch (IllegalArgumentException invalid) {
            response.sendError(400);
            return NONE;
        } catch (RuntimeException unavailable) {
            response.setStatus(503);
            request.setAttribute("portalRecoveryError", true);
            // Keep failures credential-free. A subsequent GET re-reads the durable state.
        }
        return SUCCESS;
    }
}
