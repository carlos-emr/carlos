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
package io.github.carlos_emr.carlos.eform.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Two-minute capabilities for a sessionless browser render. Each grant is bound to one saved eForm,
 * provider, and exact referenced asset set; the render lease invalidates it on completion.
 */
final class EFormRenderTokenService {

    // The token value is a live capability reference and MUST NEVER be logged. RenderToken's
    // redacting toString() makes accidental "{}" formatting safe; these traces record grant
    // lifecycle by fdid only (a PHI-correlating identifier, never clinical content).
    private static final Logger logger = MiscUtils.getLogger();

    private static final long MAX_SIZE = 1_000L;
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final int TOKEN_BYTES = 32;
    private static final int SESSION_BYTES = 32;

    private static final EFormRenderTokenService INSTANCE = new EFormRenderTokenService();

    private final Cache<String, RenderGrant> cache;
    private final Cache<String, RenderGrant> sessionCache;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Opaque render-token value. The raw string is a live loopback render capability, so this
     * wrapper redacts {@link #toString()} — a token can no longer leak through accidental log
     * formatting anywhere it travels — and the URL splice points must ask for the value
     * explicitly via {@link #queryValue()}.
     */
    record RenderToken(String queryValue) {

        RenderToken {
            // A RenderToken never holds a null/empty value: the only factory (fromRequestValue) maps
            // absent/empty input to a null token instead. Enforcing it here makes the invariant
            // structural, so callers only ever need a null check (no redundant isEmpty() guard).
            if (queryValue == null || queryValue.isEmpty()) {
                throw new IllegalArgumentException("RenderToken value must be non-empty");
            }
        }

        /** Wraps a request-supplied parameter value; null/empty (absent param) maps to null. */
        static RenderToken fromRequestValue(String rawValue) {
            return (rawValue == null || rawValue.isEmpty()) ? null : new RenderToken(rawValue);
        }

        @Override
        public String toString() {
            return "[render-token]";
        }
    }

    private EFormRenderTokenService() {
        this(Ticker.systemTicker());
    }

    EFormRenderTokenService(Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .ticker(Objects.requireNonNull(ticker, "ticker must not be null"))
                .build();
        this.sessionCache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .ticker(ticker)
                .build();
    }

    static EFormRenderTokenService getInstance() {
        return INSTANCE;
    }

    /**
     * Issues a token authorizing one render of the given saved eForm.
     *
     * @param fdid saved eForm data identifier the grant is bound to
     * @param providerNo nullable; propagated into the grant for provider-scoped signature rendering.
     * @return opaque URL-safe token to place on the renderer request
     */
    RenderToken issue(int fdid, String providerNo) {
        String token = generateToken();
        cache.put(token, new RenderGrant(fdid, providerNo));
        logger.debug("Render grant issued for fdid={} (grants live={})", fdid, cache.estimatedSize());
        return new RenderToken(token);
    }

    /**
     * Issues a grant and hands back a {@link RenderLease} so the caller can bind the grant's
     * lifetime to a try-with-resources block: {@link RenderLease#close()} invalidates the token at
     * end of render (success or failure). This is the render's own teardown path — the TTL is only
     * the backstop for a JVM that dies mid-render.
     *
     * @param fdid saved eForm data identifier the grant is bound to
     * @param providerNo nullable; propagated into the grant for provider-scoped signature rendering
     * @return a render-scoped lease over a freshly issued grant
     */
    RenderLease lease(int fdid, String providerNo) {
        return new RenderLease(issue(fdid, providerNo));
    }

    /**
     * Returns a token's grant <em>without</em> removing it, so the same render can authorize the
     * eForm document and every loopback subresource it pulls (background/asset images) under one
     * grant. The renderer bounds the lifetime by {@link #invalidate(RenderToken)}ing the token when
     * the render finishes; the TTL is the backstop.
     *
     * @return the grant, or null when the token is unknown, expired, or invalidated
     */
    RenderGrant peek(RenderToken token) {
        if (token == null) {
            return null;
        }
        RenderGrant grant = cache.getIfPresent(token.queryValue());
        // peek runs for every loopback subresource of a render (hot path), so only the miss is logged
        // — a rejected subresource fetch is the interesting event; a hit is the expected steady state.
        if (grant == null) {
            logger.debug("Render grant peek found no live grant for the presented token (unknown/expired/invalidated)");
        }
        return grant;
    }

    /**
     * Limits a render grant to template assets referenced by the bound eForm HTML.
     */
    void authorizeAssets(RenderToken token, Collection<String> fileNames) {
        RenderGrant grant = peek(token);
        if (grant != null) {
            grant.authorizeAssets(fileNames);
        }
    }

    /**
     * Exchanges a bootstrap token for a renderer-only session handle. A retry is idempotent only
     * when it presents the handle already bound to the token; another browser cannot replay it.
     */
    RenderSession exchange(RenderToken token, String presentedSessionValue) {
        RenderGrant grant = peek(token);
        if (grant == null) {
            return null;
        }
        synchronized (grant) {
            if (grant.sessionValue == null) {
                if (presentedSessionValue != null && !presentedSessionValue.isEmpty()) {
                    return null;
                }
                grant.sessionValue = generateSessionValue();
                sessionCache.put(grant.sessionValue, grant);
                return new RenderSession(grant.sessionValue);
            }
            if (!grant.sessionValue.equals(presentedSessionValue)) {
                return null;
            }
            return new RenderSession(grant.sessionValue);
        }
    }

    RenderGrant peekSession(String sessionValue) {
        if (sessionValue == null || sessionValue.isEmpty()) {
            return null;
        }
        return sessionCache.getIfPresent(sessionValue);
    }

    void authorizeStaticPaths(RenderGrant grant, Collection<String> paths) {
        if (grant != null) {
            grant.authorizeStaticPaths(paths);
        }
    }

    void authorizeApKeys(RenderToken token, Collection<String> keys) {
        RenderGrant grant = peek(token);
        if (grant != null) {
            grant.authorizeApKeys(keys);
        }
    }

    void authorizeApKeys(RenderGrant grant, Collection<String> keys) {
        if (grant != null) {
            grant.authorizeApKeys(keys);
        }
    }

    /**
     * Discards a token at end of render — success, failure, or never-redeemed. Under the
     * render-scoped peek model this is the normal teardown for redeemed tokens too, not just
     * cleanup of unredeemed ones; the TTL is only the backstop. Safe for null/unknown/
     * already-consumed tokens.
     */
    void invalidate(RenderToken token) {
        if (token == null) {
            return;
        }
        RenderGrant grant = cache.getIfPresent(token.queryValue());
        cache.invalidate(token.queryValue());
        if (grant != null) {
            synchronized (grant) {
                if (grant.sessionValue != null) {
                    sessionCache.invalidate(grant.sessionValue);
                    grant.sessionValue = null;
                }
            }
        }
        logger.debug("Render grant invalidated (render finished or aborted)");
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

    private String generateSessionValue() {
        byte[] bytes = new byte[SESSION_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Opaque renderer-only cookie value with a redacted string representation. */
    record RenderSession(String cookieValue) {
        RenderSession {
            if (cookieValue == null || cookieValue.isEmpty()) {
                throw new IllegalArgumentException("Render session value must be non-empty");
            }
        }

        @Override
        public String toString() {
            return "[render-session]";
        }
    }

    /** Render authorization bound to one saved eForm, provider, and its referenced template assets. */
    static final class RenderGrant {
        private final int fdid;
        private final String providerNo;
        private final Set<String> allowedAssets = ConcurrentHashMap.newKeySet();
        private final Set<String> allowedStaticPaths = ConcurrentHashMap.newKeySet();
        private final Set<String> allowedApKeys = ConcurrentHashMap.newKeySet();
        private volatile String sessionValue;

        private RenderGrant(int fdid, String providerNo) {
            this.fdid = fdid;
            this.providerNo = providerNo;
        }

        int fdid() {
            return fdid;
        }

        String providerNo() {
            return providerNo;
        }

        boolean allowsAsset(String fileName) {
            return allowedAssets.contains(fileName);
        }

        boolean allowsStaticPath(String path) {
            return allowedStaticPaths.contains(path);
        }

        boolean allowsApKey(String key) {
            return allowedApKeys.contains(key);
        }

        private void authorizeAssets(Collection<String> fileNames) {
            if (fileNames != null) {
                allowedAssets.addAll(fileNames);
            }
        }

        private void authorizeStaticPaths(Collection<String> paths) {
            if (paths != null) {
                allowedStaticPaths.addAll(paths);
            }
        }

        private void authorizeApKeys(Collection<String> keys) {
            if (keys != null) {
                allowedApKeys.addAll(keys);
            }
        }
    }

    /**
     * Render-scoped lease over an issued grant: {@link #close()} invalidates the grant, so
     * try-with-resources guarantees the grant dies with the render even if the render body throws.
     */
    public final class RenderLease implements AutoCloseable {
        private final RenderToken token;

        private RenderLease(RenderToken token) {
            this.token = token;
        }

        public RenderToken token() {
            return token;
        }

        @Override
        public void close() {
            invalidate(token);
        }
    }
}
