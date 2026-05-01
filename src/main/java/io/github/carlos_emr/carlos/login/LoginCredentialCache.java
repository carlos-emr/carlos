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
package io.github.carlos_emr.carlos.login;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/**
 * Short-lived, server-side transient store for login credential material used to bridge
 * multi-step authentication flows (MFA verification and forced password reset) without
 * persisting credentials in the HTTP session.
 *
 * <p>Storing credentials in the HTTP session is a security anti-pattern: sessions can be
 * serialized to disk, replicated across nodes, appear in heap/thread dumps, and be accessed
 * by any code that reads session attributes. This cache instead keeps credentials in a
 * server-side in-memory map with a short TTL, and returns an opaque random token that the
 * caller stores in the session. When the multi-step flow completes, the caller redeems the
 * token to retrieve (and atomically remove) the credentials.
 *
 * <p>Properties:
 * <ul>
 *   <li><b>Short TTL</b> — entries expire 5 minutes after write; stale credentials cannot
 *       outlive a typical MFA / password-change round-trip.</li>
 *   <li><b>One-time use</b> — {@link #consume(String)} atomically removes the entry so a
 *       leaked token cannot be reused.</li>
 *   <li><b>Unguessable tokens</b> — tokens are 256 bits of entropy from {@link SecureRandom},
 *       URL-safe Base64 encoded.</li>
 *   <li><b>Bounded</b> — capped at 10,000 entries to prevent memory-exhaustion via forged
 *       login attempts; entries may be evicted when the maximum size is exceeded according
 *       to Caffeine's eviction policy.</li>
 * </ul>
 *
 * <p>This class is thread-safe and intended to be used as a process-wide singleton via
 * {@link #getInstance()}.
 *
 * @since 2026-04-20
 */
public final class LoginCredentialCache {

    /** Maximum cache size; prevents memory exhaustion from forged login attempts. */
    private static final long MAX_SIZE = 10_000L;

    /**
     * Entry TTL. Five minutes is chosen as the smallest practical window that still
     * accommodates legitimate multi-step login flows:
     * <ul>
     *   <li>TOTP MFA entry — typically seconds, but users may need to fetch a code
     *       from a second device.</li>
     *   <li>Forced password reset — users must pick, type, and confirm a new password,
     *       sometimes navigating password-manager UI.</li>
     * </ul>
     * Shorter windows (e.g. 1–2 min) risk interrupting users mid-flow; longer windows
     * (e.g. 30 min) needlessly widen the exposure window if a cache entry were ever
     * observable (heap dump, debugger, JMX).
     */
    private static final Duration TTL = Duration.ofMinutes(5);

    /** Token byte length (256 bits of entropy). */
    private static final int TOKEN_BYTES = 32;

    private static final LoginCredentialCache INSTANCE = new LoginCredentialCache();

    private final Cache<String, LoginCredentials> cache;
    private final SecureRandom secureRandom = new SecureRandom();

    private LoginCredentialCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .build();
    }

    /**
     * @return the process-wide {@code LoginCredentialCache} singleton
     */
    public static LoginCredentialCache getInstance() {
        return INSTANCE;
    }

    /**
     * Stores credentials in the cache and returns a random opaque token that can be used
     * later to retrieve them via {@link #consume(String)}.
     *
     * @param credentials the credentials to stash; must not be {@code null}
     * @return a random, URL-safe token that uniquely identifies the cached entry
     * @throws NullPointerException if {@code credentials} is {@code null}
     */
    public String store(LoginCredentials credentials) {
        Objects.requireNonNull(credentials, "credentials must not be null");
        String token = generateToken();
        cache.put(token, credentials);
        return token;
    }

    /**
     * Atomically retrieves and removes the credentials associated with {@code token}.
     * Returns {@code null} if the token is unknown, has expired, or has already been
     * consumed.
     *
     * @param token the token previously returned by {@link #store(LoginCredentials)};
     *              may be {@code null}
     * @return the cached credentials, or {@code null} if not found
     */
    public LoginCredentials consume(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        // Atomically remove and return the cached value so that only one concurrent
        // caller can ever redeem a token. Caffeine's asMap().remove(key) is a single
        // atomic operation; getIfPresent + invalidate would allow two threads to both
        // observe a value before either evicted it.
        return cache.asMap().remove(token);
    }

    /**
     * Retrieves the credentials associated with {@code token} <em>without</em> removing
     * them from the cache. Intended for multi-step flows that may legitimately re-read
     * the credentials (for example, a forced-password-reset retry after an invalid
     * old-password submission). Callers must explicitly call {@link #invalidate(String)}
     * (or {@link #consume(String)}) when the flow terminates, successfully or otherwise.
     *
     * @param token the token previously returned by {@link #store(LoginCredentials)};
     *              may be {@code null}
     * @return the cached credentials, or {@code null} if not found
     */
    public LoginCredentials peek(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return cache.getIfPresent(token);
    }

    /**
     * Removes any credentials associated with {@code token}. Safe to call with a
     * {@code null}, empty, or already-consumed token.
     *
     * @param token the token to invalidate, may be {@code null}
     */
    public void invalidate(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        cache.invalidate(token);
    }

    /**
     * Package-private accessor for current cache size; intended for tests.
     *
     * @return the approximate number of entries currently in the cache
     */
    long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Immutable value object holding the transient credential material for a pending
     * multi-step login. Instances are stored exclusively in {@link LoginCredentialCache}
     * and never placed in the HTTP session.
     *
     * <p>The {@code encodedPassword} field holds the pre-hashed password (never plaintext).
     * {@code pin} and {@code nextPage} are validated before construction by the caller.
     */
    public static final class LoginCredentials {
        private final String userName;
        private final String encodedPassword;
        private final String pin;
        private final String nextPage;

        /**
         * @param userName         validated alphanumeric username
         * @param encodedPassword  password hash (never plaintext)
         * @param pin              validated 4-digit PIN (may be empty but not null)
         * @param nextPage         post-auth redirect target, validated by caller (may be null)
         */
        public LoginCredentials(String userName, String encodedPassword, String pin, String nextPage) {
            this.userName = userName;
            this.encodedPassword = encodedPassword;
            this.pin = pin;
            this.nextPage = nextPage;
        }

        public String getUserName() {
            return userName;
        }

        public String getEncodedPassword() {
            return encodedPassword;
        }

        public String getPin() {
            return pin;
        }

        public String getNextPage() {
            return nextPage;
        }
    }
}
