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


package io.github.carlos_emr.carlos.log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.DeamonThreadFactory;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Centralized audit logging facility for recording user actions and data access events
 * in the CARLOS EMR system.
 *
 * <p>Provides both asynchronous and synchronous methods for persisting {@link OscarLog}
 * entries. Asynchronous methods submit log entries to a cached thread pool executor
 * for non-blocking persistence, while synchronous methods persist within the caller's
 * thread and transaction context.</p>
 *
 * <p>All public methods are static for convenient access throughout the application.</p>
 *
 * @see OscarLog
 * @see LogConst
 * @see AddLogExecutorTask
 * @since 2026-03-17
 */
public class LogAction {
    private static Logger logger = MiscUtils.getLogger();
    private static OscarLogDao oscarLogDao = (OscarLogDao) SpringUtils.getBean(OscarLogDao.class);
    private static ExecutorService executorService = Executors.newCachedThreadPool(new DeamonThreadFactory(LogAction.class.getSimpleName() + ".executorService", Thread.MAX_PRIORITY));

    /**
     * Adds a log entry synchronously using the logged-in user's context.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param action String the action being performed (e.g., {@link LogConst#READ})
     * @param data String additional data associated with the log entry
     */
    public static void addLogSynchronous(LoggedInInfo loggedInInfo, String action, String data) {
        OscarLog logEntry = new OscarLog();
        if (loggedInInfo.getLoggedInSecurity() != null)
            logEntry.setSecurityId(loggedInInfo.getLoggedInSecurity().getSecurityNo());
        if (loggedInInfo.getLoggedInProvider() != null) logEntry.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        logEntry.setAction(action);
        logEntry.setData(data);
        LogAction.addLogSynchronous(logEntry);
    }

    /**
     * This method will add a log entry asynchronously in a separate thread.
     */
    public static void addLog(String provider_no, String action, String content, String data) {
        addLog(provider_no, action, content, null, null, null, data);
    }

    /**
     * This method will add a log entry asynchronously in a separate thread.
     */
    public static void addLog(String provider_no, String action, String content, String contentId, String ip) {
        addLog(provider_no, action, content, contentId, ip, null, null);
    }

    /**
     * This method will add a log entry asynchronously in a separate thread.
     */
    public static void addLog(String provider_no, String action, String content, String contentId, String ip, String demographicNo) {
        addLog(provider_no, action, content, contentId, ip, demographicNo, null);
    }

    /**
     * Adds a log entry asynchronously using the logged-in user's context with full detail.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param action String the action being performed
     * @param content String the content category (e.g., {@link LogConst#CON_DEMOGRAPHIC})
     * @param contentId String the identifier of the content being accessed
     * @param demographicNo String the patient demographic number, if applicable
     * @param data String additional data associated with the log entry
     */
    public static void addLog(LoggedInInfo loggedInInfo, String action, String content, String contentId, String demographicNo, String data) {
        OscarLog logEntry = new OscarLog();
        if (loggedInInfo.getLoggedInSecurity() != null)
            logEntry.setSecurityId(loggedInInfo.getLoggedInSecurity().getSecurityNo());
        if (loggedInInfo.getLoggedInProvider() != null) logEntry.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        logEntry.setAction(action);
        logEntry.setContent(content);
        logEntry.setContentId(contentId);
        logEntry.setIp(loggedInInfo.getIp());

        try {
            demographicNo = StringUtils.trimToNull(demographicNo);
            if (demographicNo != null) logEntry.setDemographicId(Integer.parseInt(demographicNo));
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }
        logEntry.setData(data);
        executorService.execute(new AddLogExecutorTask(logEntry));
    }

    /**
     * This method will add a log entry asynchronously in a separate thread.
     */
    public static void addLog(String provider_no, String action, String content, String contentId, String ip, String demographicNo, String data) {
        OscarLog oscarLog = new OscarLog();

        oscarLog.setProviderNo(provider_no);
        oscarLog.setAction(action);
        oscarLog.setContent(content);
        oscarLog.setContentId(contentId);
        oscarLog.setIp(ip);

        try {
            demographicNo = StringUtils.trimToNull(demographicNo);
            if (demographicNo != null) oscarLog.setDemographicId(Integer.parseInt(demographicNo));
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }

        oscarLog.setData(data);

        executorService.execute(new AddLogExecutorTask(oscarLog));
    }

    /**
     * This method will add a log entry in the same thread and can participate in the same transaction if one exists.
     */
    public static void addLogSynchronous(String provider_no, String action, String content, String contentId, String ip) {
        OscarLog oscarLog = new OscarLog();

        oscarLog.setProviderNo(provider_no);
        oscarLog.setAction(action);
        oscarLog.setContent(content);
        oscarLog.setContentId(contentId);
        oscarLog.setIp(ip);

        addLogSynchronous(oscarLog);
    }

    /**
     * This method will add the log entry in the same thread and transaction as it's being called in. This method will not throw exceptions, it will log to the file / console / log4j logger if an error occurs.
     */
    public static void addLogSynchronous(OscarLog oscarLog) {
        try {
            oscarLogDao.persist(oscarLog);
        } catch (Exception e) {
            logger.error("Error in logger.", e);
            logger.error("Error logging entry : " + oscarLog);
        }
    }


    /**
     * Persists a log entry synchronously using the provider from the HTTP session.
     *
     * <p>Originally ported from the CAISI PMM log system. Extracts the provider
     * from the session and records the access with the remote IP address.</p>
     *
     * @param accessType String the type of access being logged
     * @param entity String the entity type being accessed
     * @param entityId String the identifier of the entity
     * @param request HttpServletRequest the current HTTP request for session and IP extraction
     */
    public static void log(String accessType, String entity, String entityId, HttpServletRequest request) {
        OscarLog log = new OscarLog();

        Provider provider = (Provider) request.getSession().getAttribute("provider");
        if (provider != null) log.setProviderNo(provider.getProviderNo());

        log.setAction(accessType);
        log.setContent(entity);
        log.setContentId(entityId);
        log.setIp(request.getRemoteAddr());

        oscarLogDao.persist(log);
    }
}
