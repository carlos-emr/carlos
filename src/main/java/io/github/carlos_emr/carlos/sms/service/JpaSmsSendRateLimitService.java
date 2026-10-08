package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dao.SmsProviderRateLimitDao;
import io.github.carlos_emr.carlos.sms.model.SmsProviderRateLimit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Primary
@Service
public class JpaSmsSendRateLimitService implements SmsSendRateLimitService {
    // Initial fixed-window cap; tune once selected SMS provider limits and rollout volume are confirmed.
    private static final int DEFAULT_MAX_SENDS_PER_WINDOW = 5;
    private static final Duration DEFAULT_WINDOW = Duration.ofSeconds(5);

    private final SmsProviderRateLimitDao rateLimitDao;
    private final int maxSendsPerWindow;
    private final Duration window;
    private final Clock clock;

    @Autowired
    public JpaSmsSendRateLimitService(SmsProviderRateLimitDao rateLimitDao) {
        this(rateLimitDao, DEFAULT_MAX_SENDS_PER_WINDOW, DEFAULT_WINDOW, Clock.systemUTC());
    }

    JpaSmsSendRateLimitService(
            SmsProviderRateLimitDao rateLimitDao,
            int maxSendsPerWindow,
            Duration window,
            Clock clock
    ) {
        this.rateLimitDao = Objects.requireNonNull(rateLimitDao, "rateLimitDao is required");
        this.maxSendsPerWindow = Math.max(1, maxSendsPerWindow);
        this.window = safeWindow(window);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(SmsProviderType providerType) {
        SmsProviderType safeProviderType = providerType == null ? SmsProviderType.STUB : providerType;
        // Materialize the key with one atomic upsert before locking it. A locking read of a missing key
        // takes a gap lock under MariaDB repeatable read; concurrent inserts then deadlock while upgrading
        // those gap locks. The upsert also takes an exclusive lock for an existing key, so it avoids the
        // shared-lock upgrade deadlock caused by INSERT IGNORE.
        rateLimitDao.ensureExists(safeProviderType, Date.from(clock.instant()));
        Optional<SmsProviderRateLimit> lockedRow = rateLimitDao.findByProviderTypeForUpdate(safeProviderType);
        SmsProviderRateLimit rateLimit = lockedRow.orElse(null);
        if (rateLimit == null) {
            return false;
        }
        // A caller may have waited through a window rollover. Use the time after locking so an old
        // timestamp cannot be mistaken for a backward clock change and reset the committed count.
        boolean acquired = rateLimit.tryAcquire(Date.from(clock.instant()), maxSendsPerWindow, window);
        if (acquired) {
            rateLimitDao.merge(rateLimit);
            rateLimitDao.flush();
        }
        return acquired;
    }

    private static Duration safeWindow(Duration window) {
        if (window == null || window.isNegative() || window.isZero()) {
            return DEFAULT_WINDOW;
        }
        return window.toMillis() < 1 ? Duration.ofMillis(1) : window;
    }
}
