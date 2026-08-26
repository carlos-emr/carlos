/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import java.time.Duration;
import java.util.Objects;

/**
 * Short-lived, server-side store that records TOTP codes already accepted for a security record so
 * the same code cannot be replayed within its validity window.
 *
 * <p>RFC 6238 §5.2 requires that an accepted one-time password be rejected on subsequent
 * submissions. CARLOS accepts the current, previous, and next 30-second time step (a roughly
 * 90-second window) to tolerate clock skew, so without used-code tracking a code observed by an
 * attacker (shoulder surfing, phishing proxy, pre-TLS interception) could authenticate a second,
 * independent pending-MFA session inside that window. This cache closes that replay gap.</p>
 *
 * <p>Entries are keyed by security id and code and expire 90 seconds after write, matching the
 * acceptance window. Recording and the replay check are a single atomic {@code putIfAbsent} so
 * concurrent submitters of the same code cannot both be admitted.</p>
 */
final class MfaUsedCodeCache {
    private static final Duration TTL = Duration.ofSeconds(90);
    private static final long MAX_SIZE = 100_000L;

    private static final MfaUsedCodeCache INSTANCE = new MfaUsedCodeCache();

    private final Cache<String, Boolean> cache;

    private MfaUsedCodeCache() {
        this(Ticker.systemTicker());
    }

    MfaUsedCodeCache(Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .ticker(Objects.requireNonNull(ticker, "ticker must not be null"))
                .build();
    }

    static MfaUsedCodeCache getInstance() {
        return INSTANCE;
    }

    /**
     * Records the code as used for the given security record if it has not already been recorded
     * within the validity window.
     *
     * @param securityNo security record id the code was accepted for
     * @param code TOTP code that just passed validation
     * @return true when the code had not been used before (caller may proceed); false when the code
     *         was already accepted within the window (replay; caller must reject)
     */
    boolean recordIfUnused(Integer securityNo, String code) {
        if (securityNo == null || code == null || code.isEmpty()) {
            return false;
        }
        String key = securityNo + ":" + code;
        // Atomic insert-if-absent so two concurrent submissions of the same code cannot both
        // observe it as unused before either records it.
        return cache.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }

    long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    /**
     * Drops all tracked codes. Used by tests to isolate the process-wide singleton between cases.
     */
    void invalidateAll() {
        cache.invalidateAll();
    }
}
