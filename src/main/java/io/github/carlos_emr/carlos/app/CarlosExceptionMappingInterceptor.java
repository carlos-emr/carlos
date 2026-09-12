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

import java.util.List;
import java.util.UUID;

import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionInvocation;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.config.entities.ExceptionMappingConfig;
import org.apache.struts2.interceptor.ExceptionMappingInterceptor;

/**
 * The exception interceptor every CARLOS Struts stack runs first (see {@code carlos-default} in
 * {@code struts.xml}). It keeps struts-default's exception-mapping contract, an action's uncaught
 * exception still resolves to the {@code error} / {@code securityError} result its package maps,
 * and changes what happens around that mapping:
 *
 * <ul>
 *   <li><b>Nothing is silent.</b> struts-default's interceptor logs nothing unless configured to,
 *       so an action that died in a {@code NullPointerException} rendered the error page and left
 *       no trace. Every mapped exception is now logged once: unexpected failures at ERROR with the
 *       stack trace, {@link SecurityException} (an authorization refusal, an expected event) at WARN
 *       without one.</li>
 *   <li><b>One reference ties the screen to the log.</b> Each failure gets an incident id, logged
 *       and exposed to the result page as the {@value #INCIDENT_ID_ATTRIBUTE} request attribute, so
 *       "it just showed an error" reports arrive with the string that finds the trace.</li>
 *   <li><b>Nothing about the failure reaches the browser.</b> The exception is not pushed onto the
 *       value stack (struts-default's {@code publishException} did, exposing {@code exception} and
 *       {@code exceptionStack} to any page), and the log line carries no request parameters, no
 *       query string and no exception message: any of those can hold PHI. The class name, the
 *       action, the method, the request path, and the provider number (sanitised) are enough to find
 *       the site; the trace itself goes to the log through the throwable, which is what every other
 *       {@code logger.error(msg, e)} in the application already does.</li>
 *   <li><b>The status is real.</b> A mapped exception used to leave the response at 200 unless the
 *       result JSP fixed it up. It is 500 now, and 403 for a {@link SecurityException}, set before
 *       the result renders when the response is still open, so AJAX callers and monitoring see the
 *       failure too.</li>
 * </ul>
 *
 * <p>An exception no mapping covers is rethrown, exactly as before, and reaches the container's
 * error page through web.xml.</p>
 *
 * @since 2026-09
 */
public class CarlosExceptionMappingInterceptor extends ExceptionMappingInterceptor {

    private static final long serialVersionUID = 1L;

    /** Request attribute carrying the incident id to the result page. */
    public static final String INCIDENT_ID_ATTRIBUTE = "carlosIncidentId";

    private static final Logger LOGGER = LogManager.getLogger(CarlosExceptionMappingInterceptor.class);

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        try {
            return invocation.invoke();
        } catch (Exception e) {
            List<ExceptionMappingConfig> mappings = invocation.getProxy().getConfig().getExceptionMappings();
            ExceptionMappingConfig mapping = findMappingFromExceptions(mappings, e);
            if (mapping == null) {
                throw e;
            }
            String incidentId = newIncidentId();
            HttpServletRequest request = ServletActionContext.getRequest();
            HttpServletResponse response = ServletActionContext.getResponse();
            boolean securityRefusal = e instanceof SecurityException;

            logIncident(incidentId, e, securityRefusal, invocation, request);

            if (request != null) {
                request.setAttribute(INCIDENT_ID_ATTRIBUTE, incidentId);
            }
            if (response != null && !response.isCommitted()) {
                response.setStatus(securityRefusal
                        ? HttpServletResponse.SC_FORBIDDEN
                        : HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            return mapping.getResult();
        }
    }

    /**
     * A fresh, unguessable reference for one failure. It is not a secret, only a correlation key,
     * so a random UUID is the right shape: unique across nodes and restarts without coordination,
     * and free of anything derived from the request.
     */
    static String newIncidentId() {
        return UUID.randomUUID().toString();
    }

    private static void logIncident(String incidentId, Exception e, boolean securityRefusal,
                                    ActionInvocation invocation, HttpServletRequest request) {
        String actionName = LogSafe.sanitize(invocation.getProxy().getActionName());
        String method = request == null ? "n/a" : LogSafe.sanitize(request.getMethod());
        // Path only: the query string is where identifiers, and sometimes clinical text, travel.
        String path = request == null ? "n/a" : LogSafe.sanitizeUri(request.getRequestURI());
        String provider = LogSafe.sanitize(providerNo(request));
        String exceptionType = e.getClass().getName();

        if (securityRefusal) {
            // The message is the application's own static text ("missing required sec object (_con)"),
            // and there is nothing a trace would add to "this provider lacks this privilege".
            LOGGER.warn("Authorization refused [incident {}] {} in action {} ({} {}) provider={}: {}",
                    incidentId, exceptionType, actionName, method, path, provider, LogSafe.sanitize(e.getMessage()));
        } else {
            LOGGER.error("Unhandled {} [incident {}] in action {} ({} {}) provider={}",
                    exceptionType, incidentId, actionName, method, path, provider, e);
        }
    }

    private static String providerNo(HttpServletRequest request) {
        if (request == null) {
            return "anonymous";
        }
        try {
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            String providerNo = loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo();
            return providerNo == null ? "anonymous" : providerNo;
        } catch (RuntimeException lookupFailure) {
            // Logging the failure must never become a second failure; the incident is what matters.
            return "unknown";
        }
    }
}
