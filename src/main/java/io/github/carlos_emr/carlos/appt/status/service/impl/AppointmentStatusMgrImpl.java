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

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDao;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusMgr;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Implementation of {@link AppointmentStatusMgr} that delegates directly to
 * {@link AppointmentStatusDao}. The DAO layer is now cached via Spring Cache /
 * Caffeine, so this class no longer maintains its own in-memory list.
 *
 * <p>The static methods {@link #getCachedActiveStatuses()},
 * {@link #setCachedActiveStatuses(List)}, {@link #setCacheIsDirty(boolean)}, and
 * {@link #isCacheIsDirty()} are retained for backward compatibility with callers
 * such as {@code ApptStatusData} and the {@code AppointmentStatus} JPA callback.
 * The callback path now evicts the Spring cache directly so legacy JPA writes
 * cannot leave stale appointment-status entries pinned until TTL expiry.</p>
 */

public class AppointmentStatusMgrImpl implements AppointmentStatusMgr {

    private static final Logger logger = MiscUtils.getLogger();
    private static AppointmentStatusDao appointStatusDao = SpringUtils.getBean(AppointmentStatusDao.class);
    private static CacheManager cacheManager = SpringUtils.getBean(CacheManager.class);

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
     * Returns the legacy dirty-flag state.
     *
     * <p>The manager no longer keeps an in-memory list, so the flag is never set
     * locally. The Spring cache is evicted immediately when
     * {@link #setCacheIsDirty(boolean)} is called.</p>
     *
     * @return always {@code false}
     */
    public static boolean isCacheIsDirty() {
        return false;
    }

    /**
     * Evicts the {@code appointmentStatuses} Spring cache for backward compatibility
     * with {@code AppointmentStatus.on_jpa_update()}.
     *
     * <p>Legacy JPA writes may bypass the cached DAO methods. When the entity
     * callback signals the cache is dirty, clear the Spring cache directly via the
     * transaction-aware {@link CacheManager} so later reads reload fresh data.</p>
     *
     * @param cacheIsDirty whether the legacy callback detected a write
     */
    public static void setCacheIsDirty(boolean cacheIsDirty) {
        if (!cacheIsDirty) {
            return;
        }

        Cache cache = cacheManager.getCache("appointmentStatuses");
        if (cache != null) {
            cache.clear();
        } else {
            logger.warn("Appointment status cache invalidation requested but cache 'appointmentStatuses' is not configured");
        }
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
