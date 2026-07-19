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
 * 
 * Migrated from Apache CXF to ScribeJava OAuth1 implementation.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

/**
 * File: OscarRequestTokenService.java
 *
 * Purpose:
 *   OAuth 1.0a Request Token endpoint (CXF JAX-RS) served at /ws/oauth/initiate.
 *   Issues temporary request tokens after validating the client and signature.
 *
 * Responsibilities:
 *   • Parse OAuth params from Authorization/query/form via OAuth1ParamParser.
 *   • Verify signature (HMAC-SHA1/PLAINTEXT) with OAuth1SignatureVerifier.
 *   • Create and persist a RequestToken; return form-encoded response per RFC 5849.
 *
 * Context / Why Changed:
 *   Migrated from CXF OAuth/SOAP wiring to explicit JAX-RS + ScribeJava-style
 *   verification to match /ws/oauth/* without refactoring existing REST.
 *
 * Dependencies:
 *   • OscarOAuthDataProvider (client & token storage)
 *   • OAuth1ParamParser (normalized params)
 *   • OAuth1SignatureVerifier (crypto & checks)
 *
 * Notes:
 *   • Keep host/port/scheme consistent across the flow to avoid base-string mismatches.
 *   • Response body fields: oauth_token, oauth_token_secret, oauth_callback_confirmed=true.
 *   • Do not log secrets; log high-level failures only.
 */

package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Request;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1SignatureVerifier;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
import io.github.carlos_emr.carlos.webserv.oauth.RequestTokenRegistration;
import io.github.carlos_emr.carlos.webserv.oauth.RequestToken;
import io.github.carlos_emr.carlos.webserv.oauth.util.OAuth1ParamParser;

// JAX-RS + Servlet
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class OscarRequestTokenService {

    private final OscarOAuthDataProvider dataProvider;
    private final OAuth1ParamParser parser;

    @Resource
    private OAuth1SignatureVerifier verifier;

    public OscarRequestTokenService(OscarOAuthDataProvider dataProvider,
                                    OAuth1ParamParser parser) {
        this.dataProvider = dataProvider;
        this.parser = parser;
    }

    // Support POST
    @POST
    @Path("/initiate")
    @Produces(MediaType.APPLICATION_FORM_URLENCODED)
    public Response initiatePost(@Context HttpServletRequest req) {
        return doInitiate(req);
    }

    private Response doInitiate(HttpServletRequest req) {
        OAuth1Request oreq = parser.parseFromRequest(req);

        Client client = dataProvider.getClient(oreq.consumerKey);
        if (client == null) {
            throw new OAuth1Exception(401, "invalid_consumer");
        }

        AppOAuth1Config cfg = new AppOAuth1Config();
        cfg.setConsumerKey(client.getConsumerKey());
        cfg.setConsumerSecret(client.getSecret());
        cfg.setApplicationURI(req.getRequestURL().toString());
        verifier.verifySignature(req, cfg);

        RequestTokenRegistration reg = new RequestTokenRegistration(client);

        // --- decode+normalize callback ONLY for persistence ---
        String cbRaw = oreq.callback; // keep whatever the parser gives (was working before)
        String cbToStore;

        if ("oob".equals(cbRaw)) {
            cbToStore = "oob";
        } else if (cbRaw != null && !cbRaw.isEmpty()) {
            // decode once (RFC3986) and normalize to plain canonical URL
            String decoded = pctDecode(cbRaw);
            cbToStore = normalizeUrl(decoded);
        } else {
            // fallback to app-registered callback if your flow allows it
            cbToStore = normalizeUrl(client.getCallbackUri());
        }

        reg.setCallback(cbToStore);   // <-- store plain URL now (not encoded)

        if (oreq.scopesCsv != null && !oreq.scopesCsv.isBlank()) {
            reg.setScopes(oreq.scopesCsv.split("\\s+"));
        }
        RequestToken rt = dataProvider.createRequestToken(reg);

        String body = "oauth_token=" + enc(rt.getTokenKey())
                    + "&oauth_token_secret=" + enc(rt.getTokenSecret())
                    + "&oauth_callback_confirmed=true";

        return Response.ok(body).type(MediaType.APPLICATION_FORM_URLENCODED).build();
    }
    
    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String pctDecode(String s) {
        if (s == null || s.isEmpty()) return s;
        int n = s.length();
        StringBuilder out = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < n) {
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

    private static String normalizeUrl(String url) {
        try {
            var u = java.net.URI.create(url).normalize();
            String scheme = u.getScheme() == null ? null : u.getScheme().toLowerCase();
            String host   = u.getHost()   == null ? null : u.getHost().toLowerCase();
            int port = u.getPort();
            if ((port == 80 && "http".equalsIgnoreCase(scheme)) ||
                (port == 443 && "https".equalsIgnoreCase(scheme))) {
                port = -1; // drop default ports
            }
            String path = (u.getPath() == null || u.getPath().isEmpty()) ? "/" : u.getPath();
            return new java.net.URI(scheme, u.getUserInfo(), host, port, path, u.getQuery(), u.getFragment()).toString();
        } catch (Exception ignore) {
            return url; // fail-safe
        }
    }

}
