/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Objects;

/**
 * Short-lived, server-side store that records TOTP codes already accepted for a security record so
 * the same code cannot be replayed within its validity window.
 *
 * <p>RFC 6238 §5.2 requires that an accepted one-time password be rejected on subsequent
 * submissions. CARLOS accepts the current time step and either adjacent one to tolerate clock skew,
 * so without used-code tracking a code observed by an attacker (shoulder surfing, phishing proxy,
 * pre-TLS interception) could authenticate a second, independent pending-MFA session inside that
 * window. This cache closes that replay gap.</p>
 *
 * <p>Entries are keyed by security id and code and expire once {@link TotpWindow#ACCEPTANCE} has
 * elapsed since the write, so a code is remembered for exactly as long as it would be accepted.
 * Recording and the replay check are a single atomic {@code putIfAbsent} so concurrent submitters of
 * the same code cannot both be admitted.</p>
 */
final class MfaUsedCodeCache {
    private static final Logger logger = MiscUtils.getLogger();

    private static final Duration TTL = TotpWindow.ACCEPTANCE;
    private static final long MAX_SIZE = 100_000L;

    private static final MfaUsedCodeCache INSTANCE = new MfaUsedCodeCache();

    private final Cache<String, Boolean> cache;

    MfaUsedCodeCache() {
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
     * <p>Callers reach this only after a code has matched a time step, so a rejection here always
     * means the authentication attempt must fail. Unusable inputs therefore fail closed rather than
     * skipping replay tracking: admitting a code this cache cannot remember would hand back the
     * replay window the cache exists to close. {@code securityNo} is the primary key of a persisted
     * {@code Security} row and is never null in the login flow, so a null argument is a programming
     * error and is logged as one.</p>
     *
     * @param securityNo security record id the code was accepted for; must not be null
     * @param code TOTP code that just passed validation; must not be null or empty
     * @return true when the code had not been used before (caller may proceed); false when the code
     *         was already accepted within the window, or when the inputs cannot be tracked (replay
     *         or unusable input; caller must reject)
     */
    boolean recordIfUnused(Integer securityNo, String code) {
        if (securityNo == null) {
            // Not reachable from the login flow; log loudly rather than silently failing a login.
            logger.error("Rejecting MFA code because used-code tracking was given a null security id");
            return false;
        }
        if (code == null || code.isEmpty()) {
            return false;
        }
        String key = securityNo + ":" + code;
        // Atomic insert-if-absent so two concurrent submissions of the same code cannot both
        // observe it as unused before either records it.
        return cache.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }

    /**
     * Number of codes currently tracked, after evicting entries whose window has passed. Exposed for
     * tests asserting that rejected input is not recorded.
     */
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
