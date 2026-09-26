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
import com.github.benmanes.caffeine.cache.Ticker;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, server-side store for a login that has passed every credential check (password,
 * PIN, MFA, forced reset) and is waiting for the user to answer the concurrent-session chooser
 * (issue #3980).
 *
 * <p>At that point the user is authenticated but has no authenticated session yet. The HTTP
 * session must not carry the authentication result in the meantime: a session can be serialized,
 * replicated or dumped, and anything in it can be replayed by whoever holds its cookie. The parallel
 * fork (open-osp/Open-O PR #136) put the whole {@code LoginCheckLogin} object in the session here;
 * CARLOS keeps the payload in this process-local cache and gives the session only an opaque random
 * token, following {@link PendingMfaChallengeCache}.</p>
 *
 * <p>Entries expire five minutes after they are written, matching the lifetime of the pre-login
 * session that holds the token. The submit path {@linkplain #consume(String) consumes} the entry
 * atomically, so a token can complete at most one login.</p>
 */
final class PendingSessionChoiceCache {
    private static final long MAX_SIZE = 10_000L;
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int TOKEN_BYTES = 32;

    private static final PendingSessionChoiceCache INSTANCE = new PendingSessionChoiceCache();

    /**
     * One entry per token. A consumed token keeps its entry, with no payload, so its owner stays
     * known for as long as the completing submit may still be running; see {@link #ownerOf}.
     */
    private record Entry(PendingSessionChoice choice, Integer owner) {
    }

    private final Cache<String, Entry> cache;
    /**
     * Owners of consumed tokens whose submit is still completing. Not bounded and not expiring, so
     * a burst of new pending logins cannot evict an in-flight owner; the submit removes its own
     * entry with {@link #release} when it finishes.
     */
    private final Map<String, Integer> inFlight = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    private PendingSessionChoiceCache() {
        this(Ticker.systemTicker());
    }

    PendingSessionChoiceCache(Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .ticker(Objects.requireNonNull(ticker, "ticker must not be null"))
                .build();
    }

    static PendingSessionChoiceCache getInstance() {
        return INSTANCE;
    }

    String store(PendingSessionChoice choice) {
        Objects.requireNonNull(choice, "choice must not be null");
        String token = generateToken();
        cache.put(token, new Entry(choice, choice.securityNo()));
        return token;
    }

    PendingSessionChoice peek(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Entry entry = cache.getIfPresent(token);
        return entry == null ? null : entry.choice();
    }

    /**
     * Security number the token was issued for, before and after {@link #consume}.
     *
     * <p>The owner lives in the same entry as the payload. Consuming a token rewrites the entry,
     * which also renews its lifetime, so the owner outlasts the submit that consumed it. A cancel
     * racing that submit uses it to take the same admission lock
     * ({@link PendingSessionChoices#clearFromSession}).</p>
     *
     * @return the owner, or {@code null} for an unknown, invalidated or expired token
     */
    Integer ownerOf(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Integer completing = inFlight.get(token);
        if (completing != null) {
            return completing;
        }
        Entry entry = cache.getIfPresent(token);
        return entry == null ? null : entry.owner();
    }

    /**
     * Ends the in-flight period of a consumed token. The submit calls it once its login has
     * finished or failed, while it still holds the admission lock.
     */
    void release(String token) {
        if (token != null) {
            inFlight.remove(token);
        }
    }

    PendingSessionChoice consume(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        // Atomic take-and-mark: two concurrent submits of the same token cannot both complete, and
        // the consumed entry keeps the owner.
        PendingSessionChoice[] taken = new PendingSessionChoice[1];
        cache.asMap().computeIfPresent(token, (key, entry) -> {
            if (entry.choice() == null) {
                return entry;
            }
            taken[0] = entry.choice();
            inFlight.put(key, entry.owner());
            return new Entry(null, entry.owner());
        });
        return taken[0];
    }

    void invalidate(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        cache.invalidate(token);
        inFlight.remove(token);
    }

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
     * Immutable pending-login payload kept out of the HTTP session.
     *
     * <p>It carries exactly what {@code Login2Action.completeAuthenticatedLogin} needs to finish the
     * login on the chooser's submit request, which does not repeat the original login parameters.
     * {@code authResult} is the {@link LoginCheckLogin#auth} success contract and is copied on the
     * way in and out.</p>
     *
     * @param securityNo security row of the authenticated user
     * @param providerNo provider number of the authenticated user
     * @param authResult {@link LoginCheckLogin#auth} success result
     * @param mobileOptimized whether the original login selected the mobile layout
     * @param submitType the original login's {@code submit} parameter; may be {@code null}
     * @param oauthToken the original login's validated {@code oauth_token}; may be {@code null}
     * @param mfaVerified whether this sign-in completed an MFA challenge before the chooser, so the
     *                    submit can tell an MFA requirement added meanwhile from one already met
     * @param credentialFingerprint digest of the security row's credential fields as they were
     *                              when the credentials were checked; the submit refuses the login
     *                              if they changed. {@code null} never matches.
     */
    record PendingSessionChoice(Integer securityNo, String providerNo, String[] authResult,
                                boolean mobileOptimized, String submitType, String oauthToken,
                                boolean mfaVerified, String credentialFingerprint) {

        /** A pending login with no MFA and no credential binding (it cannot complete). */
        PendingSessionChoice(Integer securityNo, String providerNo, String[] authResult,
                             boolean mobileOptimized, String submitType, String oauthToken) {
            this(securityNo, providerNo, authResult, mobileOptimized, submitType, oauthToken, false, null);
        }

        PendingSessionChoice {
            Objects.requireNonNull(securityNo, "securityNo must not be null");
            Objects.requireNonNull(providerNo, "providerNo must not be null");
            Objects.requireNonNull(authResult, "authResult must not be null");
            if (authResult.length == 0) {
                throw new IllegalArgumentException("authResult must not be empty");
            }
            authResult = Arrays.copyOf(authResult, authResult.length);
        }

        @Override
        public String[] authResult() {
            return Arrays.copyOf(authResult, authResult.length);
        }

        // The record's generated equals/hashCode compare the array by reference; compare contents.
        @Override
        public boolean equals(Object other) {
            return other instanceof PendingSessionChoice that
                    && mobileOptimized == that.mobileOptimized
                    && mfaVerified == that.mfaVerified
                    && Objects.equals(credentialFingerprint, that.credentialFingerprint)
                    && securityNo.equals(that.securityNo)
                    && providerNo.equals(that.providerNo)
                    && Arrays.equals(authResult, that.authResult)
                    && Objects.equals(submitType, that.submitType)
                    && Objects.equals(oauthToken, that.oauthToken);
        }

        @Override
        public int hashCode() {
            return Objects.hash(securityNo, providerNo, Arrays.hashCode(authResult), mobileOptimized,
                    submitType, oauthToken, mfaVerified, credentialFingerprint);
        }

        /** Deliberately omits the authentication result and OAuth token from diagnostics. */
        @Override
        public String toString() {
            return "PendingSessionChoice[securityNo=" + securityNo + ", authResult=<redacted>]";
        }
    }
}
