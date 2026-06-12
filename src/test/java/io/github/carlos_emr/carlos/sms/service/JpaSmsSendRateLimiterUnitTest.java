package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dao.SmsProviderRateLimitDao;
import io.github.carlos_emr.carlos.sms.model.SmsProviderRateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class JpaSmsSendRateLimiterUnitTest {
    @Mock
    private SmsProviderRateLimitDao rateLimitDao;

    @Test
    @DisplayName("tryAcquire rejects sends after the SMS provider DB window is full")
    void shouldRejectSend_whenWindowLimitIsReached() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-08T12:00:00Z"));
        SmsProviderRateLimit rateLimit = SmsProviderRateLimit.forProvider(
                SmsProviderType.STUB,
                Date.from(clock.instant())
        );
        JpaSmsSendRateLimiter limiter = new JpaSmsSendRateLimiter(
                rateLimitDao,
                2,
                Duration.ofMinutes(1),
                clock
        );
        when(rateLimitDao.findByProviderTypeForUpdate(SmsProviderType.STUB)).thenReturn(Optional.of(rateLimit));

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isFalse();

        assertThat(rateLimit.getSendCount()).isEqualTo(2);
        verify(rateLimitDao, never()).persist(rateLimit);
    }

    @Test
    @DisplayName("tryAcquire resets the SMS provider DB window after duration elapses")
    void shouldAllowSend_whenWindowResets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-08T12:00:00Z"));
        SmsProviderRateLimit rateLimit = SmsProviderRateLimit.forProvider(
                SmsProviderType.STUB,
                Date.from(clock.instant())
        );
        JpaSmsSendRateLimiter limiter = new JpaSmsSendRateLimiter(
                rateLimitDao,
                1,
                Duration.ofMinutes(1),
                clock
        );
        when(rateLimitDao.findByProviderTypeForUpdate(SmsProviderType.STUB)).thenReturn(Optional.of(rateLimit));

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isFalse();
        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(rateLimit.getSendCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("tryAcquire resets the SMS provider DB window when the clock moves backward")
    void shouldAllowSend_whenClockMovesBackward() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-08T12:00:00Z"));
        SmsProviderRateLimit rateLimit = SmsProviderRateLimit.forProvider(
                SmsProviderType.STUB,
                Date.from(clock.instant())
        );
        JpaSmsSendRateLimiter limiter = new JpaSmsSendRateLimiter(
                rateLimitDao,
                1,
                Duration.ofMinutes(1),
                clock
        );
        when(rateLimitDao.findByProviderTypeForUpdate(SmsProviderType.STUB)).thenReturn(Optional.of(rateLimit));

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isFalse();
        clock.advance(Duration.ofSeconds(-10));

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(rateLimit.getSendCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("tryAcquire clamps sub-millisecond DB windows to one millisecond")
    void shouldClampWindow_whenDurationIsSubMillisecond() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-08T12:00:00Z"));
        SmsProviderRateLimit rateLimit = SmsProviderRateLimit.forProvider(
                SmsProviderType.STUB,
                Date.from(clock.instant())
        );
        JpaSmsSendRateLimiter limiter = new JpaSmsSendRateLimiter(
                rateLimitDao,
                1,
                Duration.ofNanos(1),
                clock
        );
        when(rateLimitDao.findByProviderTypeForUpdate(SmsProviderType.STUB)).thenReturn(Optional.of(rateLimit));

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isFalse();
        clock.advance(Duration.ofMillis(1));

        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
    }

    @Test
    @DisplayName("tryAcquire inserts SMS provider limiter row before locking")
    void shouldInsertRateLimitRowBeforeLock_whenProviderIsUsed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-08T12:00:00Z"));
        SmsProviderRateLimit rateLimit = SmsProviderRateLimit.forProvider(
                SmsProviderType.CLOUDLI,
                Date.from(clock.instant())
        );
        JpaSmsSendRateLimiter limiter = new JpaSmsSendRateLimiter(
                rateLimitDao,
                60,
                Duration.ofMinutes(1),
                clock
        );
        when(rateLimitDao.findByProviderTypeForUpdate(SmsProviderType.CLOUDLI)).thenReturn(Optional.of(rateLimit));

        assertThat(limiter.tryAcquire(SmsProviderType.CLOUDLI)).isTrue();

        verify(rateLimitDao).insertIfMissing(SmsProviderType.CLOUDLI, Date.from(clock.instant()));
        verify(rateLimitDao, never()).persist(any(SmsProviderRateLimit.class));
        verify(rateLimitDao).merge(rateLimit);
        verify(rateLimitDao).flush();
    }

    @Test
    @DisplayName("tryAcquire fails closed when SMS provider limiter row is still missing after insert")
    void shouldFailClosed_whenProviderLimiterRowCannotBeLocked() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-08T12:00:00Z"));
        JpaSmsSendRateLimiter limiter = new JpaSmsSendRateLimiter(
                rateLimitDao,
                60,
                Duration.ofMinutes(1),
                clock
        );
        when(rateLimitDao.findByProviderTypeForUpdate(SmsProviderType.CLOUDLI)).thenReturn(Optional.empty());

        assertThat(limiter.tryAcquire(SmsProviderType.CLOUDLI)).isFalse();

        verify(rateLimitDao).insertIfMissing(SmsProviderType.CLOUDLI, Date.from(clock.instant()));
        verify(rateLimitDao, never()).persist(any(SmsProviderRateLimit.class));
        verify(rateLimitDao, never()).merge(any(SmsProviderRateLimit.class));
        verify(rateLimitDao, never()).flush();
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
