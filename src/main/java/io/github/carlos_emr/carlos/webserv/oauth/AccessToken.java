/**
 * Purpose: Domain model for an OAuth 1.0a access token.
 * Contains: token key/secret, client reference, user subject, timestamps, flags.
 * Used by: AccessToken issuance and request signing/verification.
 * Notes:
 *   • Never log token secrets.
 *   • Equals/hashCode should NOT include the secret.
 */

/**
 * File: AccessToken.java
 *
 * Purpose:
 *   Domain model representing an OAuth 1.0a access token issued to a client.
 *   Encapsulates the client reference, token credentials, validity, and user
 *   association.
 *
 * Responsibilities:
 *   • Hold the token key/secret pair used in OAuth request signing.
 *   • Store metadata such as client reference, lifetime, and issued-at timestamp.
 *   • Maintain the associated user subject (resource owner) and granted scopes.
 *   • Provide standard getters/setters for use in token services and verifiers.
 *
 * Context / Why Added:
 *   Core part of OAuth 1.0a token handling, consumed by signature verification,
 *   access control checks, and token persistence layers.
 *
 * Notes:
 *   • Token secrets must never be logged or included in equals/hashCode.
 *   • Lifetime and issuedAt values are used to enforce token expiry.
 */

package io.github.carlos_emr.carlos.webserv.oauth;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain model representing an OAuth 1.0a access token issued to a client.
 *
 * <p>Encapsulates the token key/secret pair, client reference, user subject,
 * lifetime, and granted scopes. Used by token services and signature verifiers.
 *
 * @since 2026-01-18
 */
public class AccessToken {

    private Client client;
    private String tokenKey;
    private String tokenSecret;
    private long lifetime;
    private long issuedAt;
    private UserSubject subject;
    private List<OAuth1Permission> scopes = new ArrayList<>();

    public AccessToken(Client client, String tokenKey, String tokenSecret, long lifetime, long issuedAt) {
        this.client = client;
        this.tokenKey = tokenKey;
        this.tokenSecret = tokenSecret;
        this.lifetime = lifetime;
        this.issuedAt = issuedAt;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getTokenKey() {
        return tokenKey;
    }

    public String getTokenSecret() {
        return tokenSecret;
    }

    public long getLifetime() {
        return lifetime;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public UserSubject getSubject() {
        return subject;
    }

    public void setSubject(UserSubject subject) {
        this.subject = subject;
    }

    public List<OAuth1Permission> getScopes() {
        return scopes;
    }

    public void setScopes(List<OAuth1Permission> scopes) {
        this.scopes = scopes;
    }
}
