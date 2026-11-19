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
 * <p>Changes for one provider are serialized on the provider's row, which exists before the
 * date's first override. Without that, two overlapping Saves for a date with no override both
 * found nothing to retire and both inserted an active override. Every active override for the
 * date is then retired, so a date that already holds two is left with exactly the
 * replacement.</p>
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
     * Retires the provider's active overrides for the date and inserts the replacement, atomically
     * and one change per provider at a time.
     *
     * @param providerNo the provider whose schedule is changed
     * @param date the schedule date
     * @param replacement the new active override, or {@code null} to leave the date with none
     * @return {@code false}, with nothing written, when no such provider exists
     */
    @Transactional
    public boolean replaceOverride(String providerNo, Date date, ScheduleDate replacement) {
        if (!scheduleDateDao.lockProviderForScheduleChange(providerNo)) {
            return false;
        }
        // Read under the lock, so a change that committed while this one waited is seen and retired.
        for (ScheduleDate current : scheduleDateDao.findActiveForUpdate(providerNo, date)) {
            current.setStatus('D');
            scheduleDateDao.merge(current);
        }
        if (replacement != null) {
            scheduleDateDao.persist(replacement);
        }
        return true;
    }

    /**
     * The provider's committed active override for the date, so a caller can show the value that
     * won rather than the one it wrote. A custom ({@code c}) override is returned ahead of a
     * rule-made ({@code b}) one.
     *
     * @param providerNo the provider whose schedule is read
     * @param date the schedule date
     * @return the active override, or {@code null} when the date has none
     */
    @Transactional(readOnly = true)
    public ScheduleDate currentOverride(String providerNo, Date date) {
        return scheduleDateDao.findByProviderNoAndDate(providerNo, date);
    }
}
