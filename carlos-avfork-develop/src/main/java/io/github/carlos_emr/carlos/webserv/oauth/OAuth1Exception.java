/**
 * File: OAuth1Exception.java
 *
 * Purpose:
 *   Custom unchecked exception type for signaling OAuth 1.0a failures.
 *   Couples an HTTP response code with a short error message that can be
 *   returned to clients.
 *
 * Responsibilities:
 *   • Wrap OAuth-related validation and processing errors (e.g. invalid
 *     signature, missing parameters, expired tokens).
 *   • Carry an HTTP status code so callers can map failures directly
 *     to appropriate responses (e.g. 400, 401).
 *   • Provide a message string suitable for client error bodies.
 *
 * Context / Why Added:
 *   Replaces generic exceptions to enforce consistent error handling
 *   across the OAuth request parsing, verification, and token services.
 *
 * Notes:
 *   • Use 401 for authentication/authorization failures.
 *   • Use 400 for malformed requests or unsupported parameters.
 *   • Avoid including consumer secrets, token secrets, or other sensitive
 *     data in exception messages.
 */

package io.github.carlos_emr.carlos.webserv.oauth;

public class OAuth1Exception extends RuntimeException {
    private final int httpCode;
    public OAuth1Exception(int httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
    }
    public int getHttpCode() { return httpCode; }
}
