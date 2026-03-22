/**
 * File: UserSubject.java
 *
 * Purpose:
 *   Lightweight domain model representing the OAuth 1.0a resource owner
 *   (end-user) bound to a token or request. Encapsulates only stable,
 *   non-sensitive identifiers and role hints.
 *
 * Responsibilities:
 *   • Identify the authenticated user who approved or owns a token.
 *   • Provide a stable loginName (e.g., providerNo, staffId, patientId).
 *   • Optionally carry a list of roles/permissions for downstream checks.
 *
 * Context / Why Added:
 *   Linked to RequestToken during authorization and carried forward in
 *   AccessToken to avoid re-querying session state on every request.
 *
 * Notes:
 *   • Keep immutable where possible for thread safety.
 *   • equals/hashCode should be based on stable IDs (not display fields).
 *   • Do not include secrets, session identifiers, or volatile data.
 *   • Limit PII; log only non-sensitive identifiers if needed.
 *   • Safe for token persistence/serialization, but version carefully if
 *     the model evolves.
 */

package io.github.carlos_emr.carlos.webserv.oauth;

import java.util.List;

/**
 * Lightweight domain model representing the OAuth 1.0a resource owner (the authenticated user).
 *
 * @since 2026-01-18
 */
public class UserSubject {

    // Stable identifier for the user (e.g., providerNo or username)
    private String loginName;
    
    // Optional list of roles/permissions associated with this subject
    private List<String> roles;

    public UserSubject(String loginName, List<String> roles) {
        this.loginName = loginName;
        this.roles = roles;
    }

    public String getLoginName() {
        return loginName;
    }

    public List<String> getRoles() {
        return roles;
    }
}
