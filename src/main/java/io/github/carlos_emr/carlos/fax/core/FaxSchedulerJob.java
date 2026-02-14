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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;

/**
 * Spring-managed scheduler for fax polling and status processing cycles.
 *
 * <p>The scheduler executes inbound import, outbound send, and outbound status update in a single
 * cycle at the configured poll interval. Runtime telemetry is captured for admin diagnostics.</p>
 */
@Component
public class FaxSchedulerJob {
    private static final Logger logger = MiscUtils.getLogger();

    private static final String FAX_POLL_INTERVAL_KEY = "faxPollInterval";
    private static final long DEFAULT_PERIOD_MS = 60000L;
    private static final long FAILURE_RESTART_DELAY_MS = 300000L;
    private static final int MAX_AUTO_RESTART_ATTEMPTS = 10;

    private final FaxImporter faxImporter;
    private final FaxSender faxSender;
    private final FaxStatusUpdater faxStatusUpdater;
    private final FaxConfigDao faxConfigDao;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastSuccessfulRunEpochMs = new AtomicLong(0L);
    private final AtomicReference<String> lastError = new AtomicReference<>("");
    private final AtomicInteger autoRestartAttempts = new AtomicInteger(0);

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
     * (database connection drops, network glitches), this method automatically retries after
     * {@link #FAILURE_RESTART_DELAY_MS} (5 minutes). If restart fails, it attempts to schedule
     * another retry. If scheduling itself fails, it logs a CRITICAL error and updates
     * {@link #getLastError()} so the admin UI can surface the failure state.</p>
     *
     * <p>Admin visibility: Failure state is exposed via {@link #getLastError()} and
     * {@link #isRunning()} for monitoring dashboards.</p>
     *
     * <p><strong>Note:</strong> OutOfMemoryError failures do NOT trigger automatic restart
     * (see exception handling in {@code runCycle()}) because the system is in a bad state
     * requiring manual intervention.</p>
     */
    private synchronized void scheduleAutomaticRestart() {
        if (autoRestartAttempts.incrementAndGet() > MAX_AUTO_RESTART_ATTEMPTS) {
            logger.error("CRITICAL: Max restart attempts ({}) exceeded - fax scheduler requires manual restart via admin UI",
                    MAX_AUTO_RESTART_ATTEMPTS);
            lastError.set("Max restart attempts (" + MAX_AUTO_RESTART_ATTEMPTS + ") exceeded - manual restart required");
            return;
        }

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
                    logger.error("URGENT: attempting automatic fax scheduler restart after failure");
                    restartTask();
                } catch (Exception ex) {
                    logger.error("URGENT: automatic fax scheduler restart attempt failed - will retry in {} ms",
                            FAILURE_RESTART_DELAY_MS, ex);
                    try {
                        scheduleAutomaticRestart();
                    } catch (Exception schedulingEx) {
                        logger.error("CRITICAL: Failed to schedule automatic restart - fax scheduler requires manual restart", schedulingEx);
                        lastError.set("Auto-restart scheduling failed - manual restart required");
                    }
                }
            }
        };

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

        String faxPollInterval = (String) OscarProperties.getInstance().get(FAX_POLL_INTERVAL_KEY);
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
