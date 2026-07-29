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

import java.util.concurrent.atomic.AtomicLong;

import com.github.benmanes.caffeine.cache.Ticker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EFormRenderTokenService unit tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormRenderTokenServiceUnitTest {

    @Test
    @DisplayName("should bind bootstrap exchange to one renderer session and reject replay")
    void shouldBindExchangeToOneSession_andRejectReplay() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());
        EFormRenderTokenService.RenderToken token = service.issue(187, "999998");

        EFormRenderTokenService.RenderSession session = service.exchange(token, null);

        assertThat(session).isNotNull().hasToString("[render-session]");
        assertThat(session.cookieValue()).matches("[A-Za-z0-9_-]{40,}");
        assertThat(service.exchange(token, session.cookieValue())).isEqualTo(session);
        assertThat(service.exchange(token, null)).isNull();
        assertThat(service.exchange(token, "another-browser")).isNull();
        assertThat(service.peekSession(session.cookieValue())).isNotNull();

        service.invalidate(token);
        assertThat(service.peekSession(session.cookieValue())).isNull();
    }

    @Test
    @DisplayName("should bind a lease to the eForm and discard the grant on close")
    void shouldBindLeaseToEform_andDiscardOnClose() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());

        EFormRenderTokenService.RenderToken token;
        try (EFormRenderTokenService.RenderLease lease = service.lease(187, "999998")) {
            token = lease.token();
            assertThat(token.queryValue()).isNotBlank().matches("[A-Za-z0-9_-]{40,}");
            assertThat(token).hasToString("[render-token]");
            // The document plus each asset-image subresource peeks the same live grant within the
            // render — repeatable, never consumed.
            EFormRenderTokenService.RenderGrant grant = service.peek(token);
            assertThat(grant).isNotNull();
            assertThat(grant.fdid()).isEqualTo(187);
            assertThat(grant.providerNo()).isEqualTo("999998");
            assertThat(service.peek(token)).as("peek must be repeatable within a render").isNotNull();
        }
        // close() invalidated the grant: the render is over and the token is a dead loopback capability.
        assertThat(service.peek(token)).as("grant must not survive the lease close").isNull();
    }

    @Test
    @DisplayName("should discard the grant when the render body throws inside the lease")
    void shouldDiscardGrant_whenLeaseBodyThrows() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());
        RuntimeException renderFailure = new RuntimeException("render failed mid-capture");

        EFormRenderTokenService.RenderLease lease = service.lease(187, "999998");
        EFormRenderTokenService.RenderToken token = lease.token();
        assertThatThrownBy(() -> {
            try (lease) {
                assertThat(service.peek(token)).isNotNull();
                throw renderFailure;
            }
        }).isSameAs(renderFailure);

        // try-with-resources ran close() before the throw propagated, so the grant died with the
        // render even though the body failed — the modern replacement for consume()'s single-use pin.
        assertThat(service.peek(token)).as("grant must die with the render even on failure").isNull();
    }

    @Test
    @DisplayName("should return the grant repeatedly without removing it when peeking")
    void shouldReturnGrantRepeatedly_whenPeeking() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());

        EFormRenderTokenService.RenderToken token = service.issue(187, "999998");

        // A render fetches the document plus several asset images under one grant, so peek must be
        // repeatable and must not remove the token.
        EFormRenderTokenService.RenderGrant first = service.peek(token);
        EFormRenderTokenService.RenderGrant second = service.peek(token);
        assertThat(first).isNotNull();
        assertThat(first.fdid()).isEqualTo(187);
        assertThat(first.providerNo()).isEqualTo("999998");
        assertThat(second).as("peek must be repeatable within a render").isNotNull();

        // The renderer bounds the lifetime by invalidating the token when the render finishes.
        service.invalidate(token);
        assertThat(service.peek(token)).as("invalidated token must no longer peek").isNull();
    }

    @Test
    @DisplayName("should return null when peeking a null or unknown token")
    void shouldReturnNull_whenPeekingNullOrUnknownToken() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());

        // An empty token can no longer be constructed (see the RenderToken invariant test), so the
        // "empty" case at the peek boundary is a null token.
        assertThat(service.peek(null)).isNull();
        assertThat(service.peek(new EFormRenderTokenService.RenderToken("never-issued"))).isNull();
    }

    @Test
    @DisplayName("should reject null or empty token values while mapping absent request params to null")
    void shouldRejectEmptyValue_forRenderTokenInvariant() {
        // The factory maps an absent/empty request parameter to a null token rather than throwing.
        assertThat(EFormRenderTokenService.RenderToken.fromRequestValue(null)).isNull();
        assertThat(EFormRenderTokenService.RenderToken.fromRequestValue("")).isNull();

        // Direct construction of a null/empty token violates the structural invariant and is rejected,
        // so callers only ever need a null check on a RenderToken (never a redundant isEmpty()).
        assertThatThrownBy(() -> new EFormRenderTokenService.RenderToken(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EFormRenderTokenService.RenderToken(""))
                .isInstanceOf(IllegalArgumentException.class);

        // A non-empty value round-trips through queryValue().
        EFormRenderTokenService.RenderToken token =
                EFormRenderTokenService.RenderToken.fromRequestValue("abc123");
        assertThat(token).isNotNull();
        assertThat(token.queryValue()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("should expire unredeemed grants just past the two-minute token time to live")
    void shouldExpireGrant_afterTimeToLive() {
        AtomicLong nowNanos = new AtomicLong(0);
        EFormRenderTokenService service = new EFormRenderTokenService(nowNanos::get);

        EFormRenderTokenService.RenderToken token = service.issue(187, "999998");
        // Advance just past the two-minute TTL rather than an over-generous three minutes, so a
        // regression that widened the TTL (e.g. to 2m30s) would fail this test instead of passing.
        nowNanos.addAndGet(java.time.Duration.ofMinutes(2).plusSeconds(1).toNanos());

        assertThat(service.peek(token)).isNull();
    }

    @Test
    @DisplayName("should keep unredeemed grants live just before the two-minute token time to live")
    void shouldKeepGrant_beforeTimeToLive() {
        AtomicLong nowNanos = new AtomicLong(0);
        EFormRenderTokenService service = new EFormRenderTokenService(nowNanos::get);

        EFormRenderTokenService.RenderToken token = service.issue(187, "999998");
        // One second short of the two-minute TTL: the grant must still be redeemable, pinning the
        // lower edge of the contract so a regression that shortened the TTL would be caught too.
        nowNanos.addAndGet(java.time.Duration.ofSeconds(119).toNanos());

        assertThat(service.peek(token)).isNotNull();
    }

    @Test
    @DisplayName("should discard grants when invalidated before redemption")
    void shouldDiscardGrant_whenInvalidatedBeforeRedemption() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());

        EFormRenderTokenService.RenderToken token = service.issue(187, null);
        service.invalidate(token);

        assertThat(service.peek(token)).isNull();
        service.invalidate(null);
        service.invalidate(new EFormRenderTokenService.RenderToken("unknown"));
    }

    @Test
    @DisplayName("should authorize only exact assets referenced by the rendered eForm")
    void shouldAuthorizeAssets_onlyWhenExactlyReferenced() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());
        EFormRenderTokenService.RenderToken token = service.issue(187, "999998");

        service.authorizeAssets(token, java.util.Set.of("bg.png", "form.css"));

        EFormRenderTokenService.RenderGrant grant = service.peek(token);
        assertThat(grant.allowsAsset("bg.png")).isTrue();
        assertThat(grant.allowsAsset("form.css")).isTrue();
        assertThat(grant.allowsAsset("logo.png")).isFalse();
        assertThat(grant.allowsAsset("BG.PNG")).isFalse();
    }

    @Test
    @DisplayName("should issue unique tokens for repeated grants")
    void shouldIssueUniqueTokens_forRepeatedGrants() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());

        assertThat(service.issue(1, "a")).isNotEqualTo(service.issue(1, "a"));
        assertThat(service.size()).isEqualTo(2);
    }

}
