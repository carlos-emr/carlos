/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv.oauth;

import jakarta.servlet.http.HttpSession;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the short-lived OAuth authorization nonce session state used by the consent page.
 *
 * <p>Login rotates the servlet session after credentials are accepted. The OAuth consent page is
 * rendered before that rotation for unauthenticated users, so this helper provides a narrow copy
 * operation for only the authorization nonce attributes. It must not become a general-purpose
 * session-preservation mechanism.</p>
 *
 * @since 2026-05-18
 */
public final class OAuthAuthorizationSessionState {

    private static final String AUTHORIZATION_NONCE_PREFIX = "oauth.authorize.nonce.";

    private OAuthAuthorizationSessionState() {
    }

    /**
     * Stages a one-time nonce for the request token and returns the nonce value for the consent form.
     */
    public static String stageNonce(HttpSession session, String tokenId) {
        String nonce = UUID.randomUUID().toString();
        session.setAttribute(nonceAttribute(tokenId), nonce);
        return nonce;
    }

    /**
     * Consumes the staged nonce exactly once, returning {@code true} only for a matching value.
     */
    public static boolean consumeNonce(HttpSession session, String tokenId, String submittedNonce) {
        if (session == null || submittedNonce == null || submittedNonce.isBlank()) {
            return false;
        }
        String attribute = nonceAttribute(tokenId);
        Object expected = session.getAttribute(attribute);
        session.removeAttribute(attribute);
        return submittedNonce.equals(expected);
    }

    /**
     * Captures staged authorization nonces before the login session is invalidated.
     */
    public static Map<String, String> snapshotNonces(HttpSession session) {
        if (session == null) {
            return Collections.emptyMap();
        }
        Map<String, String> nonces = new LinkedHashMap<>();
        Enumeration<String> names = session.getAttributeNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            Object value = session.getAttribute(name);
            if (name.startsWith(AUTHORIZATION_NONCE_PREFIX) && value instanceof String nonce) {
                nonces.put(name, nonce);
            }
        }
        return nonces;
    }

    /**
     * Restores authorization nonces onto the freshly rotated login session.
     */
    public static void restoreNonces(HttpSession session, Map<String, String> nonces) {
        if (session == null || nonces == null || nonces.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : nonces.entrySet()) {
            session.setAttribute(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Builds the session attribute name for one request token's consent nonce.
     */
    static String nonceAttribute(String tokenId) {
        return AUTHORIZATION_NONCE_PREFIX + tokenId;
    }
}
