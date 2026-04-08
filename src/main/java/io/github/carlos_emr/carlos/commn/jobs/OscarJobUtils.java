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
package io.github.carlos_emr.carlos.commn.jobs;

import java.util.concurrent.ScheduledFuture;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.OscarJobDao;
import io.github.carlos_emr.carlos.commn.model.OscarJob;
import io.github.carlos_emr.carlos.commn.model.OscarJobType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

public class OscarJobUtils {

    /**
     * Allowed package prefix for job class instantiation via reflection.
     *
     * <p>Only classes whose fully-qualified name begins with this prefix may be loaded
     * by {@link #isJobTypeCurrentlyValid(OscarJobType)} and {@link #scheduleJob(OscarJob)}.
     * Job class names are stored in the database and this prevents a compromised row
     * from loading arbitrary JVM classes (CWE-470).</p>
     */
    private static final String ALLOWED_JOB_PACKAGE_PREFIX = "io.github.carlos_emr.carlos.";

    public static boolean isJobTypeCurrentlyValid(OscarJobType oscarJobType) {

        if (oscarJobType.getClassName() == null) {
            return false;
        }

        String className = oscarJobType.getClassName();
        if (!className.startsWith(ALLOWED_JOB_PACKAGE_PREFIX)) {
            MiscUtils.getLogger().warn("Rejected job class outside allowed package: {}",
                    LogSanitizer.sanitize(className));
            return false;
        }

        try {
            Class clazz = Class.forName(className);
            for (Class i : clazz.getInterfaces()) {
                if (i.getName().equals("io.github.carlos_emr.carlos.commn.jobs.OscarRunnable")) {
                    return true;
                }
            }
        } catch (Exception e) {
            //ignore
        }

        return false;
    }


    public static void initializeJobExecutionFramework() throws Exception {
        //SpringTaskScheduler
        OscarJobDao oscarJobDao = SpringUtils.getBean(OscarJobDao.class);


        for (OscarJob job : oscarJobDao.findAll(0, OscarJobDao.MAX_LIST_RETURN_SIZE)) {
            scheduleJob(job);
        }

    }

    public static void resetJobExecutionFramework() throws Exception {
        //SpringTaskScheduler
        OscarJobDao oscarJobDao = SpringUtils.getBean(OscarJobDao.class);


        for (Integer jobId : OscarJobExecutingManager.getFutures().keySet()) {
            ScheduledFuture<Object> future = OscarJobExecutingManager.getFutures().get(jobId);
            if (future != null) {
                future.cancel(false);
            }
        }
        OscarJobExecutingManager.getFutures().clear();


        for (OscarJob job : oscarJobDao.findAll(0, OscarJobDao.MAX_LIST_RETURN_SIZE)) {
            scheduleJob(job);
        }

    }


    public static boolean scheduleJob(OscarJob job) throws Exception {
        //SpringTaskScheduler
        TaskScheduler taskScheduler = (TaskScheduler) SpringUtils.getBean(TaskScheduler.class);
        OscarJobDao oscarJobDao = SpringUtils.getBean(OscarJobDao.class);
        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

        ScheduledFuture<Object> future = OscarJobExecutingManager.getFutures().get(job.getId());
        if (future != null) {
            future.cancel(false);
        }

        if (!job.isEnabled()) {
            return false;
        }
        if (job.getCronExpression() == null) {
            return false;
        }
        if (job.getOscarJobType() == null || !job.getOscarJobType().isEnabled() || !OscarJobUtils.isJobTypeCurrentlyValid(job.getOscarJobType())) {
            return false;
        }

        CronTrigger trigger = new CronTrigger(job.getCronExpression());

        String jobClassName = job.getOscarJobType().getClassName();
        if (!jobClassName.startsWith(ALLOWED_JOB_PACKAGE_PREFIX)) {
            throw new SecurityException("Job class outside allowed package: "
                    + LogSanitizer.sanitize(jobClassName));
        }
        OscarRunnable oscarRunnableInstance = (OscarRunnable) Class.forName(jobClassName).newInstance();

        Security security = new Security();
        security.setSecurityNo(0);
        oscarRunnableInstance.setLoggedInSecurity(security);

        Provider provider = providerDao.getProvider(job.getProviderNo());
        if (provider == null) {
            return false;
        }
        oscarRunnableInstance.setLoggedInProvider(provider);
        oscarRunnableInstance.setConfig(job.getConfig());

        ScheduledFuture<Object> schedulefuture = (ScheduledFuture<Object>) taskScheduler.schedule(oscarRunnableInstance, trigger);
        //cancel,isCancelled, isDone

        OscarJobExecutingManager.getFutures().put(job.getId(), schedulefuture);

        return true;
    }

}
