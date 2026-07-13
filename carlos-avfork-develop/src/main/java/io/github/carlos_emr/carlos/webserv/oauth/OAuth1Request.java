/**
 * File: OAuth1Request.java
 *
 * Purpose:
 *   Immutable representation of a normalized OAuth 1.0a request. Encapsulates
 *   the HTTP method, base URL, OAuth parameters, and extracted core fields
 *   needed for signature verification and token handling.
 *
 * Responsibilities:
 *   • Hold normalized OAuth parameters (excluding raw HttpServletRequest state).
 *   • Expose commonly used OAuth fields such as consumerKey, token, signature,
 *     nonce, timestamp, callback, and verifier.
 *   • Provide helper for accumulating parameters during parsing.
 *
 * Context / Why Added:
 *   Produced by OAuth1ParamParser to decouple request parsing from business
 *   logic. Used consistently by signature verifiers and token endpoints to
 *   ensure deterministic behavior.
 *
 * Notes:
 *   • Should be treated as immutable after parsing (no mutations in verifiers).
 *   • Never log secrets or raw signatures when debugging.
 *   • Supports non-standard `scope` parameter if present in client requests.
 */

package io.github.carlos_emr.carlos.webserv.oauth;

import java.util.*;

public class OAuth1Request {
    public String method;
    public String urlBase; // scheme://host[:port]/path (no query)
    public SortedMap<String, List<String>> params = new TreeMap<>();
    public String signature;        // oauth_signature
    public String signatureMethod;  // HMAC-SHA1
    public String consumerKey;      // oauth_consumer_key
    public String token;            // oauth_token (may be null for initiate)
    public String timestamp;        // oauth_timestamp
    public String nonce;            // oauth_nonce
    public String callback;         // oauth_callback (initiate)
    public String verifier;         // oauth_verifier (token)
    public String scopesCsv;        // non-standard, if your clients pass scope

    public void addParam(String k, String v) {
        params.computeIfAbsent(k, _k -> new ArrayList<>()).add(v);
    }
}
