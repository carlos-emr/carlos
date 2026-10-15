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
package io.github.carlos_emr.carlos.integration.patientportal.web;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalConfigurationException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.integration.patientportal.PortalEmailDeliveryService;
import io.github.carlos_emr.carlos.integration.patientportal.PortalEmailDeliveryService.RecoveryRefusedException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.BeanCreationException;

/**
 * Shows and recovers the portal password of one encrypted email, never resending it.
 *
 * <p>GET only reads. POST runs one recovery operation and then redirects back to the GET, so a
 * browser refresh cannot resubmit it. Patient scope and portal permissions are enforced by
 * {@link PortalEmailDeliveryService}, from the stored email rather than from request data.</p>
 *
 * @since 2026-09-15
 */
public class PortalEmailDelivery2Action extends ActionSupport {
    private final Logger logger = MiscUtils.getLogger();
    private final SecurityInfoManager security;
    private final PortalEmailDeliveryService delivery;

    public PortalEmailDelivery2Action(SecurityInfoManager security, PortalEmailDeliveryService delivery) {
        this.security = security;
        this.delivery = delivery;
    }

    // FindSecBugs UNVALIDATED_REDIRECT: the target is this application's context path, a fixed
    // route and a parsed integer id; no request text reaches the URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "Fixed internal route with an integer id")
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
        } catch (PatientPortalConfigurationException notConfigured) {
            portalNotConfigured(request, response, id);
        } catch (RuntimeException unavailable) {
            // Spring wraps a configuration failure raised while the portal client is built.
            if (unavailable instanceof BeanCreationException notBuilt
                    && notBuilt.contains(PatientPortalConfigurationException.class)) {
                portalNotConfigured(request, response, id);
            } else {
                // Class name only: portal messages may carry credentials or PHI. A reload re-reads the durable state.
                logger.warn("Portal email recovery failed; emailLogId={}; causeType={}", id, unavailable.getClass().getSimpleName());
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                request.setAttribute("portalRecoveryErrorKey", "email.portalDelivery.error.unavailable");
            }
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
        } catch (PatientPortalConfigurationException notConfigured) {
            portalNotConfigured(request, response, id);
        } catch (RuntimeException unavailable) {
            // For example a database fault while reading the stored email.
            logger.warn("Portal email recovery page could not be loaded; emailLogId={}; causeType={}",
                    id, unavailable.getClass().getSimpleName());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("portalRecoveryErrorKey", "email.portalDelivery.error.unavailable");
        }
        return SUCCESS;
    }

    /**
     * Not "the Portal did not respond": the fault is in CARLOS's own portal settings, and recovery
     * sends no email. Switched off is a deployment choice, so it is not logged; a switched-on
     * portal whose settings do not build is a fault.
     */
    private void portalNotConfigured(HttpServletRequest request, HttpServletResponse response, int id) {
        if (PatientPortalSettings.isConfigured()) {
            logger.warn("Portal email recovery refused: the Patient Portal settings are not valid; emailLogId={}", id);
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        request.setAttribute("portalRecoveryErrorKey", "email.portalDelivery.error.notConfigured");
    }
}
