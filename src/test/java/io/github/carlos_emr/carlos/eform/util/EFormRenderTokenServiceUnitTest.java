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
    @DisplayName("should expire unredeemed grants after the token time to live")
    void shouldExpireGrant_afterTimeToLive() {
        AtomicLong nowNanos = new AtomicLong(0);
        EFormRenderTokenService service = new EFormRenderTokenService(nowNanos::get);

        String token = service.issue(187, "999998");
        nowNanos.addAndGet(java.time.Duration.ofMinutes(3).toNanos());

        assertThat(service.consume(token)).isNull();
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
