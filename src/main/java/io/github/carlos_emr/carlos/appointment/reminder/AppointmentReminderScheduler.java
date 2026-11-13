/**
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.appointment.reminder;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs {@link AppointmentReminderService#processDue} every few minutes on one thread (#5887), as
 * {@code SmsQueueScheduler} runs the SMS queue. Off unless the clinic turns appointment reminders on. Each
 * server may run it: the service claims every reminder for one run only.
 *
 * <p>The switch is {@code emailApptReminder}, the property that already shows the box (#5887).
 *
 * @since 2026-11-12
 */
@Service
public class AppointmentReminderScheduler {

    static final String ENABLED_PROPERTY = "emailApptReminder";
    static final long INTERVAL_SECONDS = 300;
    static final int BATCH_SIZE = 50;
    /**
     * How long stopping waits for a run: it stops between reminders once interrupted, so this covers recording
     * the one it is sending. A run still busy after it leaves that reminder to the stale-claim recovery.
     */
    static final long STOP_WAIT_SECONDS = 10;

    private static final Logger logger = MiscUtils.getLogger();

    private final AppointmentReminderService reminders;
    private ScheduledExecutorService executor;

    public AppointmentReminderScheduler(AppointmentReminderService reminders) {
        this.reminders = Objects.requireNonNull(reminders, "reminders");
    }

    @PostConstruct
    public synchronized void start() {
        // Either channel's switch starts the job; each channel then sends only while its own is on (#884).
        if (executor != null || !(CarlosProperties.getInstance().isPropertyActive(ENABLED_PROPERTY)
                || CarlosProperties.getInstance().isPropertyActive(SmsReminderChannel.ENABLED_PROPERTY))) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "carlos-appointment-reminders");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::runSafely, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("Appointment reminders run every {} seconds", INTERVAL_SECONDS);
    }

    @PreDestroy
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(STOP_WAIT_SECONDS, TimeUnit.SECONDS)) {
                    logger.warn("Appointment reminder run still busy after {} seconds; stopping without it",
                            STOP_WAIT_SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    synchronized boolean isRunning() {
        return executor != null;
    }

    /** One run. A failure is logged by class only (no patient data) and the schedule carries on. */
    void runSafely() {
        try {
            ReminderRun run = reminders.processDue(BATCH_SIZE);
            if (run.claimed() > 0) {
                logger.info("Appointment reminders: {} claimed, {} sent, {} not sent", run.claimed(), run.sent(),
                        run.notSent());
            }
        } catch (RuntimeException e) {
            logger.error("Appointment reminder run failed ({})", e.getClass().getName());
        }
    }
}
