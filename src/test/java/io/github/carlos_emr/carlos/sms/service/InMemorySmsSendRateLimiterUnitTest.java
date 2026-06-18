package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class InMemorySmsSendRateLimiterUnitTest {
    @Test
    @DisplayName("tryAcquire rejects sends after the provider window is full")
    void shouldRejectSend_whenWindowLimitIsReached() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-08T12:00:00Z"));
        InMemorySmsSendRateLimiter limiter = new InMemorySmsSendRateLimiter(2, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isFalse();
    }

    @Test
    @DisplayName("tryAcquire resets the provider window after duration elapses")
    void shouldAllowSend_whenWindowResets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-08T12:00:00Z"));
        InMemorySmsSendRateLimiter limiter = new InMemorySmsSendRateLimiter(1, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isFalse();
        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
    }

    @Test
    @DisplayName("tryAcquire resets the provider window when the clock moves backward")
    void shouldAllowSend_whenClockMovesBackward() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-08T12:00:00Z"));
        InMemorySmsSendRateLimiter limiter = new InMemorySmsSendRateLimiter(1, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isFalse();
        clock.advance(Duration.ofSeconds(-10));

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
    }

    @Test
    @DisplayName("tryAcquire clamps sub-millisecond windows to one millisecond")
    void shouldClampWindow_whenDurationIsSubMillisecond() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-08T12:00:00Z"));
        InMemorySmsSendRateLimiter limiter = new InMemorySmsSendRateLimiter(1, Duration.ofNanos(1), clock);

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isFalse();
        clock.advance(Duration.ofMillis(1));

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
