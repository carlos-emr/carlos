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
package io.github.carlos_emr.carlos.schedule.service;

import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.ScheduleDateDao;
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;

/**
 * Replaces a provider's schedule override for one date (the schedule date popup's Save and
 * Delete).
 *
 * <p>The current active override is retired (status {@code D}) and its replacement, if any, is
 * inserted in one transaction. The popup used to retire the current override from the JSP and
 * then validate and insert the replacement as separate DAO calls, so a rejected or failed Save
 * left the date with no override at all (#5297).</p>
 *
 * @since 2026-11-19
 */
@Service
public class ScheduleDateOverrideService {

    private final ScheduleDateDao scheduleDateDao;

    public ScheduleDateOverrideService(ScheduleDateDao scheduleDateDao) {
        this.scheduleDateDao = scheduleDateDao;
    }

    /**
     * Retires the provider's active override for the date and inserts the replacement, atomically.
     *
     * @param providerNo the provider whose schedule is changed
     * @param date the schedule date
     * @param replacement the new active override, or {@code null} to leave the date with none
     */
    @Transactional
    public void replaceOverride(String providerNo, Date date, ScheduleDate replacement) {
        ScheduleDate current = scheduleDateDao.findByProviderNoAndDate(providerNo, date);
        if (current != null) {
            current.setStatus('D');
            scheduleDateDao.merge(current);
        }
        if (replacement != null) {
            scheduleDateDao.persist(replacement);
        }
    }
}
