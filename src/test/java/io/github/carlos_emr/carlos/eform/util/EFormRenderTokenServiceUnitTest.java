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

@DisplayName("EFormRenderTokenService unit tests")
@Tag("unit")
@Tag("fast")
class EFormRenderTokenServiceUnitTest {

    @Test
    @DisplayName("should issue opaque URL-safe tokens redeemable exactly once")
    void shouldIssueSingleUseToken_forBoundEform() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());

        String token = service.issue(187, "999998");

        assertThat(token).isNotBlank().matches("[A-Za-z0-9_-]{40,}");
        EFormRenderTokenService.RenderGrant grant = service.consume(token);
        assertThat(grant).isNotNull();
        assertThat(grant.fdid()).isEqualTo(187);
        assertThat(grant.providerNo()).isEqualTo("999998");
        assertThat(service.consume(token)).as("second redemption must fail").isNull();
    }

    @Test
    @DisplayName("should return null when consuming unknown or empty tokens")
    void shouldReturnNull_forUnknownOrEmptyTokens() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());

        assertThat(service.consume(null)).isNull();
        assertThat(service.consume("")).isNull();
        assertThat(service.consume("never-issued")).isNull();
    }

    @Test
    @DisplayName("should return the grant repeatedly without removing it when peeking")
    void shouldReturnGrantRepeatedly_whenPeeking() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());

        String token = service.issue(187, "999998");

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
    @DisplayName("should return null when peeking unknown or empty tokens")
    void shouldReturnNull_whenPeekingUnknownOrEmptyTokens() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());

        assertThat(service.peek(null)).isNull();
        assertThat(service.peek("")).isNull();
        assertThat(service.peek("never-issued")).isNull();
    }

    @Test
    @DisplayName("should expire unredeemed grants just past the two-minute token time to live")
    void shouldExpireGrant_afterTimeToLive() {
        AtomicLong nowNanos = new AtomicLong(0);
        EFormRenderTokenService service = new EFormRenderTokenService(nowNanos::get);

        String token = service.issue(187, "999998");
        // Advance just past the two-minute TTL rather than an over-generous three minutes, so a
        // regression that widened the TTL (e.g. to 2m30s) would fail this test instead of passing.
        nowNanos.addAndGet(java.time.Duration.ofMinutes(2).plusSeconds(1).toNanos());

        assertThat(service.consume(token)).isNull();
    }

    @Test
    @DisplayName("should keep unredeemed grants live just before the two-minute token time to live")
    void shouldKeepGrant_beforeTimeToLive() {
        AtomicLong nowNanos = new AtomicLong(0);
        EFormRenderTokenService service = new EFormRenderTokenService(nowNanos::get);

        String token = service.issue(187, "999998");
        // One second short of the two-minute TTL: the grant must still be redeemable, pinning the
        // lower edge of the contract so a regression that shortened the TTL would be caught too.
        nowNanos.addAndGet(java.time.Duration.ofSeconds(119).toNanos());

        assertThat(service.peek(token)).isNotNull();
    }

    @Test
    @DisplayName("should discard grants when invalidated before redemption")
    void shouldDiscardGrant_whenInvalidatedBeforeRedemption() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());

        String token = service.issue(187, null);
        service.invalidate(token);

        assertThat(service.consume(token)).isNull();
        service.invalidate(null);
        service.invalidate("unknown");
    }

    @Test
    @DisplayName("should issue unique tokens for repeated grants")
    void shouldIssueUniqueTokens_forRepeatedGrants() {
        EFormRenderTokenService service = new EFormRenderTokenService(Ticker.systemTicker());

        assertThat(service.issue(1, "a")).isNotEqualTo(service.issue(1, "a"));
        assertThat(service.size()).isEqualTo(2);
    }

}
