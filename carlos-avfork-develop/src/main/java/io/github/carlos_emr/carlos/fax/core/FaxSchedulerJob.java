/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Copyright (c) 2017-2024. Juno EMR. All Rights Reserved.
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * Originally written for the Department of Family Medicine, McMaster University.
 * Portions contributed by Juno EMR.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.fax.core;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;

/**
 * Spring-managed scheduler for fax polling and status processing cycles.
 *
 * <p>Each cycle executes three phases in order:</p>
 * <ol>
 *   <li><strong>Import:</strong> {@link FaxImporter#poll()} - download and import inbound faxes</li>
 *   <li><strong>Send:</strong> {@link FaxSender#send()} - transmit queued outbound faxes</li>
 *   <li><strong>Status:</strong> {@link FaxStatusUpdater#updateStatus()} - poll provider for delivery status</li>
 * </ol>
 *
 * <p><strong>Scheduling:</strong> Poll interval is configurable via the {@code faxPollInterval}
 * property (milliseconds, default 60000ms / 60 seconds). First cycle starts 3 seconds after bean initialization.
 * The scheduler only starts if at least one active FaxConfig exists in the database.</p>
 *
 * <p><strong>Auto-Restart:</strong> On RuntimeException, the scheduler cancels and retries after
 * a fixed 10-minute delay with no maximum attempt cap. OOM and other JVM Errors are excluded from
 * auto-restart to prevent crash loops. Admin visibility is provided via {@link #isRunning()},
 * {@link #getLastSuccessfulRunEpochMs()}, and {@link #getLastError()}.</p>
 *
 * @see FaxImporter
 * @see FaxSender
 * @see FaxStatusUpdater
 * @since 2014-08-29
 */
@Component
public class FaxSchedulerJob {
    private static final Logger logger = MiscUtils.getLogger();

    private static final String FAX_POLL_INTERVAL_KEY = "faxPollInterval";
    private static final long DEFAULT_PERIOD_MS = 60000L;
    // Intentional design: fixed 10-minute retry interval with no maximum attempt cap.
    // The fax scheduler is critical infrastructure and must keep trying to recover from
    // transient failures (database connection drops, network glitches) indefinitely.
    // OOM and JVM errors are excluded from auto-restart (see runCycle exception handling).
    // Each retry is logged at ERROR level so admins can monitor via log aggregation.
    private static final long FAILURE_RESTART_DELAY_MS = 600000L;

    private final FaxImporter faxImporter;
    private final FaxSender faxSender;
    private final FaxStatusUpdater faxStatusUpdater;
    private final FaxConfigDao faxConfigDao;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastSuccessfulRunEpochMs = new AtomicLong(0L);
    private final AtomicReference<String> lastError = new AtomicReference<>("");
    private final AtomicLong autoRestartAttempts = new AtomicLong(0);

    private Timer timer;
    private TimerTask timerTask;
    private TimerTask autoRestartTask;

    /**
     * Creates scheduler job with injected fax workflow services.
     *
     * @param faxImporter FaxImporter inbound import service
     * @param faxSender FaxSender outbound send service
     * @param faxStatusUpdater FaxStatusUpdater outbound status polling service
     * @param faxConfigDao FaxConfigDao DAO for checking active fax configurations
     */
    @Autowired
    public FaxSchedulerJob(FaxImporter faxImporter, FaxSender faxSender, FaxStatusUpdater faxStatusUpdater,
            FaxConfigDao faxConfigDao) {
        this.faxImporter = faxImporter;
        this.faxSender = faxSender;
        this.faxStatusUpdater = faxStatusUpdater;
        this.faxConfigDao = faxConfigDao;
    }

    /**
     * Starts scheduler polling after bean initialization if active fax configs exist.
     */
    @PostConstruct
    public void initialize() {
        if (hasActiveFaxConfigs()) {
            startTask();
        } else {
            logger.info("No active fax accounts configured - scheduler will start when a fax account is activated");
        }
    }

    /**
     * Checks whether any active fax configurations exist in the database.
     *
     * @return true if at least one FaxConfig has active=true
     */
    private boolean hasActiveFaxConfigs() {
        try {
            List<FaxConfig> configs = faxConfigDao.findAll(null, null);
            return configs.stream().anyMatch(FaxConfig::isActive);
        } catch (Exception e) {
            logger.error("Failed to check fax configurations at startup - scheduler will NOT start. "
                    + "It will start automatically when a fax account is activated via admin UI.", e);
            return false;
        }
    }

    private void runCycle() {
        try {
            faxImporter.poll();
            faxSender.send();
            faxStatusUpdater.updateStatus();
            lastSuccessfulRunEpochMs.set(System.currentTimeMillis());
            lastError.set("");
            running.set(true);
        } catch (OutOfMemoryError e) {
            // DO NOT attempt restart for OOM - system is in bad state.
            // Auto-restart would likely trigger another OOM, creating a crash loop.
            // Require manual intervention to investigate root cause (memory leak,
            // undersized heap, excessively large fax documents).
            cancelTask();
            lastError.set("OUT OF MEMORY - manual intervention required");
            logger.error("CRITICAL: Fax scheduler stopped due to out of memory - DO NOT RESTART AUTOMATICALLY", e);
            running.set(false);
            // Do NOT schedule automatic restart for OOM
        } catch (Error e) {
            // Non-OOM JVM errors (StackOverflowError, NoClassDefFoundError, etc.)
            // Treat as non-recoverable like OOM - do not attempt automatic restart
            cancelTask();
            lastError.set(e.getClass().getSimpleName() + " - manual intervention required");
            logger.error("CRITICAL: Fax scheduler stopped due to JVM error - DO NOT RESTART AUTOMATICALLY", e);
            running.set(false);
        } catch (RuntimeException e) {
            // Programming errors - restart might help if transient
            cancelTask();
            lastError.set(e.getClass().getSimpleName() + ": " + e.getMessage());
            logger.error("URGENT: Fax scheduler stopped due to runtime exception - attempting automatic restart", e);
            running.set(false);
            scheduleAutomaticRestart();
        } catch (Exception e) {
            // Checked exceptions (shouldn't happen in this flow, but catch anyway)
            cancelTask();
            lastError.set(e.getClass().getSimpleName() + ": " + e.getMessage());
            logger.error("URGENT: Fax scheduler stopped due to unexpected checked exception - attempting automatic restart", e);
            running.set(false);
            scheduleAutomaticRestart();
        }
    }

    /**
     * Schedules an automatic restart attempt after a fatal scheduler failure.
     *
     * <p><strong>Design Rationale:</strong> The scheduler is critical infrastructure for fax
     * operations. Rather than requiring manual admin intervention for transient failures
     * (database connection drops, network glitches), this method automatically retries at
     * fixed 10-minute intervals indefinitely. There is intentionally no maximum attempt cap --
     * the scheduler must keep trying to recover because clinics depend on fax delivery.
     * OOM and JVM errors are excluded from auto-restart (see runCycle exception handling).</p>
     *
     * <p>Admin visibility: Failure state and attempt count are exposed via {@link #getLastError()}
     * and {@link #isRunning()} for monitoring dashboards. Each retry is logged at ERROR level.</p>
     */
    private synchronized void scheduleAutomaticRestart() {
        long attempt = autoRestartAttempts.incrementAndGet();

        if (timer == null) {
            timer = new Timer("FaxSchedulerJob Recovery Timer", true);
        }
        if (autoRestartTask != null) {
            autoRestartTask.cancel();
        }

        autoRestartTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    logger.error("URGENT: attempting automatic fax scheduler restart (attempt {})", attempt);
                    restartTask();
                } catch (Exception ex) {
                    logger.error("URGENT: automatic fax scheduler restart attempt {} failed - will retry in {} ms",
                            attempt, FAILURE_RESTART_DELAY_MS, ex);
                    try {
                        scheduleAutomaticRestart();
                    } catch (Exception schedulingEx) {
                        logger.error("CRITICAL: Failed to schedule automatic restart (attempt {}) - fax scheduler requires manual restart",
                                attempt, schedulingEx);
                        lastError.set("Auto-restart scheduling failed at attempt " + attempt + " - manual restart required");
                    }
                }
            }
        };

        logger.error("Fax scheduler auto-restart scheduled: attempt {} in {} ms", attempt, FAILURE_RESTART_DELAY_MS);
        timer.schedule(autoRestartTask, FAILURE_RESTART_DELAY_MS);
    }

    /**
     * Cancels any existing timer and starts a new scheduler task.
     */
    public synchronized void restartTask() {
        cancelTask();
        startTask();
    }

    /**
     * Starts the scheduler if it is not already running. Called by admin actions
     * when a fax account is activated for the first time.
     */
    public synchronized void startIfNotRunning() {
        if (!running.get()) {
            logger.info("Starting fax scheduler on demand (triggered by fax account activation)");
            startTask();
        }
    }

    private synchronized void startTask() {
        if (timerTask != null && running.get()) {
            return;
        }

        String faxPollInterval = (String) CarlosProperties.getInstance().get(FAX_POLL_INTERVAL_KEY);
        long period = DEFAULT_PERIOD_MS;
        try {
            period = Long.parseLong(faxPollInterval);
        } catch (NumberFormatException e) {
            logger.warn("FaxSchedulerJob period is missing or invalid in properties file: {}={} - using default: {} ms",
                    FAX_POLL_INTERVAL_KEY, faxPollInterval, DEFAULT_PERIOD_MS);
        }
        if (period <= 0) {
            logger.error("FaxSchedulerJob period must be positive, got {}. Using default: {} ms", period, DEFAULT_PERIOD_MS);
            period = DEFAULT_PERIOD_MS;
        }

        timerTask = new TimerTask() {
            @Override
            public void run() {
                runCycle();
            }
        };

        if (timer == null) {
            timer = new Timer("FaxSchedulerJob Timer", true);
        }
        if (autoRestartTask != null) {
            autoRestartTask.cancel();
            autoRestartTask = null;
        }

        timer.schedule(timerTask, 3000, period);
        running.set(true);
        autoRestartAttempts.set(0);
    }

    /**
     * Cancels timer resources during bean shutdown or restart.
     */
    @PreDestroy
    synchronized void cancelTask() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (autoRestartTask != null) {
            autoRestartTask.cancel();
            autoRestartTask = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        running.set(false);
    }

    /**
     * Returns whether the scheduler timer task is currently active.
     *
     * <p>Note: Returns true as soon as the timer is scheduled, even before the first
     * cycle completes. Check {@link #getLastSuccessfulRunEpochMs()} to confirm at least
     * one cycle has completed successfully.</p>
     *
     * @return true when scheduler timer task is currently active
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * @return epoch milliseconds for the last successful execution cycle.
     */
    public long getLastSuccessfulRunEpochMs() {
        return lastSuccessfulRunEpochMs.get();
    }

    /**
     * @return most recent fatal scheduler error text, or empty string when healthy.
     */
    public String getLastError() {
        return lastError.get();
    }
}
