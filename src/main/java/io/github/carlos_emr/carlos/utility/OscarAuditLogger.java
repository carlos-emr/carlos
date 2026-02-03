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
 */

package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;

public class OscarAuditLogger {

    private static OscarAuditLogger instance = new OscarAuditLogger();
    private static OscarLogDao logDao = (OscarLogDao) SpringUtils.getBean(OscarLogDao.class);

    private OscarAuditLogger() {

    }

    public static OscarAuditLogger getInstance() {
        return instance;
    }

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
