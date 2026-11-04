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
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDao;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.config.CacheConfig;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusMgr;
import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusStyleException;
import org.springframework.beans.BeansException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private static final String CUSTOM_STATUS_COLOR = "#897DF8";

    private static final Pattern COLOUR = Pattern.compile("#[0-9a-fA-F]{6}");

    /*
     * Each seeded status's default style, keyed by status code: row ids differ between installs.
     * Matches database/mysql/migration/{on,bc}/V1.0.2 (Customized 6 really is seeded with 5.gif).
     */
    private static final StatusDefault[] STATUS_DEFAULTS = {
            new StatusDefault("t", "To Do", "#FDFEC7", "starbill.gif"),
            new StatusDefault("T", "Daysheet Printed", "#FDFEC7", "todo.gif"),
            new StatusDefault("H", "Here", "#00ee00", "here.gif"),
            new StatusDefault("P", "Picked", "#FFBBFF", "picked.gif"),
            new StatusDefault("E", "Empty Room", "#FFFF33", "empty.gif"),
            new StatusDefault("a", "Customized 1", CUSTOM_STATUS_COLOR, "1.gif"),
            new StatusDefault("b", "Customized 2", CUSTOM_STATUS_COLOR, "2.gif"),
            new StatusDefault("c", "Customized 3", CUSTOM_STATUS_COLOR, "3.gif"),
            new StatusDefault("d", "Customized 4", CUSTOM_STATUS_COLOR, "4.gif"),
            new StatusDefault("e", "Customized 5", CUSTOM_STATUS_COLOR, "5.gif"),
            new StatusDefault("f", "Customized 6", CUSTOM_STATUS_COLOR, "5.gif"),
            new StatusDefault("h", "Confirmed", "#2fcccf", "thumb.png"),
            new StatusDefault("N", "No Show", "#cccccc", "noshow.gif"),
            new StatusDefault("C", "Cancelled", "#999999", "cancel.gif"),
            new StatusDefault("B", "Billed", "#3ea4e1", "billed.gif")
    };

    private static AppointmentStatusDao getAppointmentStatusDao() {
        return SpringUtils.getBean(AppointmentStatusDao.class);
    }

    private static CacheManager getCacheManager() {
        return SpringUtils.getBean(CacheManager.class);
    }

    /**
     * Returns the active appointment statuses, backed by the DAO-level Spring cache.
     *
     * @return List of active AppointmentStatus instances (unmodifiable, from cache)
     */
    public static List<AppointmentStatus> getCachedActiveStatuses() {
        return getAppointmentStatusDao().findActive();
    }

    /**
     * Returns every appointment status, active or not, backed by the DAO-level Spring cache.
     * Display lookups (icon, title, colour) need inactive rows too: an appointment keeps a
     * status that was later deactivated in the status editor.
     *
     * @return List of all AppointmentStatus instances (unmodifiable, from cache)
     */
    public static List<AppointmentStatus> getCachedAllStatuses() {
        return getAppointmentStatusDao().findAll();
    }

    /**
     * No-op. Retained for backward compatibility. The DAO-level Spring cache
     * handles sort order and invalidation.
     *
     * @param cachedActiveStatuses ignored
     */
    @Deprecated
    public static void setCachedActiveStatuses(List<AppointmentStatus> cachedActiveStatuses) {
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
     * callback signals the cache is dirty, clear the Spring cache via the
     * transaction-aware {@link CacheManager} so later reads reload fresh data.</p>
     *
     * <p>If a transaction is active, the clear is deferred until after commit —
     * {@code TransactionAwareCacheManagerProxy} defers {@code put}/{@code evict}
     * but not {@code clear()}, so without this hook a {@code @PostUpdate}-triggered
     * clear would fire mid-transaction and remain in effect even if the originating
     * write later rolled back. A missing or misconfigured {@link CacheManager} is
     * logged at ERROR — it is a deployment misconfiguration that pins stale appointment
     * statuses for every reader up to the cache TTL — and swallowed so a cache-configuration
     * fault never aborts a legitimate appointment-status write from inside a JPA
     * lifecycle callback.</p>
     *
     * @param cacheIsDirty whether the legacy callback detected a write
     */
    public static void setCacheIsDirty(boolean cacheIsDirty) {
        if (!cacheIsDirty) {
            return;
        }

        final Cache cache;
        try {
            cache = getCacheManager().getCache(CacheConfig.APPOINTMENT_STATUSES);
        } catch (BeansException e) {
            logger.error("Appointment status cache invalidation skipped: CacheManager bean unavailable — readers may see stale statuses until TTL expiry", e);
            return;
        }
        if (cache == null) {
            logger.error("Appointment status cache invalidation requested but cache '{}' is not configured — readers may see stale statuses until TTL expiry", CacheConfig.APPOINTMENT_STATUSES);
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.clear();
                }
            });
        } else {
            cache.clear();
        }
    }

    @Override
    public List<AppointmentStatus> getAllStatus() {
        return getAppointmentStatusDao().findAll();
    }

    @Override
    public List<AppointmentStatus> getAllActiveStatus() {
        return getAppointmentStatusDao().findActive();
    }

    @Override
    public AppointmentStatus getStatus(int ID) {
        return getAppointmentStatusDao().find(ID);
    }

    @Override
    public boolean changeStatus(int ID, int iActive) {
        if (iActive != 0 && iActive != 1) {
            throw new AppointmentStatusStyleException("admin.appt.status.mgr.error.invalidActive");
        }
        AppointmentStatusDao appointmentStatusDao = getAppointmentStatusDao();
        AppointmentStatus status = appointmentStatusDao.find(ID);
        // editable=0 marks the core statuses the schedule depends on: they can be restyled but
        // never switched off (issue #3888).
        if (status == null || status.getEditable() != 1) {
            return false;
        }
        appointmentStatusDao.changeStatus(ID, iActive);
        return true;
    }

    @Override
    public boolean updateDescription(int id, String description) {
        String value = StringUtils.trimToEmpty(description);
        if (value.isEmpty()) {
            throw new AppointmentStatusStyleException("admin.appt.status.mgr.error.descriptionRequired");
        }
        if (value.length() > DESCRIPTION_MAX_LENGTH) {
            throw new AppointmentStatusStyleException("admin.appt.status.mgr.error.descriptionLength");
        }
        return getAppointmentStatusDao().modifyStyle(id, value, null, null);
    }

    @Override
    public boolean updateColour(int id, String colour) {
        String value = StringUtils.trimToEmpty(colour);
        // The colour is written into style attributes on the schedule, so nothing but #rrggbb is stored.
        if (!COLOUR.matcher(value).matches()) {
            throw new AppointmentStatusStyleException("admin.appt.status.mgr.error.color");
        }
        return getAppointmentStatusDao().modifyStyle(id, null, value, null);
    }

    @Override
    public boolean updateIcon(int id, String icon) {
        // The icon becomes part of an image URL on the schedule, so only listed file names are stored.
        if (icon == null || !ICON_SET.contains(icon)) {
            throw new AppointmentStatusStyleException("admin.appt.status.mgr.error.icon");
        }
        return getAppointmentStatusDao().modifyStyle(id, null, null, icon);
    }

    @Override
    public int checkStatusUsuage(List<AppointmentStatus> allStatus) {
        return getAppointmentStatusDao().checkStatusUsuage(allStatus);
    }

    @Override
    public void reset() {
        AppointmentStatusDao appointmentStatusDao = getAppointmentStatusDao();
        for (StatusDefault statusDefault : STATUS_DEFAULTS) {
            AppointmentStatus status = appointmentStatusDao.findByStatus(statusDefault.status);
            // findByStatus matches the first letter only; never restyle a two-letter custom code.
            if (status != null && statusDefault.status.equals(StringUtils.trim(status.getStatus()))) {
                appointmentStatusDao.modifyStyle(status.getId(), statusDefault.description,
                        statusDefault.color, statusDefault.icon);
            }
        }
    }

    private record StatusDefault(String status, String description, String color, String icon) {
    }
}
