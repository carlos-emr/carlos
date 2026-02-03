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
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.phase.PhaseInterceptor;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.carlos.login.OscarOAuthDataProvider;
import io.github.carlos_emr.carlos.login.AppOAuth1Config;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;

@Component
public class OAuthInterceptor implements PhaseInterceptor<Message> {

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

        // 1) Skip non-OAuth1 requests
        if (!OAuthRequestParser.isOAuth1Request(req)) {
            return;
        }

        try {
            // 2) Pull oauth params
            Map<String, String> oauth = OAuthRequestParser.extractOAuthParameters(req);
            String consumerKey = oauth.get("oauth_consumer_key");
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

        } catch (OAuth1Exception e) {
            throw new Fault(e);
        } catch (IllegalArgumentException badSigOrTime) {
            // from verifier: missing/stale timestamp, bad signature, unknown token, etc.
            throw new Fault(new OAuth1Exception(401, "invalid_signature"));
        } catch (Exception e) {
            throw new Fault(new OAuth1Exception(401, "oauth_authentication_failed"));
        }
    }

    @Override public void handleFault(Message message) { /* no-op */ }
    @Override public Set<String> getBefore() { return Collections.emptySet(); }
    @Override public Set<String> getAfter()  { return Collections.emptySet(); }
    @Override public Collection<PhaseInterceptor<? extends Message>> getAdditionalInterceptors() { return null; }
    @Override public String getId() { return getClass().getSimpleName(); }
}
