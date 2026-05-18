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

    private PendingMfaChallenges() {
        // Utility holder for the pending-MFA session contract.
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
        session.removeAttribute("mfaSecret");
    }
}
