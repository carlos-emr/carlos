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

/*
 * Written by Brandon Aubie <brandon@aubie.ca>
 */

/**
 * OAuthInterceptor
 *
 * Purpose:
 *   CXF phase interceptor that wires OAuth1 requests into OSCAR’s provider model.
 *
 * Responsibilities:
 *   • Detect OAuth 1.0a requests on incoming HTTP messages.
 *   • Pull consumer key and access token directly from request parameters.
 *   • Resolve the providerNo from the access token using OscarOAuthDataProvider.
 *   • Attach a LoggedInInfo object to the HttpServletRequest for downstream use.
 *
 * Design notes:
 *   • This version does NOT perform signature verification — trusted flow assumes
 *     requests reach this point only after valid OAuth handling upstream.
 *   • Keeps state lightweight; avoids DB lookups beyond resolving providerNo → Provider.
 *   • Runs in Phase.PRE_INVOKE to ensure endpoints see authenticated context only.
 *
 * Error handling:
 *   • Throws OAuth1Exception for missing/invalid consumer keys or providers.
 *   • Wraps errors in CXF Faults for consistent exception handling.
 *
 * Why simplified:
 *   • Replaces older CXF OAuth filter with a minimal interceptor that fits the
 *     current request format and avoids unused AppDefinition / verifier logic.
 */

package io.github.carlos_emr.carlos.webserv.oauth.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.phase.PhaseInterceptor;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.login.AppOAuth1Config;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;

@Component
public class OAuthInterceptor implements PhaseInterceptor<Message> {

    private static final Logger logger = MiscUtils.getLogger();

    /** OscarLog action recorded on a successful REST OAuth authentication (parity with SOAP WS_LOGIN_SUCCESS). */
    private static final String OAUTH_LOGIN_SUCCESS = "OAUTH_LOGIN_SUCCESS";
    /** OscarLog action recorded on a rejected REST OAuth authentication (parity with SOAP WS_LOGIN_FAILURE). */
    private static final String OAUTH_LOGIN_FAILURE = "OAUTH_LOGIN_FAILURE";

    @Autowired
    private OscarOAuthDataProvider oauthDataProvider;

    @Autowired
    private ProviderDao providerDao;

    @Resource
    private OAuth1SignatureVerifier verifier;

    @Override
    public String getPhase() { return Phase.PRE_INVOKE; }

    @Override
    public void handleMessage(Message message) throws Fault {
        HttpServletRequest req =
            (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);

        // 1) Fail closed: this interceptor guards the OAuth-only REST surface
        // (/ws/services). A request that carries no OAuth credentials cannot be
        // authenticated here, so reject it (401) instead of silently passing it
        // through. Passing it through left every handler that omits its own
        // privilege check reachable by an anonymous caller (unauthenticated PHI
        // reads / IDOR / mutations) — see #2798. Session/browser clients use the
        // separate session REST surface at /ws/rs (AuthenticationInInterceptor),
        // so this does not affect them. A null request (non-HTTP transport) also
        // cannot be authenticated, so it fails closed the same way.
        if (req == null || !OAuthRequestParser.isOAuth1Request(req)) {
            String remoteAddr = (req != null) ? req.getRemoteAddr() : null;
            auditAuthFailure(remoteAddr, null);
            throw toFault(new OAuth1Exception(401, "authentication_required"));
        }

        // Hoisted so the audit on both success and the auth-failure paths can record them.
        String ip = req.getRemoteAddr();
        String consumerKey = null;

        try {
            // 2) Pull oauth params
            Map<String, String> oauth = OAuthRequestParser.extractOAuthParameters(req);
            consumerKey        = oauth.get("oauth_consumer_key");
            String token       = oauth.get("oauth_token");

            if (consumerKey == null || consumerKey.isEmpty()) {
                throw new OAuth1Exception(400, "missing_consumer_key");
            }
            if (token == null || token.isEmpty()) {
                throw new OAuth1Exception(400, "missing_access_token");
            }

            // 3) Load client to get consumer secret
            Client client = oauthDataProvider.getClient(consumerKey);
            if (client == null) {
                throw new OAuth1Exception(401, "invalid_consumer");
            }

            // 4) Verify signature + timestamp freshness
            AppOAuth1Config cfg = new AppOAuth1Config();
            cfg.setConsumerKey(client.getConsumerKey());
            cfg.setConsumerSecret(client.getSecret());

            // verifier will:
            //  - collect auth/query/form params
            //  - enforce oauth_timestamp skew (±5m)
            //  - choose ACCESS token secret for resource calls
            //  - recompute HMAC-SHA1 and compare safely
            String tokenFromSig = verifier.verifySignature(req, cfg);

            // defensively ensure the same token was signed
            if (!token.equals(tokenFromSig)) {
                throw new OAuth1Exception(401, "invalid_signature");
            }

            // 5) Resolve provider from ACCESS token and attach LoggedInInfo
            String providerNo = oauthDataProvider.getProviderNoByAccessToken(token);
            Provider provider = providerDao.getProvider(providerNo);
            if (provider == null) {
                throw new OAuth1Exception(401, "unknown_provider");
            }

            LoggedInInfo info = new LoggedInInfo();
            info.setLoggedInProvider(provider);
            req.setAttribute(info.getLoggedInInfoKey(), info);

            // 6) Audit the successful authentication (parity with SOAP WS_LOGIN_SUCCESS).
            auditAuthSuccess(providerNo, ip, consumerKey);

        } catch (OAuth1Exception e) {
            // Explicit auth outcome (e.g. 400 missing param, 401 invalid consumer/token):
            // carries its own intended status code. Record the rejection in the audit trail.
            auditAuthFailure(ip, consumerKey);
            throw toFault(e);
        } catch (IllegalArgumentException badSigOrTime) {
            // from verifier: missing/stale timestamp, bad signature, unknown token, etc.
            // These are client-side authentication failures -> 401.
            auditAuthFailure(ip, consumerKey);
            throw toFault(new OAuth1Exception(401, "invalid_signature"));
        } catch (Exception e) {
            // Anything else is an unexpected server-side failure (e.g. a data-access error),
            // NOT an authentication problem. Log the cause for diagnosis but return a generic
            // 500 so genuine outages are not masked as "bad credentials", and so the client
            // body reveals nothing about the internal failure. Deliberately NOT recorded as an
            // OAUTH_LOGIN_FAILURE so the audit trail stays distinct from genuine auth rejections.
            logger.error("Unexpected error during OAuth1 authentication", e);
            throw toFault(new OAuth1Exception(500, "oauth_processing_error"));
        }
    }

    /**
     * Records a successful REST OAuth authentication in the sanctioned OscarLog audit trail,
     * mirroring {@code AuthenticationInWSS4JInterceptor}'s WS_LOGIN_SUCCESS entry.
     *
     * <p>Only safe identifiers are persisted: the resolved providerNo, the remote IP, and the
     * consumer key. The oauth_token (bearer credential), consumer secret, and signature are
     * never logged.
     */
    private void auditAuthSuccess(String providerNo, String ip, String consumerKey) {
        // An audit-write hiccup must never deny an already-authenticated request, so guard the
        // call locally instead of relying on LogAction's internal exception handling.
        try {
            OscarLog oscarLog = new OscarLog();
            oscarLog.setProviderNo(providerNo);
            oscarLog.setAction(OAUTH_LOGIN_SUCCESS);
            oscarLog.setIp(ip);
            oscarLog.setContent(safeConsumerKey(consumerKey));
            LogAction.addLogSynchronous(oscarLog);
        } catch (Exception e) {
            logger.error("Failed to write OAUTH_LOGIN_SUCCESS audit entry", e);
        }
    }

    /**
     * Records a rejected REST OAuth authentication in the sanctioned OscarLog audit trail,
     * mirroring {@code AuthenticationInWSS4JInterceptor}'s WS_LOGIN_FAILURE entry. No providerNo
     * is recorded because the request never resolved to an authenticated provider.
     */
    private void auditAuthFailure(String ip, String consumerKey) {
        // Guard the audit write so a logging failure cannot replace the intended 400/401 Fault
        // with an unexpected error surfaced to the caller.
        try {
            OscarLog oscarLog = new OscarLog();
            oscarLog.setAction(OAUTH_LOGIN_FAILURE);
            oscarLog.setIp(ip);
            oscarLog.setContent(safeConsumerKey(consumerKey));
            LogAction.addLogSynchronous(oscarLog);
        } catch (Exception e) {
            logger.error("Failed to write OAUTH_LOGIN_FAILURE audit entry", e);
        }
    }

    /**
     * The consumer key is a client-supplied identifier; sanitize it before it enters the audit
     * trail. Returns {@code null} when no consumer key was supplied so no content is recorded.
     */
    private static String safeConsumerKey(String consumerKey) {
        if (consumerKey == null || consumerKey.isEmpty()) {
            return null;
        }
        return LogSafe.sanitize(consumerKey);
    }

    /**
     * Wraps an {@link OAuth1Exception} in a CXF {@link Fault} that carries the intended
     * HTTP status code. Without {@link Fault#setStatusCode(int)} CXF discards the OAuth
     * status and defaults an in-interceptor fault to HTTP 500, so a 400/401 authentication
     * failure would surface to API callers as a server error.
     */
    private static Fault toFault(OAuth1Exception e) {
        Fault fault = new Fault(e);
        fault.setStatusCode(e.getHttpCode());
        return fault;
    }

    @Override public void handleFault(Message message) { /* no-op */ }
    @Override public Set<String> getBefore() { return Collections.emptySet(); }
    @Override public Set<String> getAfter()  { return Collections.emptySet(); }
    @Override public Collection<PhaseInterceptor<? extends Message>> getAdditionalInterceptors() { return null; }
    @Override public String getId() { return getClass().getSimpleName(); }
}
