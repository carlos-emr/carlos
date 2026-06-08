package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemorySmsSendRateLimiter implements SmsSendRateLimiter {
    private static final int DEFAULT_MAX_SENDS_PER_WINDOW = 60;
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    private final int maxSendsPerWindow;
    private final long windowMillis;
    private final Clock clock;
    private final Map<SmsProviderType, WindowCounter> counters = new ConcurrentHashMap<>();

    public InMemorySmsSendRateLimiter() {
        this(DEFAULT_MAX_SENDS_PER_WINDOW, DEFAULT_WINDOW, Clock.systemUTC());
    }

    InMemorySmsSendRateLimiter(int maxSendsPerWindow, Duration window, Clock clock) {
        this.maxSendsPerWindow = Math.max(1, maxSendsPerWindow);
        Duration safeWindow = window == null || window.isNegative() || window.isZero()
                ? DEFAULT_WINDOW
                : window;
        this.windowMillis = safeWindow.toMillis();
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public boolean tryAcquire(SmsProviderType providerType) {
        SmsProviderType safeProviderType = providerType == null ? SmsProviderType.STUB : providerType;
        WindowCounter counter = counters.computeIfAbsent(safeProviderType, ignored -> new WindowCounter(clock.millis()));
        synchronized (counter) {
            long now = clock.millis();
            if (now - counter.windowStartedAt >= windowMillis) {
                counter.windowStartedAt = now;
                counter.count = 0;
            }
            if (counter.count >= maxSendsPerWindow) {
                return false;
            }
            counter.count++;
            return true;
        }
    }

    private static class WindowCounter {
        private long windowStartedAt;
        private int count;

        private WindowCounter(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}
