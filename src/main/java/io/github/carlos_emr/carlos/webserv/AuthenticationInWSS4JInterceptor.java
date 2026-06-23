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

import java.util.HashMap;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.logging.log4j.Logger;
import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.log.LogAction;

/**
 * As of WSS 1.6 we no longer need InInterceptors for authentication, that's now moved to the Validator classes.
 * We still want this interceptor here though as it's the only way I currently know of to make excludes for a global
 * sec filter.
 */
public class AuthenticationInWSS4JInterceptor extends WSS4JInInterceptor implements CallbackHandler {
    private static final Logger logger = MiscUtils.getLogger();

    @Autowired
    private OscarUsernameTokenValidator oscarUsernameTokenValidator;

    public AuthenticationInWSS4JInterceptor() {
        // Properties will be set in postConstruct() after dependency injection
    }

    @PostConstruct
    public void initialize() {
        HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put(WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
        properties.put(WSHandlerConstants.PASSWORD_TYPE, WSS4JConstants.PW_TEXT);
        properties.put(WSHandlerConstants.PW_CALLBACK_REF, this);

        setProperties(properties);
    }

    @Override
    public void handleMessage(SoapMessage message) {
        HttpServletRequest request = (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);
        if (request == null) return; // it's an outgoing request
        String ip = request.getRemoteAddr();

        // Ensure our custom validator is used for this message
        message.put("ws-security.ut.validator", oscarUsernameTokenValidator);

        try {
            performSecurityCheck(message);

            // if it gets here that means it succeeded
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromRequest(request);

            OscarLog oscarLog = new OscarLog();
            oscarLog.setProviderNo(loggedInInfo.getLoggedInProviderNo());
            oscarLog.setAction("WS_LOGIN_SUCCESS");
            oscarLog.setIp(ip);
            LogAction.addLogSynchronous(oscarLog);
        } catch (SoapFault e) {
            logger.debug("exception thrown", e);

            // this means wrong user/password
            OscarLog oscarLog = new OscarLog();
            oscarLog.setAction("WS_LOGIN_FAILURE");
            oscarLog.setIp(ip);
            LogAction.addLogSynchronous(oscarLog);

            // Map the WS-Security failure to the most appropriate HTTP status so the
            // Soap*FaultOutInterceptor propagates it as the response code; otherwise CXF
            // defaults the fault to HTTP 500 and bad credentials read as a server error.
            // CXF has already replaced the fault body with a safe (obscured) message, so
            // only the status code is differentiated here -- no extra detail is leaked.
            e.setStatusCode(resolveFaultStatusCode(e));

            throw e;
        }
    }

    /**
     * Runs the inbound WS-Security processing (delegates to {@link WSS4JInInterceptor}).
     * Extracted as a seam so the surrounding authentication logging and HTTP status
     * mapping can be unit-tested without standing up a full CXF/WSS4J SOAP pipeline.
     */
    protected void performSecurityCheck(SoapMessage message) {
        super.handleMessage(message);
    }

    /**
     * Maps a WS-Security processing failure to an HTTP status without disclosing which
     * check failed:
     * <ul>
     *   <li>401 for authentication failures (bad/absent credentials or token);</li>
     *   <li>400 for client-side malformed, invalid, unsupported, or expired security headers;</li>
     *   <li>500 when the failure cannot be positively attributed to the request (callback,
     *       configuration, or parse errors), so genuine server faults are not mislabelled as
     *       authentication failures.</li>
     * </ul>
     */
    static int resolveFaultStatusCode(SoapFault fault) {
        WSSecurityException wss = findWssCause(fault);
        if (wss == null) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        switch (wss.getErrorCode()) {
            case FAILED_AUTHENTICATION:
            case SECURITY_TOKEN_UNAVAILABLE:
                return HttpServletResponse.SC_UNAUTHORIZED;
            case MESSAGE_EXPIRED:
            case INVALID_SECURITY:
            case INVALID_SECURITY_TOKEN:
            case UNSUPPORTED_SECURITY_TOKEN:
            case UNSUPPORTED_ALGORITHM:
            // FAILED_CHECK / FAILED_SIGNATURE are signature/digest verification failures.
            // Mapped to 400 (not 401) deliberately: it avoids disclosing whether the
            // credentials were structurally invalid versus recognized-but-wrong.
            case FAILED_CHECK:
            case FAILED_SIGNATURE:
                return HttpServletResponse.SC_BAD_REQUEST;
            default:
                // FAILURE / FAILED_ENCRYPTION / SECURITY_ERROR and anything else are not
                // reliably client-side -> treat as an unexpected server error.
                return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
    }

    private static WSSecurityException findWssCause(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof WSSecurityException) {
                return (WSSecurityException) cause;
            }
        }
        return null;
    }

    @Override
    public void handle(Callback[] callbacks) {
        // do nothing
    }
}
