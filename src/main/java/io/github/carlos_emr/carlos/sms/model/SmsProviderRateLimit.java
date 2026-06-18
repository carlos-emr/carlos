package io.github.carlos_emr.carlos.sms.model;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.time.Duration;
import java.util.Date;

@Entity
@Table(name = "sms_provider_rate_limit")
public class SmsProviderRateLimit extends AbstractModel<SmsProviderType> {
    private static final Duration DEFAULT_WINDOW = Duration.ofSeconds(5);

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 16)
    private SmsProviderType providerType = SmsProviderType.STUB;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "window_started_at", nullable = false)
    private Date windowStartedAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt = new Date();

    public SmsProviderRateLimit() {
        // Required by JPA.
    }

    public static SmsProviderRateLimit forProvider(SmsProviderType providerType, Date now) {
        SmsProviderRateLimit rateLimit = new SmsProviderRateLimit();
        rateLimit.providerType = providerType == null ? SmsProviderType.STUB : providerType;
        Date safeNow = copyOf(now);
        if (safeNow == null) {
            safeNow = new Date();
        }
        rateLimit.windowStartedAt = safeNow;
        rateLimit.createdAt = copyOf(safeNow);
        rateLimit.updatedAt = copyOf(safeNow);
        return rateLimit;
    }

    public boolean tryAcquire(Date now, int maxSendsPerWindow, Duration window) {
        Date safeNow = copyOf(now);
        if (safeNow == null) {
            safeNow = new Date();
        }
        int safeMaxSendsPerWindow = Math.max(1, maxSendsPerWindow);
        long safeWindowMillis = safeWindowMillis(window);

        long elapsedMillis = windowStartedAt == null ? safeWindowMillis : safeNow.getTime() - windowStartedAt.getTime();
        if (windowStartedAt == null || elapsedMillis < 0 || elapsedMillis >= safeWindowMillis) {
            windowStartedAt = copyOf(safeNow);
            sendCount = 1;
            touch(safeNow);
            return true;
        }

        if (sendCount >= safeMaxSendsPerWindow) {
            return false;
        }

        sendCount++;
        touch(safeNow);
        return true;
    }

    @Override
    public SmsProviderType getId() {
        return providerType;
    }

    public SmsProviderType getProviderType() {
        return providerType;
    }

    public int getSendCount() {
        return sendCount;
    }

    public Date getWindowStartedAt() {
        return copyOf(windowStartedAt);
    }

    public Date getCreatedAt() {
        return copyOf(createdAt);
    }

    public Date getUpdatedAt() {
        return copyOf(updatedAt);
    }

    @PrePersist
    void prePersist() {
        Date now = new Date();
        if (createdAt == null) {
            createdAt = copyOf(now);
        }
        if (updatedAt == null) {
            updatedAt = copyOf(now);
        }
        if (windowStartedAt == null) {
            windowStartedAt = copyOf(now);
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = new Date();
    }

    private void touch(Date now) {
        updatedAt = copyOf(now);
    }

    private static long safeWindowMillis(Duration window) {
        Duration safeWindow = window == null || window.isNegative() || window.isZero()
                ? DEFAULT_WINDOW
                : window;
        return Math.max(1, safeWindow.toMillis());
    }

    private static Date copyOf(Date date) {
        return date == null ? null : new Date(date.getTime());
    }
}
