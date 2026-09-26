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
import java.util.Objects;

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

    private final Cache<String, PendingSessionChoice> cache;
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
        cache.put(token, choice);
        return token;
    }

    PendingSessionChoice peek(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return cache.getIfPresent(token);
    }

    PendingSessionChoice consume(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        // Atomic remove-and-return: two concurrent submits of the same token cannot both complete.
        return cache.asMap().remove(token);
    }

    void invalidate(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        cache.invalidate(token);
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
     */
    record PendingSessionChoice(Integer securityNo, String providerNo, String[] authResult,
                                boolean mobileOptimized, String submitType, String oauthToken,
                                boolean mfaVerified) {

        /** A pending login that did not go through MFA. */
        PendingSessionChoice(Integer securityNo, String providerNo, String[] authResult,
                             boolean mobileOptimized, String submitType, String oauthToken) {
            this(securityNo, providerNo, authResult, mobileOptimized, submitType, oauthToken, false);
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
                    && securityNo.equals(that.securityNo)
                    && providerNo.equals(that.providerNo)
                    && Arrays.equals(authResult, that.authResult)
                    && Objects.equals(submitType, that.submitType)
                    && Objects.equals(oauthToken, that.oauthToken);
        }

        @Override
        public int hashCode() {
            return Objects.hash(securityNo, providerNo, Arrays.hashCode(authResult), mobileOptimized,
                    submitType, oauthToken, mfaVerified);
        }

        /** Deliberately omits the authentication result and OAuth token from diagnostics. */
        @Override
        public String toString() {
            return "PendingSessionChoice[securityNo=" + securityNo + ", authResult=<redacted>]";
        }
    }
}
