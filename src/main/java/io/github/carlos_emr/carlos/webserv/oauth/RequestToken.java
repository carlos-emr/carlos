/**
 * File: RequestToken.java
 *
 * Purpose:
 *   Domain model representing an OAuth 1.0a request token. Captures the
 *   temporary credentials issued to a client before authorization and
 *   exchange for an access token.
 *
 * Responsibilities:
 *   • Hold the client reference, token key/secret, and optional callback URI.
 *   • Store requested scopes and associate them with the token.
 *   • Track the verifier value once the resource owner approves the request.
 *   • Serve as the bridge between the initiation, authorization, and token
 *     exchange steps.
 *
 * Context / Why Added:
 *   Used in Step 1 (request token creation), Step 2 (authorize with verifier),
 *   and Step 3 (exchange for access token) of the OAuth 1.0a flow. Provides
 *   a persistent model for tracking lifecycle state of request tokens.
 *
 * Notes:
 *   • One-time use: request tokens must be marked consumed after a successful
 *     exchange.
 *   • Never log token secrets.
 *   • Scopes determine which permissions will be carried over into the access
 *     token after authorization.
 */


package io.github.carlos_emr.carlos.webserv.oauth;

import java.util.ArrayList;
import java.util.List;

public class RequestToken {

    private Client client;
    private String tokenKey;
    private String tokenSecret;
    private List<OAuth1Permission> scopes = new ArrayList<>();
    private String callback;
    private String verifier;

    public RequestToken(Client client, String tokenKey, String tokenSecret) {
        this.client = client;
        this.tokenKey = tokenKey;
        this.tokenSecret = tokenSecret;
    }

    public Client getClient() {
        return client;
    }

    public String getTokenKey() {
        return tokenKey;
    }

    public String getTokenSecret() {
        return tokenSecret;
    }

    public List<OAuth1Permission> getScopes() {
        return scopes;
    }

    public void setScopes(List<OAuth1Permission> scopes) {
        this.scopes = scopes;
    }

    public String getCallback() {
        return callback;
    }

    public void setCallback(String callback) {
        this.callback = callback;
    }

    public String getVerifier() {
        return verifier;
    }

    public void setVerifier(String verifier) {
        this.verifier = verifier;
    }
}
