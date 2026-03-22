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
package io.github.carlos_emr.carlos.webserv.rest;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MultivaluedMap;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.jobs.OscarJobExecutingManager;
import io.github.carlos_emr.carlos.commn.jobs.OscarJobUtils;
import io.github.carlos_emr.carlos.commn.model.OscarJob;
import io.github.carlos_emr.carlos.commn.model.OscarJobType;
import io.github.carlos_emr.carlos.managers.OscarJobManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarJobResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarJobTypeResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.OscarJobTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.OscarJobTypeTo1;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.stereotype.Component;

@Path("/jobs")
@Component("oscarJobService")
@Consumes(MediaType.APPLICATION_JSON)
/**
 * REST service for managing scheduled background jobs.
 *
 * @since 2012-08-13
 */
public class OscarJobService extends AbstractServiceImpl {

    Logger logger = MiscUtils.getLogger();

    @Autowired
    OscarJobManager oscarJobManager;


    @GET
    @Path("/types/current")
    @Produces("application/json")
    public OscarJobTypeResponse getCurrentlyAvailableJobTypes() {
        List<OscarJobType> results = oscarJobManager.getCurrentlyAvaliableJobTypes();

        OscarJobTypeResponse response = new OscarJobTypeResponse();
        for (OscarJobType result : results) {
            OscarJobTypeTo1 to = new OscarJobTypeTo1();
            BeanUtils.copyProperties(result, to);
            response.getTypes().add(to);
        }
        return response;
    }

    @GET
    @Path("/types/all")
    @Produces("application/json")
    public OscarJobTypeResponse getAllJobTypes() {
        List<OscarJobType> results = oscarJobManager.getAllJobTypes();

        OscarJobTypeResponse response = new OscarJobTypeResponse();
        for (OscarJobType result : results) {
            OscarJobTypeTo1 to = new OscarJobTypeTo1();
            BeanUtils.copyProperties(result, to);
            to.setCurrentlyValid(OscarJobUtils.isJobTypeCurrentlyValid(result));
            response.getTypes().add(to);
        }
        return response;
    }


    @GET
    @Path("/all")
    @Produces("application/json")
    public OscarJobResponse getAllJobs() {
        List<OscarJob> results = oscarJobManager.getAllJobs(getLoggedInInfo());

        OscarJobResponse response = new OscarJobResponse();
        for (OscarJob result : results) {
            OscarJobTo1 to = new OscarJobTo1();
            BeanUtils.copyProperties(result, to, new String[]{"oscarJobType"});
            to.setOscarJobType(new OscarJobTypeTo1());
            BeanUtils.copyProperties(result.getOscarJobType(), to.getOscarJobType());

            if (result.getCronExpression() != null) {
                CronTrigger trigger = new CronTrigger(result.getCronExpression());
                //ScheduledFuture<Object> future = OscarJobExecutingManager.getFutures().get(result.getId());

                if (result.isEnabled()) {
                    to.setNextPlannedExecutionDate(trigger.nextExecutionTime(new SimpleTriggerContext()));
                }

            }
            response.getJobs().add(to);
        }
        return response;
    }

    @GET
    @Path("/job/{jobId}")
    @Produces("application/json")
    public OscarJobResponse getJob(@PathParam("jobId") Integer jobId) {
        OscarJob result = oscarJobManager.getJob(getLoggedInInfo(), jobId);

        OscarJobResponse response = new OscarJobResponse();

        OscarJobTo1 to = new OscarJobTo1();
        BeanUtils.copyProperties(result, to, new String[]{"oscarJobType"});
        to.setOscarJobType(new OscarJobTypeTo1());
        BeanUtils.copyProperties(result.getOscarJobType(), to.getOscarJobType());
        response.getJobs().add(to);

        return response;
    }


    @POST
    @Path("/saveJob")
    @Produces("application/json")
    @Consumes("application/x-www-form-urlencoded")
    public OscarJobResponse saveJob(MultivaluedMap<String, String> params) {
        OscarJob job = new OscarJob();
        job.setId(Integer.parseInt(params.getFirst("job.id")));
        job.setDescription(params.getFirst("job.description"));
        job.setEnabled("on".equals(params.getFirst("job.enabled")) ? true : false);
        job.setName(params.getFirst("job.name"));
        job.setOscarJobTypeId(Integer.parseInt(params.getFirst("job.oscarJobTypeId")));
        job.setProviderNo(params.getFirst("job.provider"));
        job.setUpdated(new Date());

        OscarJob result = null;

        if (job.getId() > 0) {
            //edit
            OscarJob savedJob = oscarJobManager.getJob(getLoggedInInfo(), job.getId());
            savedJob.setName(job.getName());
            savedJob.setDescription(job.getDescription());
            savedJob.setEnabled(job.isEnabled());
            savedJob.setOscarJobTypeId(job.getOscarJobTypeId());
            savedJob.setOscarJobType(null);
            savedJob.setProviderNo(job.getProviderNo());

            oscarJobManager.updateJob(getLoggedInInfo(), savedJob);
            result = savedJob;
        } else {
            job.setId(null);
            oscarJobManager.saveJob(getLoggedInInfo(), job);
            result = job;
        }

        if (job.getCronExpression() != null && job.getCronExpression().length() > 0) {
            try {
                OscarJobUtils.scheduleJob(job);
            } catch (Exception e) {
                logger.warn("job " + job.getName() + " not added");
            }
        }
        return getJob(result.getId());
    }

    @POST
    @Path("/cancelJob")
    @Produces("application/json")
    @Consumes("application/x-www-form-urlencoded")
    public OscarJobResponse cancelJob(@FormParam(value = "jobId") Integer jobId) {

        ScheduledFuture<Object> future = OscarJobExecutingManager.getFutures().get(jobId);
        if (future != null) {
            future.cancel(true);
        }
        return getJob(jobId);
    }

    public String getIdsAsStringList(List<String> list) {
        StringBuilder sb = new StringBuilder();

        for (int x = 0; x < list.size(); x++) {
            if (x > 0) sb.append(',');
            sb.append(list.get(x));
        }

        return (sb.toString());
    }


    @POST
    @Path("/saveCrontabExpression")
    @Produces("application/json")
    @Consumes("application/x-www-form-urlencoded")
    public OscarJobResponse saveCrontabExpression(MultivaluedMap<String, String> params) {

        Integer jobId = null;
        try {
            jobId = Integer.parseInt(params.getFirst("scheduleJobId"));
        } catch (NumberFormatException e) {
            //TODO: log error
            return null;
        }

        String minuteChooser = params.getFirst("minute_chooser");
        List<String> minutes = params.get("minute");

        String hourChooser = params.getFirst("hour_chooser");
        List<String> hours = params.get("hour");

        String dayChooser = params.getFirst("day_chooser");
        List<String> days = params.get("day");

        String monthChooser = params.getFirst("month_chooser");
        List<String> months = params.get("month");

        String weekdayChooser = params.getFirst("weekday_chooser");
        List<String> weekdays = params.get("weekday");

        String cronExpression = "* * * * * *";
        String[] parts = cronExpression.split(" ");

        parts[0] = "0";
        parts[1] = generateCronTabItem(minuteChooser, minutes);
        parts[2] = generateCronTabItem(hourChooser, hours);
        parts[3] = generateCronTabItem(dayChooser, days);
        parts[4] = generateCronTabItem(monthChooser, months);
        parts[5] = generateCronTabItem(weekdayChooser, weekdays);

        for (int x = 0; x < parts.length; x++) {
            if (parts[x] == null) {
                logger.warn("invalid cron item..aborting");
                return null;
            }
        }

        OscarJob job = oscarJobManager.getJob(getLoggedInInfo(), jobId);
        if (job != null) {
            job.setCronExpression(parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3] + " " + parts[4] + " " + parts[5]);
            oscarJobManager.updateJob(getLoggedInInfo(), job);
            try {
                OscarJobUtils.scheduleJob(job);
            } catch (Exception e) {
                logger.warn("error scheduling job " + e);
            }
        }

        return getJob(jobId);
    }

    private String generateCronTabItem(String chooser, List<String> values) {
        if (chooser.equals("1")) {
            if (values.isEmpty()) {
                return null;
            }
            return getIdsAsStringList(values);
        }
        return "*";
    }

    @GET
    @Path("/jobType/{jobTypeId}")
    @Produces("application/json")
    public OscarJobTypeResponse getJobType(@PathParam("jobTypeId") Integer jobTypeId) {
        OscarJobType result = oscarJobManager.getJobType(getLoggedInInfo(), jobTypeId);

        OscarJobTypeResponse response = new OscarJobTypeResponse();

        OscarJobTypeTo1 to = new OscarJobTypeTo1();
        BeanUtils.copyProperties(result, to);
        response.getTypes().add(to);

        return response;
    }

    @POST
    @Path("/saveJobType")
    @Produces("application/json")
    @Consumes("application/x-www-form-urlencoded")
    public OscarJobTypeResponse saveJobType(MultivaluedMap<String, String> params) {
        OscarJobType job = new OscarJobType();
        job.setId(Integer.parseInt(params.getFirst("jobType.id")));
        job.setName(params.getFirst("jobType.name"));
        job.setDescription(params.getFirst("jobType.description"));
        job.setClassName(params.getFirst("jobType.className"));
        job.setEnabled("on".equals(params.getFirst("jobType.enabled")) ? true : false);
        job.setUpdated(new Date());

        OscarJobType result = null;

        if (job.getId() > 0) {
            //edit
            OscarJobType savedJob = oscarJobManager.getJobType(getLoggedInInfo(), job.getId());
            savedJob.setName(job.getName());
            savedJob.setDescription(job.getDescription());
            savedJob.setClassName(job.getClassName());
            savedJob.setEnabled(job.isEnabled());

            oscarJobManager.updateJobType(getLoggedInInfo(), savedJob);
            result = savedJob;
        } else {
            job.setId(null);
            oscarJobManager.saveJobType(getLoggedInInfo(), job);
            result = job;
        }

        return getJobType(result.getId());
    }

    @POST
    @Path("/enableJob")
    @Produces("application/json")
    @Consumes("application/x-www-form-urlencoded")
    public OscarJobResponse enableJob(@FormParam(value = "jobId") Integer jobId) {
        OscarJob job = oscarJobManager.getJob(getLoggedInInfo(), jobId);
        if (job != null) {
            job.setEnabled(true);
        }
        oscarJobManager.updateJob(getLoggedInInfo(), job);
        try {
            OscarJobUtils.scheduleJob(job);
        } catch (Exception e) {
            logger.warn("error scheduling job " + e);
        }
        return getJob(jobId);
    }

    @POST
    @Path("/disableJob")
    @Produces("application/json")
    @Consumes("application/x-www-form-urlencoded")
    public OscarJobResponse disableJob(@FormParam(value = "jobId") Integer jobId) {
        OscarJob job = oscarJobManager.getJob(getLoggedInInfo(), jobId);
        if (job != null) {
            job.setEnabled(false);
        }
        oscarJobManager.updateJob(getLoggedInInfo(), job);

        ScheduledFuture<Object> future = OscarJobExecutingManager.getFutures().get(job.getId());
        if (future != null) {
            future.cancel(false);
        }


        return getJob(jobId);
    }
}
