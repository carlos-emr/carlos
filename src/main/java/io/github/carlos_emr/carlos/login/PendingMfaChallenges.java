/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import jakarta.servlet.http.HttpSession;

/**
 * Session-facing contract for pending MFA challenge state.
 *
 * <p>The HTTP session is allowed to hold only marker values and an opaque cache token. Sensitive
 * challenge payloads are kept in {@link PendingMfaChallengeCache}. Keeping cleanup here gives
 * logout and session-listener paths the same invalidation behavior as {@link Login2Action}.</p>
 */
public final class PendingMfaChallenges {
    public static final String AUTH_ATTR = "pendingMfaAuthentication";
    public static final String PROVIDER_NO_ATTR = "pendingMfaProviderNo";
    public static final String TOKEN_ATTR = "pendingMfaChallengeToken";
    public static final String ATTEMPTS_ATTR = "pendingMfaFailedAttempts";
    public static final String LEGACY_REGISTRATION_SECRET_ATTR = "mfaSecret";

    private PendingMfaChallenges() {
        // Utility holder for the pending-MFA session contract.
    }

    /**
     * Stages the complete pending-MFA session marker set after the sensitive payload has been
     * stored in {@link PendingMfaChallengeCache}. Callers should use this instead of setting the
     * attributes individually so partial marker state does not survive refactors.
     */
    public static void stage(HttpSession session, String providerNo, String token, int attempts) {
        session.setAttribute(AUTH_ATTR, Boolean.TRUE); // nosemgrep: tainted-session-from-http-request -- server-generated MFA challenge marker
        session.setAttribute(PROVIDER_NO_ATTR, providerNo); // nosemgrep: tainted-session-from-http-request -- authenticated LoginCheckLogin provider number for audit context
        session.setAttribute(TOKEN_ATTR, token); // nosemgrep: tainted-session-from-http-request -- opaque server-generated cache token, not raw MFA state
        session.setAttribute(ATTEMPTS_ATTR, attempts); // nosemgrep: tainted-session-from-http-request -- server-controlled MFA retry counter
    }

    /**
     * Returns the opaque pending-MFA token only when the session attribute has the expected type.
     */
    public static String getToken(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object tokenAttr = session.getAttribute(TOKEN_ATTR);
        return tokenAttr instanceof String ? (String) tokenAttr : null;
    }

    /**
     * Invalidates any cached pending-MFA payload referenced by the session and removes all related
     * session attributes. Safe to call with null, already-expired, or partially staged sessions.
     *
     * @param session session to clean; may be null
     */
    public static void clearFromSession(HttpSession session) {
        if (session == null) {
            return;
        }
        Object tokenAttr = session.getAttribute(TOKEN_ATTR);
        if (tokenAttr instanceof String) {
            PendingMfaChallengeCache.getInstance().invalidate((String) tokenAttr);
        }
        session.removeAttribute(AUTH_ATTR);
        session.removeAttribute(PROVIDER_NO_ATTR);
        session.removeAttribute(TOKEN_ATTR);
        session.removeAttribute(ATTEMPTS_ATTR);
        session.removeAttribute(LEGACY_REGISTRATION_SECRET_ATTR);
    }
}
