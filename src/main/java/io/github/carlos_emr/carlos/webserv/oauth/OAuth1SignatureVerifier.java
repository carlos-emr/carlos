/**
 * File: OAuth1SignatureVerifier.java
 *
 * Purpose:
 *   Abstraction for server-side verification of OAuth 1.0a request signatures.
 *   Provides a high-level contract for authenticating signed requests and
 *   resolving the associated provider.
 *
 * Responsibilities:
 *   • Verify an incoming request’s signature against the consumer key/secret
 *     and (if present) token secret.
 *   • Enforce anti-replay protections via timestamp/nonce validation.
 *   • Throw OAuth1Exception with HTTP-like codes on invalid or expired requests.
 *   • Provide a lookup from access token → providerNo for downstream use.
 *
 * Context / Why Added:
 *   Defines the SPI for OAuth verification, allowing multiple strategies
 *   (e.g., HMAC-SHA1, PLAINTEXT) to be implemented and injected as needed.
 *   Part of the CXF → ScribeJava migration to make verification explicit
 *   and testable.
 *
 * Notes:
 *   • Implementations must use constant-time comparison to prevent timing leaks.
 *   • Crypto details are delegated to HmacSha1SignatureVerifier (or similar).
 *   • ProviderNo resolution is implementation-specific via token storage.
 */

package io.github.carlos_emr.carlos.webserv.oauth;

import jakarta.servlet.http.HttpServletRequest;
import io.github.carlos_emr.carlos.login.AppOAuth1Config;

public interface OAuth1SignatureVerifier {
    String verifySignature(HttpServletRequest req, AppOAuth1Config cfg);
    String getProviderNo(String token);
}
