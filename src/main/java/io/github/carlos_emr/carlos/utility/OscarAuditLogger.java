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

package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;

/**
 * Singleton audit logger for recording user actions in the CARLOS EMR system.
 *
 * <p>Persists audit log entries to the database via {@link OscarLogDao}, capturing
 * the action performed, content context, associated demographic, IP address, and
 * the logged-in provider. Used for regulatory compliance and security auditing.
 *
 * @since 2026-03-17
 */
public class OscarAuditLogger {

    private static OscarAuditLogger instance = new OscarAuditLogger();
    private static OscarLogDao logDao = (OscarLogDao) SpringUtils.getBean(OscarLogDao.class);

    private OscarAuditLogger() {

    }

    /**
     * Returns the singleton instance of the audit logger.
     *
     * @return OscarAuditLogger the shared audit logger instance
     */
    public static OscarAuditLogger getInstance() {
        return instance;
    }

    /**
     * Logs an audit event with the specified action, content, and data.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info (may be {@code null})
     * @param action       String the action being performed (e.g., "read", "write", "delete")
     * @param content      String the content type or category being accessed
     * @param data         String additional data or details about the action
     */
    public void log(LoggedInInfo loggedInInfo, String action, String content, String data) {
        try {
            OscarLog logItem = new OscarLog();
            logItem.setAction(action);
            logItem.setContent(content);
            logItem.setData(data);

            if (loggedInInfo != null) logItem.setProviderNo(loggedInInfo.getLoggedInProviderNo());

            logDao.persist(logItem);

        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't write log message", e);
        }
    }

    /**
     * Logs an audit event with the specified action, content, demographic, and data.
     *
     * @param loggedInInfo  LoggedInInfo the current user's session info (may be {@code null})
     * @param action        String the action being performed
     * @param content       String the content type or category being accessed
     * @param demographicNo Integer the patient demographic number associated with the action
     * @param data          String additional data or details about the action
     */
    public void log(LoggedInInfo loggedInInfo, String action, String content, Integer demographicNo, String data) {
        try {
            OscarLog logItem = new OscarLog();
            logItem.setAction(action);
            logItem.setContent(content);
            logItem.setDemographicId(demographicNo);
            logItem.setData(data);

            if (loggedInInfo != null) logItem.setProviderNo(loggedInInfo.getLoggedInProviderNo());

            logDao.persist(logItem);

        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't write log message", e);
        }
    }

    /**
     * Logs a detailed audit event with action, content, keyword, IP address, demographic, and data.
     *
     * @param loggedInInfo  LoggedInInfo the current user's session info (may be {@code null})
     * @param action        String the action being performed
     * @param content       String the content type or category being accessed
     * @param keyword       String a keyword or content identifier for search/filtering
     * @param ipAddress     String the IP address of the requesting client
     * @param demographicNo Integer the patient demographic number associated with the action
     * @param data          String additional data or details about the action
     */
    public void log(LoggedInInfo loggedInInfo, String action, String content, String keyword, String ipAddress, Integer demographicNo, String data) {
        try {
            OscarLog logItem = new OscarLog();
            logItem.setAction(action);
            logItem.setContent(content);
            logItem.setContentId(keyword);
            logItem.setDemographicId(demographicNo);
            logItem.setData(data);
            logItem.setIp(ipAddress);

            if (loggedInInfo != null) logItem.setProviderNo(loggedInInfo.getLoggedInProviderNo());

            logDao.persist(logItem);

        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't write log message", e);
        }
    }
}
