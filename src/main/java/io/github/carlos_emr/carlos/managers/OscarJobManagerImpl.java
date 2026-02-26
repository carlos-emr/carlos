/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarJobDao;
import io.github.carlos_emr.carlos.commn.dao.OscarJobTypeDao;
import io.github.carlos_emr.carlos.commn.jobs.OscarJobUtils;
import io.github.carlos_emr.carlos.commn.model.OscarJob;
import io.github.carlos_emr.carlos.commn.model.OscarJobType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.log.LogAction;

@Service
public class OscarJobManagerImpl implements OscarJobManager {


    @Autowired
    private OscarJobDao oscarJobDao;

    @Autowired
    private OscarJobTypeDao oscarJobTypeDao;


    public void saveJob(LoggedInInfo loggedInInfo, OscarJob oscarJob) {
        oscarJobDao.persist(oscarJob);

        //--- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "OscarJobManager.saveJob", "oscarJob.id=" + oscarJob.getId());
    }

    public void updateJob(LoggedInInfo loggedInInfo, OscarJob oscarJob) {
        oscarJobDao.merge(oscarJob);

        //--- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "OscarJobManager.updateJob", "oscarJob.id=" + oscarJob.getId());
    }

    /*
     * Make sure it's a valid class, and that it implements OscarRunnable
     */
    public List<OscarJobType> getCurrentlyAvaliableJobTypes() {

        List<OscarJobType> validTypes = new ArrayList<OscarJobType>();

        for (OscarJobType oscarJobType : oscarJobTypeDao.findAll(0, AbstractDaoImpl.MAX_LIST_RETURN_SIZE)) {
            if (OscarJobUtils.isJobTypeCurrentlyValid(oscarJobType)) {
                validTypes.add(oscarJobType);
            }
        }
        return validTypes;
    }

    public List<OscarJobType> getAllJobTypes() {
        return oscarJobTypeDao.findAll(0, AbstractDaoImpl.MAX_LIST_RETURN_SIZE);
    }


    public List<OscarJob> getAllJobs(LoggedInInfo loggedInInfo) {

        List<OscarJob> jobs = oscarJobDao.findAll(0, OscarJobDao.MAX_LIST_RETURN_SIZE);

        //--- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "OscarJobManager.getAllJobs", "");

        return jobs;
    }

    public List<OscarJob> getJobByName(LoggedInInfo loggedInInfo, String name) {
        List<OscarJob> jobs = oscarJobDao.getJobByName(name);
        //--- log action ---
        LogAction.addLog(loggedInInfo, "OscarJobManager.getJobByName", null, null, null, "name=" + name);
        return jobs;
    }

    public OscarJob getJob(LoggedInInfo loggedInInfo, Integer id) {

        OscarJob job = oscarJobDao.find(id);

        if (job != null) {
            //--- log action ---
            LogAction.addLogSynchronous(loggedInInfo, "OscarJobManager.getJob", "id=" + id);
        }

        return job;
    }

    public OscarJobType addIfNotLoaded(LoggedInInfo loggedInInfo, OscarJobType jobType) {
        List<OscarJobType> allJobs = getAllJobTypes();
        boolean loaded = false;
        for (OscarJobType ojt : allJobs) {
            if (ojt.getName().equals(jobType.getName()) && ojt.getClassName().equals(jobType.getClassName())) {
                return ojt;
            }
        }
        if (!loaded) {
            saveJobType(loggedInInfo, jobType);
            return jobType;
        }
        return null;
    }

    public OscarJobType getJobType(LoggedInInfo loggedInInfo, Integer id) {

        OscarJobType jobType = oscarJobTypeDao.find(id);

        if (jobType != null) {
            //--- log action ---
            LogAction.addLogSynchronous(loggedInInfo, "OscarJobManager.getJobType", "id=" + id);
        }

        return jobType;
    }

    public void saveJobType(LoggedInInfo loggedInInfo, OscarJobType oscarJob) {
        oscarJobTypeDao.persist(oscarJob);

        //--- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "OscarJobManager.saveJobType", "oscarJobType.id=" + oscarJob.getId());
    }

    public void updateJobType(LoggedInInfo loggedInInfo, OscarJobType oscarJob) {
        oscarJobTypeDao.merge(oscarJob);

        //--- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "OscarJobManager.updateJobType", "oscarJobType.id=" + oscarJob.getId());
    }

}
