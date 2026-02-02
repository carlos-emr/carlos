/**
 * File: RequestTokenRegistration.java
 *
 * Purpose:
 *   Domain model representing the client’s registration request when
 *   initiating an OAuth 1.0a request token. Encapsulates the client,
 *   requested callback URI, and requested scopes.
 *
 * Responsibilities:
 *   • Capture the client application initiating the request.
 *   • Hold the callback URI for redirecting after authorization.
 *   • Store the list of requested scopes/permissions.
 *   • Provide simple getters/setters for use in token issuance.
 *
 * Context / Why Added:
 *   Used in the OAuth 1.0a "request token" step to carry the client’s
 *   requested parameters into the token service. Keeps the initiation
 *   flow clean and testable.
 *
 * Notes:
 *   • Callback URI may be overridden or validated by server policy.
 *   • Scopes should be canonicalized/validated before persisting.
 *   • Does not store or generate token keys/secrets — that happens
 *     when issuing a RequestToken.
 */


package io.github.carlos_emr.carlos.webserv.oauth;

public class RequestTokenRegistration {
    private Client client;
    private String callback;
    private String[] scopes;

    public RequestTokenRegistration(Client client) {
        this.client = client;
    }

    public Client getClient() {
        return client;
    }

    public String getCallback() {
        return callback;
    }

    public void setCallback(String callback) {
        this.callback = callback;
    }

    public String[] getScopes() {
        return scopes;
    }

    public void setScopes(String[] scopes) {
        this.scopes = scopes;
    }
}
