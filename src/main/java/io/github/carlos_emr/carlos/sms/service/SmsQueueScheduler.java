package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.DeamonThreadFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

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

    private final SmsQueueWorker smsQueueWorker;
    private ScheduledExecutorService executorService;

    public SmsQueueScheduler(SmsQueueWorker smsQueueWorker) {
        this.smsQueueWorker = smsQueueWorker;
    }

    @PostConstruct
    public void start() {
        if (!schedulerEnabled()) {
            return;
        }
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
    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
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

    private boolean schedulerEnabled() {
        return CarlosProperties.getInstance().isPropertyActive(ENABLED_PROPERTY);
    }

    private long intervalSeconds() {
        return Math.max(1, longProperty(INTERVAL_SECONDS_PROPERTY, DEFAULT_INTERVAL_SECONDS));
    }

    private int batchSize() {
        return Math.max(1, (int) longProperty(BATCH_SIZE_PROPERTY, DEFAULT_BATCH_SIZE));
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
