package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.sms.event.SmsConfigChangedEvent;
import io.github.carlos_emr.carlos.utility.DeamonThreadFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class SmsQueueScheduler {
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final String ENABLED_PROPERTY = "sms.queue.scheduler.enabled";
    private static final String INTERVAL_SECONDS_PROPERTY = "sms.queue.scheduler.intervalSeconds";
    private static final String BATCH_SIZE_PROPERTY = "sms.queue.scheduler.batchSize";
    private static final long DEFAULT_INTERVAL_SECONDS = 60;
    // Default queued rows to ask the worker to process per scheduler run; override via sms.queue.scheduler.batchSize.
    private static final int DEFAULT_BATCH_SIZE = 60;

    private final SmsQueueProcessingService smsQueueWorker;
    private final SmsConfigService configService;
    private ScheduledExecutorService executorService;

    public SmsQueueScheduler(SmsQueueProcessingService smsQueueWorker) {
        this(smsQueueWorker, null);
    }

    @Autowired
    public SmsQueueScheduler(SmsQueueProcessingService smsQueueWorker, SmsConfigService configService) {
        this.smsQueueWorker = smsQueueWorker;
        this.configService = configService;
    }

    /** @return whether the scheduler is running in this server right now */
    public synchronized boolean isRunning() {
        return executorService != null;
    }

    /**
     * Applies a saved Administration &gt; SMS scheduler setting without a restart. Runs after the save
     * commits (or at once outside a transaction), and only affects this server; other servers pick the
     * setting up when they start.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfigChanged(SmsConfigChangedEvent event) {
        if (event.schedulerEnabled()) {
            startExecutor();
        } else {
            stop();
        }
    }

    @PostConstruct
    public void start() {
        if (!schedulerEnabled()) {
            // Make the dependency visible: queued work (including direct sends deferred by the rate
            // limiter and scheduled retries) is only drained by this scheduler or by enqueueAndProcessNow.
            LOGGER.info(
                    "SMS queue scheduler is off (Administration > SMS, or {} while nothing is saved there); "
                            + "queued and rate-limited SMS will not be drained automatically. Turn it on before "
                            + "relying on queued/retried delivery.",
                    ENABLED_PROPERTY
            );
            return;
        }
        startExecutor();
    }

    private synchronized void startExecutor() {
        if (executorService != null) {
            return;
        }
        LOGGER.info(
                "SMS queue scheduler enabled; polling every {}s with batch size {}.",
                intervalSeconds(),
                batchSize()
        );
        executorService = Executors.newSingleThreadScheduledExecutor(
                new DeamonThreadFactory(SmsQueueScheduler.class.getSimpleName(), Thread.NORM_PRIORITY)
        );
        executorService.scheduleWithFixedDelay(
                this::runSafely,
                intervalSeconds(),
                intervalSeconds(),
                TimeUnit.SECONDS
        );
    }

    @PreDestroy
    public synchronized void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    public int runOnce() {
        return smsQueueWorker.processDueMessages(batchSize());
    }

    private void runSafely() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            LOGGER.warn("SMS queue scheduler run failed; exceptionClass={}", exceptionClass(e));
        }
    }

    /**
     * The setting saved in Administration &gt; SMS, or {@code sms.queue.scheduler.enabled} while nothing is
     * saved or the settings cannot be read at startup.
     */
    private boolean schedulerEnabled() {
        Optional<Boolean> stored = Optional.empty();
        if (configService != null) {
            try {
                stored = configService.storedSchedulerEnabled();
            } catch (RuntimeException e) {
                LOGGER.warn("SMS settings could not be read at startup; using {}. exceptionClass={}",
                        ENABLED_PROPERTY, exceptionClass(e));
            }
        }
        return stored.orElseGet(() -> CarlosProperties.getInstance().isPropertyActive(ENABLED_PROPERTY));
    }

    private long intervalSeconds() {
        return Math.max(1, longProperty(INTERVAL_SECONDS_PROPERTY, DEFAULT_INTERVAL_SECONDS));
    }

    private int batchSize() {
        long configuredBatchSize = longProperty(BATCH_SIZE_PROPERTY, DEFAULT_BATCH_SIZE);
        return Math.clamp(configuredBatchSize, 1, Integer.MAX_VALUE);
    }

    private long longProperty(String propertyName, long defaultValue) {
        String value = CarlosProperties.getInstance().getProperty(propertyName, Long.toString(defaultValue));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String exceptionClass(RuntimeException e) {
        return e == null ? "unknown" : e.getClass().getName();
    }
}
