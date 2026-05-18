/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Short-lived, server-side transient store for pending MFA challenge state.
 *
 * <p>Password/PIN authentication succeeds before OTP validation, but the HTTP session must not
 * become a container for authenticated domain objects or MFA registration secrets during that
 * gap. Sessions can be serialized to disk, replicated across nodes, and inspected in diagnostic
 * dumps. This cache keeps the sensitive pending state process-local and gives the session only an
 * opaque random token plus non-secret routing hints.</p>
 *
 * <p>Entries expire five minutes after write, matching the pending-MFA session lifetime created by
 * {@link Login2Action}. Retryable invalid-code submissions use {@link #peek(String)} so the same
 * challenge can remain live until success, explicit failure, exhaustion, or TTL expiry.</p>
 */
final class PendingMfaChallengeCache {
    private static final long MAX_SIZE = 10_000L;
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int TOKEN_BYTES = 32;

    private static final PendingMfaChallengeCache INSTANCE = new PendingMfaChallengeCache();

    private final Cache<String, PendingMfaChallenge> cache;
    private final SecureRandom secureRandom = new SecureRandom();

    private PendingMfaChallengeCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .build();
    }

    static PendingMfaChallengeCache getInstance() {
        return INSTANCE;
    }

    String store(PendingMfaChallenge challenge) {
        Objects.requireNonNull(challenge, "challenge must not be null");
        String token = generateToken();
        cache.put(token, challenge);
        return token;
    }

    PendingMfaChallenge peek(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return cache.getIfPresent(token);
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
     * Immutable MFA challenge payload kept out of the HTTP session.
     *
     * <p>The {@code authResult} array is copied on construction and access because it is the
     * legacy {@link LoginCheckLogin#auth} success contract. The optional registration secret is
     * present only while the user is proving ownership of a newly generated MFA seed.</p>
     */
    static final class PendingMfaChallenge {
        private final Integer securityNo;
        private final String providerNo;
        private final String[] authResult;
        private final String registrationSecret;

        PendingMfaChallenge(Integer securityNo, String providerNo, String[] authResult,
                            String registrationSecret) {
            this.securityNo = Objects.requireNonNull(securityNo, "securityNo must not be null");
            this.providerNo = Objects.requireNonNull(providerNo, "providerNo must not be null");
            this.authResult = Arrays.copyOf(
                    Objects.requireNonNull(authResult, "authResult must not be null"), authResult.length);
            this.registrationSecret = registrationSecret;
        }

        Integer securityNo() {
            return securityNo;
        }

        String providerNo() {
            return providerNo;
        }

        String[] authResult() {
            return Arrays.copyOf(authResult, authResult.length);
        }

        String registrationSecret() {
            return registrationSecret;
        }
    }
}
