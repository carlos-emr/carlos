/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.webserv.rest.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.logging.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.carlos.log.LogAction;


// TODO Consider moving this configuration into XML
// TODO Consider dropping this in favour of POJO aspects
// This needs a bit of work. The output looks like
//  "2013-04-25 14:44:34,884 INFO  [WebServiceLoggingAdvice:66] Logging access for execution(OscarSearchResponse io.github.carlos_emr.carlos.rest.webserv.PharmacyService.getPharmacies(Integer,Integer))"
// which isn't very informative..but it's a first stab.


@Aspect
@Component
public class WebServiceLoggingAdvice {

    private static Logger logger = MiscUtils.getLogger();

    /** Audit provider for a call with no authenticated caller. */
    private static final String NO_AUTHENTICATED_CALLER = "N/A";

    @Pointcut("execution(public * io.github.carlos_emr.carlos.webserv.rest.*.*(..))")
    public void pointcut() {
        // Pointcut marker only; AspectJ never executes this body.
    }

    private String getServiceCallDescription(ProceedingJoinPoint joinpoint) {
        Signature signature = joinpoint.getSignature();
        String type = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        return type + "." + methodName;
    }

    /**
     * Writes one {@link OscarLog} audit record per REST call, for success and for failure.
     *
     * <p>The record's provider is the authenticated caller, resolved once before the service runs
     * (see {@link #resolveAuditProviderNo(HttpServletRequest)}), or {@code "N/A"} when there is
     * none. Resolving the caller never creates a session and never changes the call's outcome.</p>
     */
    @Around("execution(public * io.github.carlos_emr.carlos.webserv.rest.*.*(..))")
    public Object logAccess(ProceedingJoinPoint joinpoint) throws Throwable {
        // Log only the service.method name (never the joinpoint argument values, which can
        // carry PHI or OAuth-derived context) and only at DEBUG so this diagnostic logging is
        // disabled in production by default. The access record itself is still captured in the
        // OscarLog audit store below.
        if (logger.isDebugEnabled()) {
            logger.debug("REST WS access: {}", getServiceCallDescription(joinpoint));
        }

        // Resolved before proceed() and outside the try: an audit lookup failure after a successful
        // call would otherwise be caught below, recorded as FAILURE and rethrown to the client.
        HttpServletRequest request = currentRequest();
        String providerNo = resolveAuditProviderNo(request);

        try {
            Object result = joinpoint.proceed();

            logAccess("REST WS: " + getServiceCallDescription(joinpoint), request, providerNo);
            return result;
        } catch (Throwable t) {
            logger.debug("WS Failure", t);

            logAccess("REST WS: FAILURE: " + getServiceCallDescription(joinpoint), request, providerNo);
            throw t;
        }
    }

    private HttpServletRequest currentRequest() {
        Message currentMessage = PhaseInterceptorChain.getCurrentMessage();
        return (HttpServletRequest) currentMessage.get("HTTP.REQUEST");
    }

    /**
     * Resolves the audit author in the same order as {@code AbstractServiceImpl.getLoggedInInfo()}:
     * the request principal first (set by {@code OAuthInterceptor} / {@code WsUtils}), then an
     * existing session (the {@code /ws/rs} surface).
     *
     * <p>Uses {@code getSession(false)}: {@code LoggedInInfo.getLoggedInInfoFromSession(HttpServletRequest)}
     * would create a session for every OAuth call just to audit it (issue #3446).</p>
     *
     * @return the caller's provider number, or {@code "N/A"} with no caller, no provider number, or an
     *     invalidated session
     */
    private String resolveAuditProviderNo(HttpServletRequest request) {
        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromRequest(request);
        if (info == null) {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return NO_AUTHENTICATED_CALLER;
            }
            try {
                info = LoggedInInfo.getLoggedInInfoFromSession(session);
            } catch (IllegalStateException _) {
                // The session was invalidated or expired after the container handed it over; there
                // is no caller left to attribute the call to.
                return NO_AUTHENTICATED_CALLER;
            }
        }
        String providerNo = info == null ? null : info.getLoggedInProviderNo();
        // Checked here because OscarLog.setProviderNo trims blanks to null, which would store NULL.
        return StringUtils.isBlank(providerNo) ? NO_AUTHENTICATED_CALLER : providerNo;
    }

    private void logAccess(String action, HttpServletRequest request, String providerNo) {
        OscarLog log = new OscarLog();
        log.setAction(action);
        log.setProviderNo(providerNo);

        log.setIp(request.getRemoteAddr());
        log.setContent(request.getRequestURL().toString());
        log.setData(request.getParameterMap().toString());

        LogAction.addLogSynchronous(log);
    }

}
