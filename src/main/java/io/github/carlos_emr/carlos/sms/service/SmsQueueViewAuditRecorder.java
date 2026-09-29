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
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueWindow;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes the audit records each time the SMS queue page is opened: one for the page view itself, and one
 * for each patient whose messages were shown. The per-patient record carries the patient's demographic
 * number in the log's own patient field, so the usual audit search by patient finds it.
 * <p>
 * A patient counts as shown when any of their messages was in a list, even if this viewer does not see the
 * demographic number column: the status, times and last four phone digits were still shown. Patients whose
 * messages were hidden from this viewer are not recorded as shown. The records hold nothing else about the
 * messages.
 * <p>
 * All records of one page view are written in one transaction, so either all of them are stored or none.
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
     * @param loggedInInfo                the viewer
     * @param window                      the time period the page was opened with
     * @param displayedDemographicNumbers the patients whose messages the page is about to show this viewer;
     *                                    repeats are recorded once
     */
    @Transactional
    public void recordViewed(LoggedInInfo loggedInInfo, SmsQueueWindow window,
                             Collection<Integer> displayedDemographicNumbers) {
        Objects.requireNonNull(window, "window is required");
        Set<Integer> patients = new TreeSet<>();
        for (Integer demographicNo : displayedDemographicNumbers) {
            if (demographicNo != null) {
                patients.add(demographicNo);
            }
        }
        String windowData = "window=" + window.parameterValue();
        // The page view itself, written even when no patient was shown. No demographic number in the text.
        oscarLogDao.persist(newLog(loggedInInfo, null, windowData + " patientsShown=" + patients.size()));
        // One record per patient, so "who saw this patient's messages" can be answered by patient.
        for (Integer demographicNo : patients) {
            oscarLogDao.persist(newLog(loggedInInfo, demographicNo, windowData));
        }
    }

    private static OscarLog newLog(LoggedInInfo loggedInInfo, Integer demographicNo, String data) {
        OscarLog log = new OscarLog();
        if (loggedInInfo.getLoggedInSecurity() != null) {
            log.setSecurityId(loggedInInfo.getLoggedInSecurity().getSecurityNo());
        }
        log.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        log.setIp(loggedInInfo.getIp());
        log.setAction(ACTION);
        log.setContent(CONTENT);
        log.setDemographicId(demographicNo);
        log.setData(data);
        return log;
    }
}
