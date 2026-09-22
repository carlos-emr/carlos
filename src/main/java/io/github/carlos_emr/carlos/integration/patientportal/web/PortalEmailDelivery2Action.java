/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import io.github.carlos_emr.carlos.integration.patientportal.PortalEmailDeliveryService;
import io.github.carlos_emr.carlos.integration.patientportal.PortalEmailDeliveryService.RecoveryRefusedException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Shows and recovers the portal password of one encrypted email, never resending it.
 *
 * <p>GET only reads. POST runs one recovery operation and then redirects back to the GET, so a
 * browser refresh cannot resubmit it. Patient scope and portal permissions are enforced by
 * {@link PortalEmailDeliveryService}, from the stored email rather than from request data.</p>
 */
public class PortalEmailDelivery2Action extends ActionSupport {
    private final Logger logger = MiscUtils.getLogger();
    private final SecurityInfoManager security;
    private final PortalEmailDeliveryService delivery;

    public PortalEmailDelivery2Action(SecurityInfoManager security, PortalEmailDeliveryService delivery) {
        this.security = security;
        this.delivery = delivery;
    }

    @Override
    public String execute() throws Exception {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        boolean post = "POST".equals(request.getMethod());
        if (!post && !"GET".equals(request.getMethod())) {
            response.setHeader("Allow", "GET, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        var user = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!security.hasPrivilege(user, "_email", SecurityInfoManager.READ, null)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }
        int id;
        try {
            id = Integer.parseInt(request.getParameter("emailLogId"));
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        try {
            if (post) {
                delivery.recover(user, id, request.getParameter("operation"),
                        "true".equals(request.getParameter("confirmed")));
                response.sendRedirect(request.getContextPath() + "/email/portalDelivery?emailLogId=" + id);
                return NONE;
            }
        } catch (SecurityException denied) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        } catch (RecoveryRefusedException refused) {
            response.setStatus(refused.isConflict() ? HttpServletResponse.SC_CONFLICT : HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("portalRecoveryErrorKey", refused.messageKey());
        } catch (IllegalArgumentException invalid) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        } catch (RuntimeException unavailable) {
            // Class name only: portal messages may carry credentials or PHI. A reload re-reads the durable state.
            logger.warn("Portal email recovery failed; emailLogId={}; causeType={}", id, unavailable.getClass().getSimpleName());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("portalRecoveryErrorKey", "email.portalDelivery.error.unavailable");
        }
        try {
            var emailLog = delivery.findForRecovery(user, id);
            request.setAttribute("emailLog", emailLog);
            request.setAttribute("recoveryView", PortalEmailDeliveryService.classify(emailLog).name());
        } catch (SecurityException denied) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        } catch (IllegalArgumentException notFound) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        return SUCCESS;
    }
}
