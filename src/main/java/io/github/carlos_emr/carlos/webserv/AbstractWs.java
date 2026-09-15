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


package io.github.carlos_emr.carlos.webserv;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.xml.ws.WebServiceContext;
import jakarta.xml.ws.handler.MessageContext;

import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;


public abstract class AbstractWs {
    protected static final int GZIP_THRESHOLD = 0;

    private static final Logger logger = MiscUtils.getLogger();

    @Resource
    protected WebServiceContext context;

    /**
     * Injected rather than looked up per call. Optional so an instance created outside the Spring
     * container still resolves a checker through the {@link SpringUtils} fallback below.
     */
    @Autowired(required = false)
    private SecurityInfoManager securityInfoManager;

    protected HttpServletRequest getHttpServletRequest() {
        MessageContext messageContext = context.getMessageContext();
        HttpServletRequest request = (HttpServletRequest) messageContext.get(MessageContext.SERVLET_REQUEST);
        return (request);
    }

    protected Security getLoggedInSecurity() {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        return (loggedInInfo.getLoggedInSecurity());
    }

    protected Provider getLoggedInProvider() {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        return (loggedInInfo.getLoggedInProvider());
    }

    protected LoggedInInfo getLoggedInInfo() {
        return (LoggedInInfo.getLoggedInInfoFromRequest(getHttpServletRequest()));
    }

    /**
     * Resolves the privilege checker.
     *
     * <p>Every concrete {@code *Ws} is a Spring {@code @Component}, so the injected field is
     * normally what answers here. The {@link SpringUtils} fallback stays for the case where CXF
     * instantiates a service outside the Spring container: {@link #requirePrivilege} must never
     * degrade to "no checker, no check". The field matters for cost as much as style — the
     * per-patient loops in {@code DemographicWs} would otherwise pay a container lookup per
     * patient.</p>
     */
    protected SecurityInfoManager getSecurityInfoManager() {
        return securityInfoManager != null ? securityInfoManager : SpringUtils.getBean(SecurityInfoManager.class);
    }

    protected void requirePrivilege(String objectName, String privilege) {
        requirePrivilege(getLoggedInInfo(), objectName, privilege, null);
    }

    protected void requirePrivilege(String objectName, String privilege, String demographicNo) {
        requirePrivilege(getLoggedInInfo(), objectName, privilege, demographicNo);
    }

    protected void requirePrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege) {
        requirePrivilege(loggedInInfo, objectName, privilege, null);
    }

    /**
     * Fails the call unless the authenticated caller holds {@code privilege} on {@code objectName}.
     *
     * <p>WSS4J authentication establishes <em>who</em> the caller is; this establishes what they
     * may reach. Call it before any manager or DAO invocation so a denial cannot be preceded by a
     * PHI-bearing load.</p>
     *
     * <p>A null {@code loggedInInfo} is denied here rather than passed down.
     * {@code SecurityInfoManagerImpl.hasPrivilege} would dereference it, catch its own
     * {@code NullPointerException} and return false — fail-closed, but only after writing a
     * misleading "Error checking privileges" stack trace for what is simply an unauthenticated
     * request.</p>
     *
     * @param loggedInInfo the authenticated caller, or null when the request carried no session
     * @param objectName   security object name, e.g. {@code _demographic}
     * @param privilege    one of {@code r}, {@code u}, {@code w}, {@code d}, {@code x}
     * @param demographicNo patient scope for a {@code _object$<demographicNo>} override, or null
     *                      for the unscoped check
     * @throws SecurityException when the caller is unauthenticated or lacks the privilege
     */
    protected void requirePrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, String demographicNo) {
        if (loggedInInfo == null || !getSecurityInfoManager().hasPrivilege(loggedInInfo, objectName, privilege, demographicNo)) {
            auditDenial(loggedInInfo, objectName, privilege, demographicNo);
            throw new SecurityException("missing required sec object (" + objectName + ")");
        }
    }

    /**
     * Records an authorization denial in the audit log.
     *
     * <p>Authentication failures are already audited by {@code AuthenticationInWSS4JInterceptor};
     * without this, an authenticated caller being refused PHI left no trace at all, which is
     * precisely the event PIPEDA expects to find in the log. Only identifiers are recorded — never
     * clinical content.</p>
     *
     * <p>Auditing must not be able to change the outcome, so any failure here is swallowed: a
     * denial stays a denial even if the audit write fails.</p>
     */
    private void auditDenial(LoggedInInfo loggedInInfo, String objectName, String privilege, String demographicNo) {
        try {
            String data = "object=" + LogSafe.sanitize(objectName)
                    + " privilege=" + LogSafe.sanitize(privilege)
                    + (demographicNo != null ? " demographicNo=" + LogSafe.sanitize(demographicNo) : "");
            if (loggedInInfo == null) {
                logger.warn("SOAP authorization denied for unauthenticated request: {}", data);
                return;
            }
            LogAction.addLogSynchronous(loggedInInfo, "ws.accessDenied", data);
        } catch (Exception auditFailure) {
            logger.warn("Failed to audit SOAP authorization denial", auditFailure);
        }
    }
}
