/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.fax.core;

import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.PreDestroy;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.OscarProperties;

@Component
public class FaxSchedulerJob extends TimerTask {
    private static final Logger logger = MiscUtils.getLogger();

    /*
     * This Java code defines a static variable 'isRunning' along with static getter and setter methods within the FaxSchedulerJob class.
     * This variable is used to determine whether the fax scheduler is currently running or not.
     * By utilizing a static variable and associated static methods, it allows for checking the scheduler status without the need for creating instances of the FaxSchedulerJob class.
     * To check the scheduler status, one can simply invoke the static method 'isRunning()' using the class name, like so: FaxSchedulerJob.isRunning().
     */
    private static boolean isRunning = false;

    private static final String FAX_POLL_INTERVAL_KEY = "faxPollInterval";
    private static Timer timer;
    private static TimerTask timerTask = null;

    @Override
    public void run() {
        try {
            FaxImporter faxImporter = new FaxImporter();
            faxImporter.poll();

            FaxSender faxSender = new FaxSender();
            faxSender.send();

            FaxStatusUpdater faxStatusUpdater = new FaxStatusUpdater();
            faxStatusUpdater.updateStatus();

            setRunning(true);
        } catch (Exception e) {
            /*
             * Catching any unexpected exception thrown by these methods here and logging it.
             * Also, cancelling the thread so that an admin has to manually reboot the scheduler
             * or restart the system to start the task again. Doing this because it would not be good
             * to keep running the thread even after it throws an unknown error, which may lead to the
             * fax scheduler going haywire.
             */
            cancel();
            logger.error("Fax scheduler has been stopped due to an unexpected error", e);
            setRunning(false);

            /*
             * Rethrowing the caught exception to ensure termination of the thread.
             */
            throw e;
        }
    }

    public synchronized void restartTask() {
        cancelTask();
        startTask();
    }

    private synchronized void startTask() {
        if (isRunning) {
            return;
        }
        String faxPollInterval = (String) OscarProperties.getInstance().get(FAX_POLL_INTERVAL_KEY);
        long period = 60000;
        try {
            period = Long.parseLong(faxPollInterval);
        } catch (Exception e) {
            logger.error("FaxSchedularJob not scheduled, period is missing or invalid in properties file : " + FAX_POLL_INTERVAL_KEY + ": " + faxPollInterval, e);
            logger.error("Setting period to default: 60000 ms");
        }
        timerTask = new FaxSchedulerJob();
        timer = new Timer("FaxSchedulerJob Timer");
        timer.schedule(timerTask, 3000, period);
    }

    @PreDestroy
    private synchronized void cancelTask() {
        if (timerTask != null) {
            timerTask.cancel();
            timer.cancel();
        } else {
            this.cancel();
        }
        setRunning(false);
    }

    public static Boolean isRunning() {
        return isRunning;
    }

    public static void setRunning(Boolean isFaxSchedulerRunning) {
        isRunning = isFaxSchedulerRunning;
    }

}
