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
import java.util.Objects;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Short-lived, render-scoped grants that authorize the server-side browser renderer to fetch one
 * saved eForm surface.
 *
 * <p>The renderer's headless browser is a separate process with no HTTP session. Instead of
 * forwarding the requesting user's session cookie into that browser (which would let any script
 * on the rendered page act as the user), the caller mints an opaque token here <em>after</em> its
 * own {@code _eform} privilege check, and the PDF-render servlets redeem it for the bound
 * {@code fdid}. This mirrors the pending-MFA cache-token pattern: the token is a random capability
 * reference, never a credential.</p>
 *
 * <p><strong>Render-scoped, not single-use.</strong> A single render fetches several loopback
 * subresources under the same token — the main eForm document plus its {@code ${oscar_image_path}}
 * background/asset images (rendered via {@link EFormImageViewForPdfGenerationServlet}). Those
 * subresource fetches carry no HTTP session, so they authorize themselves by
 * {@link #peek(RenderToken)}ing the same grant. Redemption therefore does <em>not</em> remove the
 * token; instead the renderer holds a {@link RenderLease} whose {@link RenderLease#close()}
 * {@link #invalidate(RenderToken)}s the grant at end of render — try-with-resources guarantees this
 * even when the render body throws — and the two-minute TTL bounds any leak.</p>
 *
 * <p>Entries expire two minutes after issue — comfortably above the renderer's page budget and
 * far below any session lifetime.</p>
 */
final class EFormRenderTokenService {

    // The token value is a live capability reference and MUST NEVER be logged. RenderToken's
    // redacting toString() makes accidental "{}" formatting safe; these traces record grant
    // lifecycle by fdid only (a PHI-correlating identifier, never clinical content).
    private static final Logger logger = MiscUtils.getLogger();

    private static final long MAX_SIZE = 1_000L;
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final int TOKEN_BYTES = 32;

    private static final EFormRenderTokenService INSTANCE = new EFormRenderTokenService();

    private final Cache<String, RenderGrant> cache;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Opaque render-token value. The raw string is a live loopback render capability, so this
     * wrapper redacts {@link #toString()} — a token can no longer leak through accidental log
     * formatting anywhere it travels — and the URL splice points must ask for the value
     * explicitly via {@link #queryValue()}.
     */
    record RenderToken(String queryValue) {

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
        if (token == null || token.queryValue().isEmpty()) {
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
     * Discards a token at end of render — success, failure, or never-redeemed. Under the
     * render-scoped peek model this is the normal teardown for redeemed tokens too, not just
     * cleanup of unredeemed ones; the TTL is only the backstop. Safe for null/unknown/
     * already-consumed tokens.
     */
    void invalidate(RenderToken token) {
        if (token == null || token.queryValue().isEmpty()) {
            return;
        }
        cache.invalidate(token.queryValue());
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

    /** Immutable render authorization bound to one saved eForm and optional provider scope. */
    record RenderGrant(int fdid, String providerNo) {
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
