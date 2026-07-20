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
 * {@link #peek(String)}ing the same grant. Redemption therefore does <em>not</em> remove the token;
 * instead the renderer {@link #invalidate(String)}s it in its {@code finally} block, and the
 * two-minute TTL bounds any leak. {@link #consume(String)} remains available for callers that want
 * atomic remove-on-read semantics.</p>
 *
 * <p>Entries expire two minutes after issue — comfortably above the renderer's page budget and
 * far below any session lifetime.</p>
 */
final class EFormRenderTokenService {

    // The token value is a live capability reference and MUST NEVER be logged. These traces record
    // grant lifecycle by fdid only (a PHI-correlating identifier, never clinical content).
    private static final Logger logger = MiscUtils.getLogger();

    private static final long MAX_SIZE = 1_000L;
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final int TOKEN_BYTES = 32;

    private static final EFormRenderTokenService INSTANCE = new EFormRenderTokenService();

    private final Cache<String, RenderGrant> cache;
    private final SecureRandom secureRandom = new SecureRandom();

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
     * @param providerNo provider number the render surface should be scoped to; may be null when
     *        the caller has no provider context (signature blocks then render unscoped)
     * @return opaque URL-safe token to place on the renderer request
     */
    String issue(int fdid, String providerNo) {
        String token = generateToken();
        cache.put(token, new RenderGrant(fdid, providerNo));
        logger.debug("Render grant issued for fdid={} (grants live={})", fdid, cache.estimatedSize());
        return token;
    }

    /**
     * Redeems a token, returning its grant at most once.
     *
     * @return the grant, or null when the token is unknown, expired, or already redeemed
     */
    RenderGrant consume(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        // Atomic remove so two concurrent redemption attempts cannot both observe the grant.
        RenderGrant grant = cache.asMap().remove(token);
        if (grant == null) {
            logger.debug("Render grant consume found no live grant (unknown/expired/already redeemed)");
        } else {
            logger.debug("Render grant consumed for fdid={}", grant.fdid());
        }
        return grant;
    }

    /**
     * Returns a token's grant <em>without</em> removing it, so the same render can authorize the
     * eForm document and every loopback subresource it pulls (background/asset images) under one
     * grant. The renderer bounds the lifetime by {@link #invalidate(String)}ing the token when the
     * render finishes; the TTL is the backstop.
     *
     * @return the grant, or null when the token is unknown, expired, or invalidated
     */
    RenderGrant peek(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        RenderGrant grant = cache.getIfPresent(token);
        // peek runs for every loopback subresource of a render (hot path), so only the miss is logged
        // — a rejected subresource fetch is the interesting event; a hit is the expected steady state.
        if (grant == null) {
            logger.debug("Render grant peek found no live grant for the presented token (unknown/expired/invalidated)");
        }
        return grant;
    }

    /**
     * Discards an unredeemed token, e.g. when a render fails before the browser ever fetched the
     * surface. Safe for null/unknown/already-consumed tokens.
     */
    void invalidate(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        cache.invalidate(token);
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
}
