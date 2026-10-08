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
package io.github.carlos_emr.carlos.webserv.oauth.util;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * Bounds how many {@code OAUTH_LOGIN_FAILURE} audit rows {@link OAuthInterceptor} writes (issue #4429).
 *
 * <p>Every rejected call to the OAuth-only {@code /ws/services} surface is audited with a
 * <em>synchronous</em> {@code log} insert. An anonymous client needs no credentials to be rejected, so
 * without a bound it can drive an unbounded stream of inserts: the audit table grows, the database is
 * loaded, and the noise drowns out genuine authentication failures. The front-door rate limit narrows
 * that, but the application must not depend on it (loopback and direct-to-Tomcat callers bypass nginx).
 *
 * <p>Two fixed windows apply, both {@value #WINDOW_SECONDS} seconds:
 * <ul>
 *   <li>per remote address: at most {@value #MAX_ROWS_PER_ADDRESS} individual rows;</li>
 *   <li>across all addresses: at most {@value #MAX_ROWS_GLOBAL} individual rows, which bounds a
 *       distributed attacker that rotates source addresses.</li>
 * </ul>
 * The first rejection that exceeds a window returns {@link Decision#WRITE_SUMMARY}, so the trail still
 * records <em>that</em> suppression began (one row per address or per global window); later ones in the
 * same window return {@link Decision#SUPPRESS}. Tracked addresses are capped at {@value #MAX_ADDRESSES}
 * so the limiter's own memory is bounded under address spraying.
 *
 * <p>Successful authentications are not throttled here: they require a valid signed token.
 */
final class OAuthFailureAuditService {

    static final int WINDOW_SECONDS = 60;
    static final int MAX_ROWS_PER_ADDRESS = 10;
    static final int MAX_ROWS_GLOBAL = 300;
    static final long MAX_ADDRESSES = 10_000L;

    /** Bucket used when the transport supplied no remote address. */
    private static final String UNKNOWN_ADDRESS = "";

    /** What the caller should do with one rejected authentication. */
    enum Decision {
        /** Write the normal per-rejection audit row. */
        WRITE,
        /** Window budget just exhausted: write one summary row noting suppression has begun. */
        WRITE_SUMMARY,
        /** Budget already exhausted and summarised in this window: write nothing. */
        SUPPRESS
    }

    private final Cache<String, AtomicInteger> perAddress;
    private final Ticker ticker;
    private final long windowNanos = Duration.ofSeconds(WINDOW_SECONDS).toNanos();

    private long globalWindowStart;
    private int globalCount;

    OAuthFailureAuditService() {
        this(Ticker.systemTicker());
    }

    OAuthFailureAuditService(Ticker ticker) {
        this.ticker = ticker;
        this.globalWindowStart = ticker.read();
        // expireAfterWrite is measured from the entry's creation (the counter is mutated in place and
        // never re-put), which makes each address window fixed rather than sliding: a steady attacker
        // cannot keep extending its own window and so cannot stay silenced forever.
        this.perAddress = Caffeine.newBuilder()
            .maximumSize(MAX_ADDRESSES)
            .expireAfterWrite(Duration.ofSeconds(WINDOW_SECONDS))
            .ticker(ticker)
            .build();
    }

    /**
     * Classifies one rejected authentication from {@code remoteAddr} ({@code null} when unknown).
     * Both budgets apply and the stricter outcome wins; an address already past its own budget does not spend global budget.
     */
    Decision admit(String remoteAddr) {
        String key = remoteAddr == null ? UNKNOWN_ADDRESS : remoteAddr;
        int addressSeen = perAddress.get(key, k -> new AtomicInteger()).incrementAndGet();

        Decision local = addressSeen <= MAX_ROWS_PER_ADDRESS ? Decision.WRITE
            : addressSeen == MAX_ROWS_PER_ADDRESS + 1 ? Decision.WRITE_SUMMARY
            : Decision.SUPPRESS;
        if (local == Decision.SUPPRESS) {
            // An address already over its own budget writes nothing, so it must not also drain the
            // global budget: otherwise one flooder would starve every other address's failure rows.
            return Decision.SUPPRESS;
        }

        // Rows that would be written (individual or per-address summary) spend global budget; the
        // stricter of the two outcomes wins, and the global summary is reported once per window.
        Decision global = admitGlobal();
        if (local == Decision.WRITE && global == Decision.WRITE) {
            return Decision.WRITE;
        }
        if (local == Decision.WRITE_SUMMARY && global != Decision.SUPPRESS
                || global == Decision.WRITE_SUMMARY) {
            return Decision.WRITE_SUMMARY;
        }
        return Decision.SUPPRESS;
    }

    private synchronized Decision admitGlobal() {
        long now = ticker.read();
        if (now - globalWindowStart >= windowNanos) {
            globalWindowStart = now;
            globalCount = 0;
        }
        globalCount++;
        if (globalCount <= MAX_ROWS_GLOBAL) {
            return Decision.WRITE;
        }
        return globalCount == MAX_ROWS_GLOBAL + 1 ? Decision.WRITE_SUMMARY : Decision.SUPPRESS;
    }
}
