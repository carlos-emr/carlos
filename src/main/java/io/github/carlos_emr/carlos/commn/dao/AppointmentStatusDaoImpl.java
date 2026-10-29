/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.config.CacheConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentStatusDaoImpl extends AbstractDaoImpl<AppointmentStatus> implements AppointmentStatusDao {
    private static final String ALL_KEY = "all";
    private static final String ACTIVE_KEY = "active";
    private static final String STATUS_KEY_PREFIX = "status:";

    /*
     * Reads use CacheManager directly instead of @Cacheable so the cache holds private copies
     * and every read, hit or miss, returns fresh copies (issue #5304). @Cacheable would hand
     * every caller the same cached entity instances, so one caller's change would be seen by
     * all readers until the cache expired. Keys, bounds (CacheConfig) and the transaction-aware
     * put/evict behaviour are unchanged; every write still evicts through the @CacheEvict
     * overrides below.
     */
    private final CacheManager cacheManager;

    /**
     * @param cacheManager the application's transaction-aware cache manager ({@code CacheConfig}),
     *                     which must define the {@code appointmentStatuses} cache
     */
    @Autowired
    public AppointmentStatusDaoImpl(CacheManager cacheManager) {
        super(AppointmentStatus.class);
        this.cacheManager = cacheManager;
    }

    /**
     * Returns every appointment status, active or not.
     *
     * @return an unmodifiable list of detached copies; changing an element affects only this
     *         caller's copy and writes nothing to the database
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<AppointmentStatus> findAll() {
        List<AppointmentStatus> cached = getCachedList(ALL_KEY);
        if (cached != null) {
            return copyStatuses(cached);
        }

        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName());
        List<AppointmentStatus> results = query.getResultList();

        cache().put(ALL_KEY, copyStatuses(results));
        return copyStatuses(results);
    }

    /**
     * Returns the active appointment statuses ordered by id.
     *
     * @return an unmodifiable list of detached copies; changing an element affects only this
     *         caller's copy and writes nothing to the database
     */
    @Override
    public List<AppointmentStatus> findActive() {
        List<AppointmentStatus> cached = getCachedList(ACTIVE_KEY);
        if (cached != null) {
            return copyStatuses(cached);
        }

        Query q = entityManager.createQuery("select a from AppointmentStatus a where a.active=?1 order by a.id");
        q.setParameter(1, 1);

        @SuppressWarnings("unchecked")
        List<AppointmentStatus> results = q.getResultList();

        cache().put(ACTIVE_KEY, copyStatuses(results));
        return copyStatuses(results);
    }

    /**
     * Finds the status whose code starts with the first character of {@code status}.
     *
     * @param status the status code; only its first character is matched
     * @return a detached copy of the matching status, or {@code null} when {@code status} is
     *         null/empty or nothing matches (a miss is not cached)
     */
    @Override
    public AppointmentStatus findByStatus(String status) {
        if (status == null || status.length() == 0) {
            return null;
        }

        String cacheKey = STATUS_KEY_PREFIX + status;
        AppointmentStatus cached = getCachedStatus(cacheKey);
        if (cached != null) {
            return AppointmentStatus.copyOf(cached);
        }

        Query q = entityManager.createQuery("select a from AppointmentStatus a where a.status like ?1");
        q.setParameter(1, status.substring(0, 1) + "%");

        @SuppressWarnings("unchecked")
        List<AppointmentStatus> results = q.getResultList();

        for (AppointmentStatus r : results) {
            if (r.getStatus() != null && r.getStatus().length() > 0 && r.getStatus().charAt(0) == status.charAt(0)) {
                cache().put(cacheKey, AppointmentStatus.copyOf(r));
                return AppointmentStatus.copyOf(r);
            }
        }

        return null;
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CacheConfig.APPOINTMENT_STATUSES);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + CacheConfig.APPOINTMENT_STATUSES);
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private List<AppointmentStatus> getCachedList(String key) {
        Cache.ValueWrapper wrapper = cache().get(key);
        return wrapper == null ? null : (List<AppointmentStatus>) wrapper.get();
    }

    private AppointmentStatus getCachedStatus(String key) {
        Cache.ValueWrapper wrapper = cache().get(key);
        return wrapper == null ? null : (AppointmentStatus) wrapper.get();
    }

    /*
     * Unmodifiable, as these lists always were; the elements are new copies on every call.
     */
    private static List<AppointmentStatus> copyStatuses(List<AppointmentStatus> source) {
        List<AppointmentStatus> copies = new ArrayList<>(source.size());
        for (AppointmentStatus appointmentStatus : source) {
            copies.add(AppointmentStatus.copyOf(appointmentStatus));
        }
        return Collections.unmodifiableList(copies);
    }

    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true)
    @Override
    public void modifyStatus(int ID, String strDesc, String strColor) {
        AppointmentStatus appts = find(ID);
        if (appts != null) {
            appts.setDescription(strDesc);
            appts.setColor(strColor);
        }
    }

    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true)
    public void changeStatus(int ID, int iActive) {
        AppointmentStatus appts = find(ID);
        if (appts != null) {
            appts.setActive(iActive);
        }
    }

    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true)
    @Override
    public void persist(AbstractModel<?> o) { super.persist(o); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true)
    @Override
    public void merge(AbstractModel<?> o) { super.merge(o); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true)
    @Override
    public void remove(AbstractModel<?> o) { super.remove(o); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true)
    @Override
    public boolean remove(Object id) { return super.remove(id); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true)
    @Override
    public AppointmentStatus saveEntity(AppointmentStatus entity) { return super.saveEntity(entity); }

    // batch* methods use a separate EntityManager and invoke persist/remove on it directly,
    // bypassing the Spring proxy — so @CacheEvict on persist/remove never fires through this
    // path. Override both overloads to restore eviction at the proxied boundary.
    //
    // beforeInvocation = true: AbstractDaoImpl.batchPersist commits sub-batches inside its
    // loop, so a later sub-batch failure leaves earlier sub-batches persisted to the DB.
    // Default beforeInvocation = false would skip eviction on exception, pinning stale
    // entries in the cache until TTL.
    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<AppointmentStatus> oList) { super.batchPersist(oList); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<AppointmentStatus> oList, int batchSize) { super.batchPersist(oList, batchSize); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<AppointmentStatus> oList) { super.batchRemove(oList); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<AppointmentStatus> oList, int batchSize) { super.batchRemove(oList, batchSize); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true)
    @Override public void batchPersistAtomically(List<AppointmentStatus> list) { super.batchPersistAtomically(list); }
    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true)
    @Override public void batchPersistAtomically(List<AppointmentStatus> list, int size) { super.batchPersistAtomically(list, size); }
    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true)
    @Override public void batchRemoveAtomically(List<AppointmentStatus> list) { super.batchRemoveAtomically(list); }
    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true)
    @Override public void batchRemoveAtomically(List<AppointmentStatus> list, int size) { super.batchRemoveAtomically(list, size); }
    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true, beforeInvocation = true)
    @Override public void batchPersistWithIndependentCommits(List<AppointmentStatus> list) { super.batchPersistWithIndependentCommits(list); }
    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true, beforeInvocation = true)
    @Override public void batchPersistWithIndependentCommits(List<AppointmentStatus> list, int size) { super.batchPersistWithIndependentCommits(list, size); }
    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true, beforeInvocation = true)
    @Override public void batchRemoveWithIndependentCommits(List<AppointmentStatus> list) { super.batchRemoveWithIndependentCommits(list); }
    @CacheEvict(value = CacheConfig.APPOINTMENT_STATUSES, allEntries = true, beforeInvocation = true)
    @Override public void batchRemoveWithIndependentCommits(List<AppointmentStatus> list, int size) { super.batchRemoveWithIndependentCommits(list, size); }

    /**
     * Finds the first inactive status that still has associated appointments.
     *
     * @param allStatus statuses to check, in the order referenced by the returned index
     * @return the index of the first inactive status in use, or {@code -1} when none are in use
     */
    @Override
    public int checkStatusUsuage(List<AppointmentStatus> allStatus) {
        for (int i = 0; i < allStatus.size(); i++) {
            AppointmentStatus apptStatus = allStatus.get(i);
            if (apptStatus.getActive() == 1) {
                continue;
            }
            String sql = "select count(*) as total from appointment a where a.status like ?1 ";
            // sql = sql + "collate latin1_general_cs";

            Query q = entityManager.createNativeQuery(sql);
            q.setParameter(1, apptStatus.getStatus() + "%");
            Object result = q.getSingleResult();

            if (((Number) result).intValue() > 0) {
                return i;
            }
        }
        return -1;
    }
}
