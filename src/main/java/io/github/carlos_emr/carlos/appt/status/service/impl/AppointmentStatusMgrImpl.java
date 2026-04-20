/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.carlos_emr.carlos.appt.status.service.impl;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDao;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusMgr;

/**
 * Implementation of {@link AppointmentStatusMgr} that delegates directly to
 * {@link AppointmentStatusDao}. The DAO layer is now cached via Spring Cache /
 * Caffeine, so this class no longer maintains its own in-memory list.
 *
 * <p>The static methods {@link #getCachedActiveStatuses()},
 * {@link #setCachedActiveStatuses(List)}, {@link #setCacheIsDirty(boolean)}, and
 * {@link #isCacheIsDirty()} are retained for backward compatibility with callers
 * such as {@code ApptStatusData} and the {@code AppointmentStatus} JPA callback,
 * but they now delegate to the DAO (which is cached) or are no-ops.</p>
 */

public class AppointmentStatusMgrImpl implements AppointmentStatusMgr {

    private static AppointmentStatusDao appointStatusDao = SpringUtils.getBean(AppointmentStatusDao.class);

    /**
     * Returns the active appointment statuses, backed by the DAO-level Spring cache.
     *
     * @return List of active AppointmentStatus instances (unmodifiable, from cache)
     */
    public static List<AppointmentStatus> getCachedActiveStatuses() {
        return appointStatusDao.findActive();
    }

    /**
     * No-op. Retained for backward compatibility. The DAO-level Spring cache
     * handles sort order and invalidation.
     *
     * @param cachedActiveStatuses ignored
     */
    @SuppressWarnings("unchecked")
    public static synchronized void setCachedActiveStatuses(List<AppointmentStatus> cachedActiveStatuses) {
        // No-op: DAO-level Spring cache handles this
    }

    /**
     * No-op. Retained for backward compatibility with {@code AppointmentStatus.on_jpa_update()}.
     * Cache invalidation is handled by {@code @CacheEvict} on the DAO write methods.
     *
     * @return always {@code false}
     */
    public static boolean isCacheIsDirty() {
        return false;
    }

    /**
     * No-op. Retained for backward compatibility with {@code AppointmentStatus.on_jpa_update()}.
     * Cache invalidation is handled by {@code @CacheEvict} on the DAO write methods.
     *
     * @param cacheIsDirty ignored
     */
    public static void setCacheIsDirty(boolean cacheIsDirty) {
        // No-op: DAO-level Spring cache handles invalidation
    }

    public List<AppointmentStatus> getAllStatus() {
        return appointStatusDao.findAll();
    }

    public List<AppointmentStatus> getAllActiveStatus() {
        return appointStatusDao.findActive();
    }

    public AppointmentStatus getStatus(int ID) {
        return appointStatusDao.find(ID);
    }

    public void changeStatus(int ID, int iActive) {
        appointStatusDao.changeStatus(ID, iActive);
    }

    public void modifyStatus(int ID, String strDesc, String strColor) {
        appointStatusDao.modifyStatus(ID, strDesc, strColor);
    }

    public int checkStatusUsuage(List<AppointmentStatus> allStatus) {
        return appointStatusDao.checkStatusUsuage(allStatus);
    }

    public void reset() {
        appointStatusDao.modifyStatus(1, "To Do", "#FDFEC7");
        appointStatusDao.modifyStatus(2, "Daysheet Printed", "#FDFEC7");
        appointStatusDao.modifyStatus(3, "Here", "#00ee00");
        appointStatusDao.modifyStatus(4, "Picked", "#FFBBFF");
        appointStatusDao.modifyStatus(5, "Empty Room", "#FFFF33");
        appointStatusDao.modifyStatus(6, "Costumized 1", "#897DF8");
        appointStatusDao.modifyStatus(7, "Costumized 2", "#897DF8");
        appointStatusDao.modifyStatus(8, "Costumized 3", "#897DF8");
        appointStatusDao.modifyStatus(9, "Costumized 4", "#897DF8");
        appointStatusDao.modifyStatus(10, "Costumized 5", "#897DF8");
        appointStatusDao.modifyStatus(11, "Costumized 6", "#897DF8");
        appointStatusDao.modifyStatus(12, "No Show", "#cccccc");
        appointStatusDao.modifyStatus(13, "Cancelled", "#999999");
        appointStatusDao.modifyStatus(14, "Billed", "#3ea4e1");
    }
}
