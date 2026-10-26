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
import java.util.concurrent.ScheduledFuture;
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
    // One single-thread executor for the scheduler's whole life, so runs can never overlap. Turning the
    // scheduler off cancels the schedule, not the executor.
    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> schedule;
    // Set at application shutdown. A settings save that commits afterwards must not start a thread
    // that nothing would ever stop.
    private boolean shutDown;
    // Held while a settings change reads the saved setting and applies it, so two changes cannot read in
    // one order and apply in the other. Separate from this object's monitor so isRunning() never waits
    // for the database.
    private final Object settingsChangeLock = new Object();

    /** For tests: no stored settings, so the scheduler follows the property. */
    SmsQueueScheduler(SmsQueueProcessingService smsQueueWorker) {
        this(smsQueueWorker, null);
    }

    @Autowired
    public SmsQueueScheduler(SmsQueueProcessingService smsQueueWorker, SmsConfigService configService) {
        this.smsQueueWorker = smsQueueWorker;
        this.configService = configService;
    }

    /** @return whether the scheduler is running in this server right now */
    public synchronized boolean isRunning() {
        // A schedule that has ended (a run failed in a way runSafely could not absorb) is not running,
        // whatever was asked for. The next settings save or restart starts it again.
        return schedule != null && !schedule.isDone();
    }

    /**
     * Applies a saved Administration &gt; SMS scheduler setting without a restart. Runs after the save
     * commits (or at once outside a transaction), and only affects this server; other servers pick the
     * setting up when they start.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfigChanged(SmsConfigChangedEvent event) {
        // Two saves close together can reach this listener in the opposite order to their commits, so the
        // committed setting decides, not the event. The event's value is used only if it cannot be read.
        synchronized (settingsChangeLock) {
            boolean enabled = event.schedulerEnabled();
            if (configService != null) {
                try {
                    enabled = configService.committedSchedulerEnabled().orElse(enabled);
                } catch (RuntimeException e) {
                    LOGGER.warn("SMS settings could not be re-read after a save; using the value from the save. "
                            + "exceptionClass={}", exceptionClass(e));
                }
            }
            if (enabled) {
                startExecutor();
            } else {
                stopAfterCurrentRun();
            }
        }
    }

    /**
     * Stops scheduling further runs but lets a run that is in progress finish. Interrupting it could cut
     * in between the SMS provider accepting a message and CARLOS recording that, leaving the message
     * marked as sending with its outcome unknown.
     */
    private synchronized void stopAfterCurrentRun() {
        if (schedule != null) {
            schedule.cancel(false);
            schedule = null;
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
        if (shutDown || (schedule != null && !schedule.isDone())) {
            return;
        }
        LOGGER.info(
                "SMS queue scheduler enabled; polling every {}s with batch size {}.",
                intervalSeconds(),
                batchSize()
        );
        if (executorService == null) {
            executorService = Executors.newSingleThreadScheduledExecutor(
                    new DeamonThreadFactory(SmsQueueScheduler.class.getSimpleName(), Thread.NORM_PRIORITY)
            );
        }
        schedule = executorService.scheduleWithFixedDelay(
                this::runSafely,
                intervalSeconds(),
                intervalSeconds(),
                TimeUnit.SECONDS
        );
    }

    /**
     * Stops at once, interrupting a run in progress. For application shutdown: it is final, and the
     * scheduler cannot be started again afterwards.
     */
    @PreDestroy
    public synchronized void stop() {
        shutDown = true;
        schedule = null;
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    /**
     * Drains one batch of due messages, unless SMS is turned off in Administration &gt; SMS: then queued
     * messages stay queued (not failed) and go out once it is turned back on.
     */
    public int runOnce() {
        if (configService != null && !configService.sendingEnabled()) {
            return 0;
        }
        return smsQueueWorker.processDueMessages(batchSize());
    }

    private void runSafely() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            LOGGER.warn("SMS queue scheduler run failed; exceptionClass={}", exceptionClass(e));
        } catch (Error e) {
            // An Error that escapes ends a fixed-delay schedule for good, silently. Log it and carry on
            // with the next run; the class name only, since a message could quote data.
            LOGGER.error("SMS queue scheduler run failed with an error; exceptionClass={}", e.getClass().getName());
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
