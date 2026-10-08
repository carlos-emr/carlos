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
import java.util.function.Function;

@Primary
@Service
public class JpaSmsSendRateLimitService implements SmsSendRateLimitService {
    private final SmsProviderRateLimitDao rateLimitDao;
    private final Function<SmsProviderType, SmsSendRateLimit> limits;
    private final Clock clock;

    /** Each provider is held to the limit its client states; a provider with no client gets the default. */
    @Autowired
    public JpaSmsSendRateLimitService(SmsProviderRateLimitDao rateLimitDao, SmsProviderClientResolver providerClients) {
        this(rateLimitDao, providerType -> providerClients.registeredProviderTypes().contains(providerType)
                ? providerClients.resolve(providerType).sendRateLimit()
                : SmsSendRateLimit.DEFAULT, Clock.systemUTC());
    }

    /**
     * For tests: one limit for every provider. A count below 1 means 1; a missing, zero or negative window means
     * the default window, and a positive one under a millisecond means one millisecond.
     */
    JpaSmsSendRateLimitService(
            SmsProviderRateLimitDao rateLimitDao,
            int maxSendsPerWindow,
            Duration window,
            Clock clock
    ) {
        this(rateLimitDao, fixed(maxSendsPerWindow, window), clock);
    }

    JpaSmsSendRateLimitService(
            SmsProviderRateLimitDao rateLimitDao,
            Function<SmsProviderType, SmsSendRateLimit> limits,
            Clock clock
    ) {
        this.rateLimitDao = Objects.requireNonNull(rateLimitDao, "rateLimitDao is required");
        this.limits = Objects.requireNonNull(limits, "SMS send rate limits are required");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    private static Function<SmsProviderType, SmsSendRateLimit> fixed(int maxSendsPerWindow, Duration window) {
        SmsSendRateLimit limit = new SmsSendRateLimit(Math.max(1, maxSendsPerWindow), safeWindow(window));
        return providerType -> limit;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(SmsProviderType providerType) {
        SmsProviderType safeProviderType = providerType == null ? SmsProviderType.STUB : providerType;
        SmsSendRateLimit limit = Objects.requireNonNull(limits.apply(safeProviderType), "SMS send rate limit is required");
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
        boolean acquired = rateLimit.tryAcquire(Date.from(clock.instant()), limit.maxSends(), limit.window());
        if (acquired) {
            rateLimitDao.merge(rateLimit);
            rateLimitDao.flush();
        }
        return acquired;
    }

    private static Duration safeWindow(Duration window) {
        if (window == null || window.isNegative() || window.isZero()) {
            return SmsSendRateLimit.DEFAULT.window();
        }
        return window.toMillis() < 1 ? Duration.ofMillis(1) : window;
    }
}
