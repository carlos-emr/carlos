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
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes an audit record each time the SMS settings are saved, because the settings row itself only
 * remembers the last save. The record names who saved, the switches and provider now in force, and
 * which settings changed. It never holds a secret, a credential value or the sender number.
 * <p>
 * It joins the save's transaction, so a save that cannot be audited is not stored.
 *
 * @since 2026-09-28
 */
@Service
public class SmsConfigAuditRecorder {
    static final String ACTION = "update";
    static final String CONTENT = "sms_config";

    private final OscarLogDao oscarLogDao;

    public SmsConfigAuditRecorder(OscarLogDao oscarLogDao) {
        this.oscarLogDao = oscarLogDao;
    }

    /**
     * @param saved         the settings as saved
     * @param providerNo    the administrator who saved them
     * @param changedFields names of the settings that changed, for example {@code enabled} or
     *                      {@code webhookSecret}; names only, never values
     */
    @Transactional
    public void recordSaved(SmsConfig saved, String providerNo, List<String> changedFields) {
        OscarLog log = new OscarLog();
        log.setProviderNo(providerNo);
        log.setAction(ACTION);
        log.setContent(CONTENT);
        log.setContentId(saved.getId() == null ? null : String.valueOf(saved.getId()));
        log.setData("providerType=" + saved.getProviderType()
                + " enabled=" + saved.isEnabled()
                + " schedulerEnabled=" + saved.isSchedulerEnabled()
                + " changed=" + String.join(",", changedFields));
        oscarLogDao.persist(log);
    }
}
