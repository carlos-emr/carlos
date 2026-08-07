package io.github.carlos_emr.carlos.webserv.oauth.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * File: OAuthRequestParser.java
 *
 * Purpose:
 *   Utility class for extracting OAuth 1.0a parameters from HttpServletRequest
 *   objects in a consistent way.
 *
 * Responsibilities:
 *   • Detect whether a request carries OAuth1 credentials.
 *   • Parse Authorization headers, query strings, and form parameters for
 *     oauth_* values.
 *   • Normalize/URL-decode values and return them in a simple map.
 *   • Provide shortcuts for common fields like consumer key and signature.
 *
 * Context / Why Added:
 *   Consolidates OAuth parameter parsing logic so that signature verification
 *   and interceptors don’t duplicate fragile string handling.
 *
 * Notes:
 *   • Prioritizes Authorization header values over query/form parameters.
 *   • Decoding is done with UTF-8; exceptions are swallowed to avoid
 *     blocking on malformed input.
 *   • Utility class only; cannot be instantiated.
 */


public final class OAuthRequestParser {
    private OAuthRequestParser() {
        // utility class - prevents instantiation
    }

    /** Returns true if this request appears to carry any OAuth1 info. */
    public static boolean isOAuth1Request(HttpServletRequest req) {
        return req.getHeader("Authorization") != null
            || req.getParameter("oauth_consumer_key") != null;
    }

    /** Shortcut to pull the consumer key out of the OAuth params. */
    public static String getConsumerKey(HttpServletRequest req) {
        return extractOAuthParameters(req).get("oauth_consumer_key");
    }

    /**
     * Extracts all OAuth1 parameters (those prefixed oauth_) from
     * the Authorization header (if present) and then from query/form
     * parameters as a fallback.
     */
    public static Map<String, String> extractOAuthParameters(HttpServletRequest req) {
        Map<String, String> params = new LinkedHashMap<>();

        // 1) Pull from every "Authorization" header
        Enumeration<String> authHeaders = req.getHeaders("Authorization");
        while (authHeaders.hasMoreElements()) {
            String header = authHeaders.nextElement();
            if (header != null && header.startsWith("OAuth ")) {
                // strip off "OAuth "
                String raw = header.substring("OAuth ".length());
                // split on commas not inside quotes
                for (String pair : raw.split("\\s*,\\s*")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim();
                        String val = kv[1].trim();
                        // remove surrounding quotes if present
                        if (val.startsWith("\"") && val.endsWith("\"")) {
                            val = val.substring(1, val.length() - 1);
                        }
                        // URL-decode
                        try {
                            val = URLDecoder.decode(val, StandardCharsets.UTF_8.name());
                        } catch (Exception ignored) { }
                        params.put(key, val);
                    }
                }
            }
        }

        // 2) Pull from query/form parameters as fallback (do not override header entries)
        req.getParameterMap().forEach((k, vals) -> {
            if (k.startsWith("oauth_") && vals.length > 0) {
                params.putIfAbsent(k, vals[0]);
            }
        });

        return params;
    }

    /**
     * Pulls the oauth_signature value out of an OAuth "Authorization" header.
     * Returns null if none found.
     */
    public static String extractSignatureFromHeader(String authzHeader) {
        if (authzHeader == null || !authzHeader.startsWith("OAuth ")) {
            return null;
        }
        // strip "OAuth "
        String[] pairs = authzHeader.substring("OAuth ".length()).split("\\s*,\\s*");
        for (String pair : pairs) {
            if (pair.startsWith("oauth_signature=")) {
                // key=value
                String v = pair.split("=", 2)[1]
                              .replaceAll("^\"|\"$", ""); // trim quotes
                try {
                    return URLDecoder.decode(v, StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    return v;
                }
            }
        }
        return null;
    }
}
