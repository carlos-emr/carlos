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

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drains the outbound SMS queue on a fixed delay in this server, when the Administration &gt; SMS setting (or,
 * while nothing is saved, {@value #ENABLED_PROPERTY}) turns it on.
 * <p>
 * It also remembers, in memory, when its last run on this server started and how the last finished run ended,
 * for the Administration &gt; SMS queue view. That record is per server and starts empty at every restart.
 */
@Service
public class SmsQueueScheduler {
    private static final Logger LOGGER = MiscUtils.getLogger();
    /** The property the scheduler follows while no Administration &gt; SMS setting is saved. */
    public static final String ENABLED_PROPERTY = "sms.queue.scheduler.enabled";
    private static final String INTERVAL_SECONDS_PROPERTY = "sms.queue.scheduler.intervalSeconds";
    private static final String BATCH_SIZE_PROPERTY = "sms.queue.scheduler.batchSize";
    private static final long DEFAULT_INTERVAL_SECONDS = 60;
    // Default queued rows to ask the worker to process per scheduler run; override via sms.queue.scheduler.batchSize.
    private static final int DEFAULT_BATCH_SIZE = 60;

    private final SmsQueueProcessingService smsQueueWorker;
    private final SmsConfigService configService;
    private final Clock clock;
    private final AtomicInteger activeRuns = new AtomicInteger();
    // One single-thread executor for the scheduler's whole life, so runs can never overlap. Turning the
    // scheduler off cancels the schedule, not the executor.
    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> schedule;
    // Written by the scheduler thread (or a direct runOnce caller) and read by the admin page's request thread.
    private volatile Instant lastRunStartedAt;
    private volatile CompletedRun lastCompletedRun;

    /** How a queue run on this server ended. */
    public enum RunOutcome {
        /** The worker processed the due messages (possibly none). */
        COMPLETED,
        /** Sending is turned off in Administration &gt; SMS, so the run left the queue alone. */
        SENDING_OFF,
        /** The run threw; on the scheduler thread, {@code runSafely} logs the exception class. */
        FAILED
    }

    /**
     * The last queue run on this server that finished.
     *
     * @param startedAt  when it started
     * @param finishedAt when it finished
     * @param outcome    how it ended
     * @param processed  messages the worker processed (sent or consent-blocked); 0 unless {@code COMPLETED}
     */
    public record CompletedRun(Instant startedAt, Instant finishedAt, RunOutcome outcome, int processed) {
    }

    /** For tests: no stored settings, so the scheduler follows the property. */
    SmsQueueScheduler(SmsQueueProcessingService smsQueueWorker) {
        this(smsQueueWorker, null);
    }

    @Autowired
    public SmsQueueScheduler(SmsQueueProcessingService smsQueueWorker, SmsConfigService configService) {
        this(smsQueueWorker, configService, Clock.systemUTC());
    }

    /** For tests: a fixed clock makes the recorded run times predictable. */
    SmsQueueScheduler(SmsQueueProcessingService smsQueueWorker, SmsConfigService configService, Clock clock) {
        this.smsQueueWorker = smsQueueWorker;
        this.configService = configService;
        this.clock = clock;
    }

    /** @return whether the scheduler is running in this server right now */
    public synchronized boolean isRunning() {
        return schedule != null;
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
            stopAfterCurrentRun();
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
        if (schedule != null) {
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

    /** Stops at once, interrupting a run in progress. For application shutdown. */
    @PreDestroy
    public synchronized void stop() {
        schedule = null;
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    /**
     * Drains one batch of due messages, unless SMS is turned off in Administration &gt; SMS: then queued
     * messages stay queued (not failed) and go out once it is turned back on.
     * <p>
     * Every call is recorded for the queue view: its start time at once, and its end time, outcome and
     * processed count when it returns or throws.
     */
    public int runOnce() {
        Instant startedAt = clock.instant();
        lastRunStartedAt = startedAt;
        activeRuns.incrementAndGet();
        RunOutcome outcome = RunOutcome.FAILED;
        int processed = 0;
        try {
            if (configService != null && !configService.sendingEnabled()) {
                outcome = RunOutcome.SENDING_OFF;
                return 0;
            }
            processed = smsQueueWorker.processDueMessages(batchSize());
            outcome = RunOutcome.COMPLETED;
            return processed;
        } finally {
            // Publish the finished run before the in-progress count drops, so a reader that sees no run in
            // progress also sees this run's result.
            lastCompletedRun = new CompletedRun(startedAt, clock.instant(), outcome, processed);
            activeRuns.decrementAndGet();
        }
    }

    /** @return when the most recent queue run on this server started (it may still be going); empty before the first */
    public Optional<Instant> lastRunStartedAt() {
        return Optional.ofNullable(lastRunStartedAt);
    }

    /** @return the last queue run on this server that finished; empty before the first finishes */
    public Optional<CompletedRun> lastCompletedRun() {
        return Optional.ofNullable(lastCompletedRun);
    }

    /** @return whether a queue run is going on in this server right now */
    public boolean isRunInProgress() {
        return activeRuns.get() > 0;
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
