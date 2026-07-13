/**
 * File: OAuth1Permission.java
 *
 * Purpose:
 *   Domain model representing an OAuth 1.0a scope/permission granted to
 *   a client application. Encapsulates the identifier and optional
 *   descriptive text.
 *
 * Responsibilities:
 *   • Store the canonical scope identifier (e.g., "read", "write").
 *   • Optionally provide a description or constraints associated
 *     with the scope.
 *   • Used by request/authorization flows to track what a client
 *     is permitted to access.
 *
 * Context / Why Added:
 *   Supports fine-grained access control in OAuth 1.0a by attaching
 *   specific scopes to RequestTokens and AccessTokens.
 *
 * Notes:
 *   • Keep scope names canonical/consistent to avoid mismatches.
 *   • Validate or reject unknown scopes at request time to prevent
 *     accidental over-permissioning.
 */

package io.github.carlos_emr.carlos.webserv.oauth;

public class OAuth1Permission {
    private String permission;
    private String description;

    public OAuth1Permission(String permission, String description) {
        this.permission = permission;
        this.description = description;
    }

    public String getPermission() {
        return permission;
    }

    public String getDescription() {
        return description;
    }
}
