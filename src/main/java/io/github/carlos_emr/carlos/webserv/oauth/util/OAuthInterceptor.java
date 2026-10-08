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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

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
import io.github.carlos_emr.carlos.commn.model.ServiceAccessToken;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;
import io.github.carlos_emr.carlos.webserv.oauth.OAuthScopeEnforcement;
import io.github.carlos_emr.carlos.webserv.oauth.OAuthScopes;

@Component
public class OAuthInterceptor implements PhaseInterceptor<Message> {

    private static final Logger logger = MiscUtils.getLogger();

    /** OscarLog action recorded on a successful REST OAuth authentication (parity with SOAP WS_LOGIN_SUCCESS). */
    private static final String OAUTH_LOGIN_SUCCESS = "OAUTH_LOGIN_SUCCESS";
    /** OscarLog action recorded on a rejected REST OAuth authentication (parity with SOAP WS_LOGIN_FAILURE). */
    private static final String OAUTH_LOGIN_FAILURE = "OAUTH_LOGIN_FAILURE";
    /** OscarLog action recorded once when {@link FailureAuditBudget} starts dropping failure rows (#4429). */
    private static final String OAUTH_LOGIN_FAILURES_SUPPRESSED = "OAUTH_LOGIN_FAILURES_SUPPRESSED";

    @Autowired
    private OscarOAuthDataProvider oauthDataProvider;

    @Autowired
    private ProviderDao providerDao;

    @Resource
    private OAuth1SignatureVerifier verifier;

    private FailureAuditBudget failureAuditBudget = new FailureAuditBudget(System::currentTimeMillis);

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
            auditAuthFailure(remoteAddr, null, false);
            throw toFault(new OAuth1Exception(401, "authentication_required"));
        }

        // Hoisted so the audit on both success and the auth-failure paths can record them.
        String ip = req.getRemoteAddr();
        String consumerKey = null;
        // Set once the request is proven to come from a registered client holding the access token's
        // secret. Refusals after that point (unknown_provider, insufficient_scope) are never budgeted.
        boolean signed = false;

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
            signed = true;

            // 5) Resolve provider AND scopes from a single access-token load (the token's provider and its
            //    granted scopes both come off the same ServiceAccessToken, so we avoid a second lookup that
            //    could race token expiry/revocation between the two reads).
            ServiceAccessToken accessToken = oauthDataProvider.findUnexpiredAccessToken(token);
            if (accessToken == null) {
                throw new OAuth1Exception(401, "unknown_provider");
            }
            String providerNo = accessToken.getProviderNo();
            Provider provider = providerDao.getProvider(providerNo);
            if (provider == null) {
                throw new OAuth1Exception(401, "unknown_provider");
            }

            // 5a) Enforce the granted OAuth scopes (issues #3083, #4419). No-op only when an operator
            //     turned enforcement off or the endpoint is explicitly scope-exempt. Done before attaching
            //     LoggedInInfo so an out-of-scope call never reaches the resource with a security context.
            enforceScope(req, accessToken);

            LoggedInInfo info = new LoggedInInfo();
            info.setLoggedInProvider(provider);
            req.setAttribute(info.getLoggedInInfoKey(), info);

            // 6) Audit the successful authentication (parity with SOAP WS_LOGIN_SUCCESS).
            auditAuthSuccess(providerNo, ip, consumerKey);

        } catch (OAuth1Exception e) {
            // Explicit auth outcome (e.g. 400 missing param, 401 invalid consumer/token):
            // carries its own intended status code. Record the rejection in the audit trail.
            auditAuthFailure(ip, consumerKey, signed);
            throw toFault(e);
        } catch (IllegalArgumentException badSigOrTime) {
            // from verifier: missing/stale timestamp, bad signature, unknown token, etc.
            // These are client-side authentication failures -> 401.
            auditAuthFailure(ip, consumerKey, false);
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
    private void auditAuthFailure(String ip, String consumerKey, boolean signed) {
        // #4429: an anonymous client can call /ws/services as fast as it likes, and each rejection used
        // to be one synchronous log-table insert. The budget bounds the rows; when it closes for an
        // address (or for everyone), one OAUTH_LOGIN_FAILURES_SUPPRESSED row says so, so the audit trail
        // still shows the flood without recording each request of it. Only unauthenticated refusals are
        // budgeted: a correctly signed call refused for its scope or provider comes from a registered
        // client holding a live token, and must always reach the audit trail. Otherwise an anonymous
        // flood could use up the budget and hide a compromised token probing beyond its grant.
        FailureAuditBudget.Decision decision = signed
                ? FailureAuditBudget.Decision.AUDIT
                : failureAuditBudget.admit(ip);
        if (decision == FailureAuditBudget.Decision.SUPPRESS) {
            return;
        }
        // Guard the audit write so a logging failure cannot replace the intended 400/401 Fault
        // with an unexpected error surfaced to the caller.
        try {
            OscarLog oscarLog = new OscarLog();
            oscarLog.setIp(ip);
            if (decision == FailureAuditBudget.Decision.AUDIT) {
                oscarLog.setAction(OAUTH_LOGIN_FAILURE);
                oscarLog.setContent(safeConsumerKey(consumerKey));
            } else {
                oscarLog.setAction(OAUTH_LOGIN_FAILURES_SUPPRESSED);
                oscarLog.setContent(decision == FailureAuditBudget.Decision.SUPPRESS_ADDRESS_FROM_NOW
                        ? "per-address limit reached" : "server-wide limit reached");
                logger.warn("OAuth authentication failures exceeded the audit budget ({}); further "
                        + "OAUTH_LOGIN_FAILURE rows are suppressed for up to {}s",
                        decision == FailureAuditBudget.Decision.SUPPRESS_ADDRESS_FROM_NOW
                                ? "per address" : "server-wide",
                        FailureAuditBudget.WINDOW_MILLIS / 1000);
            }
            LogAction.addLogSynchronous(oscarLog);
        } catch (Exception e) {
            logger.error("Failed to write OAUTH_LOGIN_FAILURE audit entry", e);
        }
    }

    /**
     * Bounds how many {@code OAUTH_LOGIN_FAILURE} rows rejected {@code /ws/services} calls can write
     * (#4429), per client address and server-wide, in fixed one-minute windows.
     *
     * <p>Within a window an address gets {@link #PER_ADDRESS_LIMIT} ordinary rows and then one
     * {@link Decision#SUPPRESS_ADDRESS_FROM_NOW} notice; everything after that is dropped until the window
     * ends. The server-wide limit does the same across all addresses, so a client rotating addresses (an
     * IPv6 prefix, say) cannot get around the per-address limit. The front door's {@code limit_req} is the
     * first line; this is the second, for direct-to-Tomcat traffic and for whatever the rate limit admits.
     *
     * <p>Address windows live in a size-bounded cache, so a flood of distinct addresses cannot grow memory
     * without limit; an address evicted early merely starts a fresh window. Thread-safe.
     */
    static final class FailureAuditBudget {

        enum Decision {
            /** Write the ordinary failure row. */
            AUDIT,
            /** Write one notice that this address's rows are suppressed for the rest of the window. */
            SUPPRESS_ADDRESS_FROM_NOW,
            /** Write one notice that all rows are suppressed for the rest of the window. */
            SUPPRESS_ALL_FROM_NOW,
            /** Write nothing. */
            SUPPRESS
        }

        static final long WINDOW_MILLIS = 60_000L;
        static final int PER_ADDRESS_LIMIT = 10;
        static final int SERVER_WIDE_LIMIT = 300;
        private static final int MAX_TRACKED_ADDRESSES = 10_000;
        private static final String UNKNOWN_ADDRESS = "unknown";

        private final LongSupplier clock;
        private final Cache<String, Window> addressWindows = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_ADDRESSES)
                .expireAfterAccess(Duration.ofMillis(2 * WINDOW_MILLIS))
                .build();
        private final Window serverWindow = new Window();

        FailureAuditBudget(LongSupplier clock) {
            this.clock = clock;
        }

        Decision admit(String address) {
            long now = clock.getAsLong();
            String key = (address == null || address.isBlank()) ? UNKNOWN_ADDRESS : address;
            int addressCount = addressWindows.get(key, k -> new Window()).increment(now);
            if (addressCount > PER_ADDRESS_LIMIT + 1) {
                return Decision.SUPPRESS;
            }
            // Only rows that would be written count against the server-wide budget.
            int serverCount = serverWindow.increment(now);
            if (serverCount > SERVER_WIDE_LIMIT + 1) {
                return Decision.SUPPRESS;
            }
            if (serverCount == SERVER_WIDE_LIMIT + 1) {
                return Decision.SUPPRESS_ALL_FROM_NOW;
            }
            return addressCount == PER_ADDRESS_LIMIT + 1 ? Decision.SUPPRESS_ADDRESS_FROM_NOW : Decision.AUDIT;
        }

        /** A fixed window: the count resets once {@link #WINDOW_MILLIS} has passed since it opened. */
        private static final class Window {
            private long start = Long.MIN_VALUE;
            private int count;

            synchronized int increment(long now) {
                if (start == Long.MIN_VALUE || now - start >= WINDOW_MILLIS || now < start) {
                    start = now;
                    count = 0;
                }
                if (count < Integer.MAX_VALUE) {
                    count++;
                }
                return count;
            }
        }
    }

    /**
     * Enforces the granted OAuth 1.0a scopes for the current request (issue #3083).
     *
     * <p>Fast-exits when an operator has turned enforcement off ({@link OAuthScopeEnforcement}; it is on
     * by default since #4419) or when the endpoint is explicitly scope-exempt ({@link OAuthScopes#requiredScope}
     * returns {@link OAuthScopes#NO_SCOPE_REQUIRED}). An endpoint the scope map does not know requires
     * {@link OAuthScopes#UNMAPPED_ENDPOINT}, which no token satisfies. When a scope is required and the token's
     * granted scopes do not satisfy it, throws {@link OAuth1Exception} with HTTP 403 {@code insufficient_scope};
     * the caller's catch block records the rejection in the audit trail.
     */
    private void enforceScope(HttpServletRequest req, ServiceAccessToken accessToken) {
        if (!OAuthScopeEnforcement.isEnabled()) {
            return;
        }
        // Resolve the scope from getPathInfo(): the container-decoded, canonicalized path (dot-segments
        // collapsed, matrix params stripped) that JAX-RS/CXF actually routes on. Using the raw request URI
        // here would force us to re-implement that normalization and risk diverging from the real routing.
        String requiredScope = OAuthScopes.requiredScope(req.getMethod(), req.getPathInfo());
        if (requiredScope == null) {  // OAuthScopes.NO_SCOPE_REQUIRED: an explicitly exempt endpoint
            return;
        }
        if (!OAuthScopes.isSatisfiedBy(requiredScope, grantedScopes(accessToken))) {
            throw new OAuth1Exception(403, "insufficient_scope");
        }
    }

    /**
     * The scope strings granted on the access token, or an empty list when none are present. Persisted
     * scopes are a space-delimited string that may be null/blank (e.g. tokens minted before scopes carried
     * meaning); that is treated as no granted scopes so enforcement fails closed (403) rather than NPE-ing.
     */
    private static List<String> grantedScopes(ServiceAccessToken accessToken) {
        String raw = accessToken == null ? null : accessToken.getScopes();
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        // Before #4419's decoding fix, /initiate stored a multi-scope request still percent-encoded
        // ("demographic.read%20provider.read" as ONE scope). Decode here too so those tokens keep the
        // grants their provider approved. Safe: the result is still matched exactly against the vocabulary.
        raw = percentDecode(raw);
        List<String> scopes = new ArrayList<>();
        for (String scope : raw.trim().split("\\s+")) {
            if (!scope.isEmpty()) {
                scopes.add(scope);
            }
        }
        return scopes;
    }

    /** RFC 3986 percent-decoding of ASCII escapes; anything malformed is kept as-is. */
    private static String percentDecode(String s) {
        if (s.indexOf('%') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.append((char) ((hi << 4) + lo));
                    i += 2;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
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
