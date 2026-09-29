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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one audit record each time the SMS queue page is opened. The page lists messages with their
 * patients' demographic numbers, so who looked, and when, is recorded. The record holds no row data.
 *
 * @since 2026-09-28
 */
@Service
public class SmsQueueViewAuditRecorder {
    static final String ACTION = "read";
    static final String CONTENT = "sms_queue";

    private final OscarLogDao oscarLogDao;

    public SmsQueueViewAuditRecorder(OscarLogDao oscarLogDao) {
        this.oscarLogDao = oscarLogDao;
    }

    /**
     * @param loggedInInfo               the viewer
     * @param demographicNumbersIncluded whether the page showed demographic numbers to this viewer
     */
    @Transactional
    public void recordViewed(LoggedInInfo loggedInInfo, boolean demographicNumbersIncluded) {
        OscarLog log = new OscarLog();
        if (loggedInInfo.getLoggedInSecurity() != null) {
            log.setSecurityId(loggedInInfo.getLoggedInSecurity().getSecurityNo());
        }
        log.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        log.setIp(loggedInInfo.getIp());
        log.setAction(ACTION);
        log.setContent(CONTENT);
        log.setData("demographicNumbersIncluded=" + demographicNumbersIncluded);
        oscarLogDao.persist(log);
    }
}
