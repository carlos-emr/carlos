//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */
package ca.openosp.openo.olis;

import java.util.TimerTask;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.Misc;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.olis.dao.OLISSystemPreferencesDao;
import ca.openosp.openo.olis.model.OLISSystemPreferences;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.scheduling.concurrent.ScheduledExecutorTask;
//import org.springframework.scheduling.timer.ScheduledTimerTask;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class OLISPreferences2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "r", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        DateTimeFormatter input = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss Z");
        DateTimeFormatter output = DateTimeFormat.forPattern("YYYYMMddHHmmssZ");
        DateTime date;
        String startTime = Misc.getStr(request.getParameter("startTime"), "").trim();
        if (!startTime.equals("")) {
            date = input.parseDateTime(startTime);
            startTime = date.toString(output);
        }
        String endTime = Misc.getStr(request.getParameter("endTime"), "").trim();
        if (!endTime.equals("")) {
            date = input.parseDateTime(endTime);
            endTime = date.toString(output);
        }

        Integer pollFrequency = Misc.getInt(request.getParameter("pollFrequency"), 30);
        String filterPatients = request.getParameter("filter_patients");
        OLISSystemPreferencesDao olisPrefDao = (OLISSystemPreferencesDao) SpringUtils.getBean(OLISSystemPreferencesDao.class);
        ;
        OLISSystemPreferences olisPrefs = olisPrefDao.getPreferences();

        try {
            olisPrefs.setStartTime(startTime);
            olisPrefs.setEndTime(endTime);
            olisPrefs.setFilterPatients((filterPatients != null) ? true : false);
            boolean restartTimer = !pollFrequency.equals(olisPrefs.getPollFrequency());
            olisPrefs.setPollFrequency(pollFrequency);
            olisPrefDao.save(olisPrefs);
            request.setAttribute("success", true);

            if (restartTimer) {
	     		/* 	ScheduledTimerTask task = (ScheduledTimerTask)SpringUtils.getBean(ScheduledExecutorTask.class);		
	     			TimerTask tt = task.getTimerTask();
	     			Thread t = new Thread(tt);
	     			t.start();*/
                ScheduledExecutorTask task = (ScheduledExecutorTask) SpringUtils.getBean(ScheduledExecutorTask.class);
                Runnable tt = task.getRunnable();
                Thread t = new Thread(tt);
                t.start();
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Changing Preferences failed", e);
            request.setAttribute("success", false);
        }
        return SUCCESS;

    }
}
