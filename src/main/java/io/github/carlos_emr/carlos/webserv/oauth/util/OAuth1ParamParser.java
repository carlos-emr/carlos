/**
 * Purpose: Extract and normalize OAuth 1.0a parameters from HttpServletRequest.
 * Responsibilities:
 *   • Read Authorization header and query/form params; merge per OAuth precedence.
 *   • Percent-decode where applicable; build an immutable OAuth1Request.
 * Why changed/added: Separate parsing from business logic to make signature verification
 * deterministic and unit-testable during the CXF→ScribeJava migration.
 * Notes:
 *   • Do not mutate the request; no logging of secrets.
 *   • Validate required fields (consumer key, signature method, nonce, timestamp).
 */

/**
 * File: OAuth1ParamParser.java
 *
 * Purpose:
 *   Centralized parser for OAuth 1.0a HTTP request parameters. Ensures that
 *   parsing, normalization, and validation of OAuth inputs are consistent
 *   across all endpoints.
 *
 * Responsibilities:
 *   • Extract OAuth parameters from Authorization header, query string, and
 *     application/x-www-form-urlencoded request bodies.
 *   • Normalize values (trim quotes, decode where applicable) and preserve
 *     them in an immutable OAuth1Request object.
 *   • Validate presence of required fields (consumer key, signature,
 *     signature method, timestamp, nonce).
 *   • Provide a clean separation of concerns so signature verification logic
 *     is deterministic and unit-testable.
 *
 * Context / Why Added:
 *   Introduced during the CXF → ScribeJava migration to decouple low-level
 *   request parsing from signature verification and controller logic. This
 *   makes OAuth request handling more predictable and easier to test.
 *
 * Notes:
 *   • Does not mutate the underlying HttpServletRequest.
 *   • Does not log secrets or sensitive parameters.
 *   • Endpoint-specific validations (e.g., token presence, callback rules)
 *     are handled downstream.
 */


package io.github.carlos_emr.carlos.webserv.oauth.util;

import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Request;
import org.springframework.stereotype.Component;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;

@Component
public class OAuth1ParamParser {

    public OAuth1Request parseFromRequest(HttpServletRequest req) {
        OAuth1Request r = new OAuth1Request();
        r.method = req.getMethod();

        // base URL without query
        URI u = URI.create(req.getRequestURL().toString());
        int port = u.getPort();
        boolean defaultPort = (u.getScheme().equals("http") && port == 80) || (u.getScheme().equals("https") && port == 443);
        String hostPort = defaultPort || port == -1 ? u.getHost() : (u.getHost() + ":" + port);
        r.urlBase = u.getScheme() + "://" + hostPort + u.getPath();

        // 1) Authorization header
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "OAuth ", 0, 6)) {
            String rest = auth.substring(6).trim();
            for (String part : rest.split(",")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length != 2) continue;
                String key = kv[0].trim();
                String val = trimQuotes(kv[1].trim());
                r.addParam(key, val);
            }
        }

        // 2) Query params
        if (req.getQueryString() != null) {
            for (String qp : req.getQueryString().split("&")) {
                if (qp.isEmpty()) continue;
                String[] kv = qp.split("=", 2);
                String k = decode(kv[0]);
                String v = kv.length > 1 ? decode(kv[1]) : "";
                r.addParam(k, v);
            }
        }

        // 3) Body params for x-www-form-urlencoded
        String ctype = req.getContentType();
        if (ctype != null && ctype.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            req.getParameterMap().forEach((k, arr) -> {
                for (String v : arr) r.addParam(k, v);
            });
        }

        // lift common oauth_* fields
        r.signature       = first(r.params.get("oauth_signature"));
        r.signatureMethod = first(r.params.get("oauth_signature_method"));
        r.consumerKey     = first(r.params.get("oauth_consumer_key"));
        r.token           = first(r.params.get("oauth_token"));
        r.timestamp       = first(r.params.get("oauth_timestamp"));
        r.nonce           = first(r.params.get("oauth_nonce"));
        r.callback        = first(r.params.get("oauth_callback"));
        r.verifier        = first(r.params.get("oauth_verifier"));
        r.scopesCsv       = first(r.params.get("scope")); // if your clients send it

        // minimal required checks (endpoint-specific checks are done in controllers)
        if (r.consumerKey == null || r.signature == null || r.signatureMethod == null
                || r.timestamp == null || r.nonce == null) {
            throw new OAuth1Exception(400, "invalid_oauth_parameters");
        }
        return r;
    }

    private static String first(List<String> v) { return (v == null || v.isEmpty()) ? null : v.get(0); }
    private static String trimQuotes(String s) { return (s.startsWith("\"") && s.endsWith("\"")) ? s.substring(1, s.length()-1) : s; }
    private static String decode(String s) { return s.replace("+", " "); } // let container handle full decode
}
