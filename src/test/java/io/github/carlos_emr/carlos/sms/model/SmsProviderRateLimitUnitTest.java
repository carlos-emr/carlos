package io.github.carlos_emr.carlos.sms.model;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("model")
class SmsProviderRateLimitUnitTest {
    @Test
    @DisplayName("tryAcquire rejects sends after the window is full")
    void shouldRejectSend_whenWindowLimitIsReached() {
        Date now = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        SmsProviderRateLimit rateLimit = SmsProviderRateLimit.forProvider(SmsProviderType.STUB, now);

        assertThat(rateLimit.tryAcquire(now, 2, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimit.tryAcquire(now, 2, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimit.tryAcquire(now, 2, Duration.ofMinutes(1))).isFalse();
        assertThat(rateLimit.getSendCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("tryAcquire resets count when the window expires")
    void shouldResetCount_whenWindowExpires() {
        Date now = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        Date nextWindow = Date.from(Instant.parse("2026-06-08T12:01:00Z"));
        SmsProviderRateLimit rateLimit = SmsProviderRateLimit.forProvider(SmsProviderType.STUB, now);

        assertThat(rateLimit.tryAcquire(now, 1, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimit.tryAcquire(now, 1, Duration.ofMinutes(1))).isFalse();
        assertThat(rateLimit.tryAcquire(nextWindow, 1, Duration.ofMinutes(1))).isTrue();

        assertThat(rateLimit.getSendCount()).isEqualTo(1);
        assertThat(rateLimit.getWindowStartedAt()).isEqualTo(nextWindow);
    }

    @Test
    @DisplayName("tryAcquire resets count when the clock moves backward")
    void shouldResetCount_whenClockMovesBackward() {
        Date now = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        Date earlier = Date.from(Instant.parse("2026-06-08T11:59:50Z"));
        SmsProviderRateLimit rateLimit = SmsProviderRateLimit.forProvider(SmsProviderType.STUB, now);

        assertThat(rateLimit.tryAcquire(now, 1, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimit.tryAcquire(now, 1, Duration.ofMinutes(1))).isFalse();
        assertThat(rateLimit.tryAcquire(earlier, 1, Duration.ofMinutes(1))).isTrue();

        assertThat(rateLimit.getSendCount()).isEqualTo(1);
        assertThat(rateLimit.getWindowStartedAt()).isEqualTo(earlier);
    }

    @Test
    @DisplayName("tryAcquire clamps sub-millisecond windows to one millisecond")
    void shouldClampWindow_whenDurationIsSubMillisecond() {
        Date now = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        Date oneMillisecondLater = Date.from(Instant.parse("2026-06-08T12:00:00.001Z"));
        SmsProviderRateLimit rateLimit = SmsProviderRateLimit.forProvider(SmsProviderType.STUB, now);

        assertThat(rateLimit.tryAcquire(now, 1, Duration.ofNanos(1))).isTrue();
        assertThat(rateLimit.tryAcquire(now, 1, Duration.ofNanos(1))).isFalse();
        assertThat(rateLimit.tryAcquire(oneMillisecondLater, 1, Duration.ofNanos(1))).isTrue();
    }
}
