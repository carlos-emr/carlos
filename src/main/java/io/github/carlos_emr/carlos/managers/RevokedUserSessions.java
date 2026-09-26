/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Remembers, for a short time, which sessions were signed out because the same user signed in
 * elsewhere (issue #3980).
 *
 * <p>A signed-out browser still sends its old session cookie on its next request. The
 * unauthenticated-request path looks the requested id up here so it can send that browser to the
 * login page with "you were signed out because you signed in elsewhere" rather than a bare
 * sign-in form. The lookup consumes the entry, so the message is shown once.</p>
 *
 * <p>Only a SHA-256 digest of the session id is stored: the registry must not become a second
 * place a bearer token can be read from (heap dumps, diagnostics). Entries expire after
 * three hours, which is longer than the authenticated session lifetime set at login (two hours),
 * so a browser that returns after its session would have expired anyway is not misinformed for
 * long. The store is per JVM, like the session registry itself.</p>
 *
 * @since 2026-09-26
 */
public final class RevokedUserSessions {

    /**
     * Request attribute the login page reads to show the "signed out because you signed in
     * elsewhere" notice. Set by {@code RootEntryRedirectFilter}, never from a request parameter, so
     * the notice cannot be forged with a crafted link.
     */
    public static final String NOTICE_REQUEST_ATTR = "signedOutElsewhere";

    private static final Duration TTL = Duration.ofHours(3);
    private static final long MAX_SIZE = 10_000L;

    private static final Cache<String, Boolean> REVOKED = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_SIZE)
            .build();

    private RevokedUserSessions() {
        // Static registry.
    }

    /**
     * Records that the session with this id was signed out by a newer sign-in.
     *
     * @param sessionId servlet session id; ignored when {@code null} or blank
     */
    public static void mark(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        REVOKED.put(digest(sessionId), Boolean.TRUE);
    }

    /**
     * Reports whether the session with this id was signed out by a newer sign-in, without
     * forgetting it. Used to route the browser to the login page, which then consumes the entry.
     *
     * @param requestedSessionId the session id the browser sent; may be {@code null}
     * @return {@code true} while the entry recorded by {@link #mark(String)} is live
     */
    public static boolean isRevoked(String requestedSessionId) {
        if (requestedSessionId == null || requestedSessionId.isBlank()) {
            return false;
        }
        return REVOKED.getIfPresent(digest(requestedSessionId)) != null;
    }

    /**
     * Reports whether the session with this id was signed out by a newer sign-in, and forgets it.
     *
     * @param requestedSessionId the session id the browser sent; may be {@code null}
     * @return {@code true} once for a session recorded by {@link #mark(String)}
     */
    public static boolean consume(String requestedSessionId) {
        if (requestedSessionId == null || requestedSessionId.isBlank()) {
            return false;
        }
        return REVOKED.asMap().remove(digest(requestedSessionId)) != null;
    }

    /** Test hook: forgets every recorded session. */
    static void clearForTesting() {
        REVOKED.invalidateAll();
    }

    private static String digest(String sessionId) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(sessionId.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every Java SE platform must provide SHA-256.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
