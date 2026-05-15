/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
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
import io.github.carlos_emr.carlos.config.CacheConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;
import io.github.carlos_emr.carlos.commn.model.AppointmentType;

@Repository
public class AppointmentTypeDaoImpl extends AbstractDaoImpl<AppointmentType> implements AppointmentTypeDao {
    private static final String ALL_KEY = "all";
    private static final String NAME_KEY_PREFIX = "name:";

    private final CacheManager cacheManager;

    @Autowired
    public AppointmentTypeDaoImpl(CacheManager cacheManager) {
        super(AppointmentType.class);
        this.cacheManager = cacheManager;
    }

    @Override
    public List<AppointmentType> listAll() {
        List<AppointmentType> cached = getCachedList(ALL_KEY);
        if (cached != null) {
            return copyAppointmentTypes(cached);
        }

        String sqlCommand = "select x from AppointmentType x order by x.name";
        Query query = entityManager.createQuery(sqlCommand);

        @SuppressWarnings("unchecked")
        List<AppointmentType> results = query.getResultList();

        List<AppointmentType> snapshot = Collections.unmodifiableList(copyAppointmentTypes(results));
        cache().put(ALL_KEY, snapshot);
        return copyAppointmentTypes(snapshot);

    }

    @Override
    public AppointmentType findByAppointmentTypeByName(String name) {
        String cacheKey = NAME_KEY_PREFIX + name;
        if (name != null && !name.isEmpty()) {
            AppointmentType cached = getCachedAppointmentType(cacheKey);
            if (cached != null) {
                return AppointmentType.copyOf(cached);
            }
        }

        Query query = entityManager.createQuery("from AppointmentType atype where atype.name = ?1").setParameter(1, name);
        AppointmentType result = this.getSingleResultOrNull(query);
        if (result != null && name != null && !name.isEmpty()) {
            cache().put(cacheKey, AppointmentType.copyOf(result));
        }
        return AppointmentType.copyOf(result);
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CacheConfig.APPOINTMENT_TYPES);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + CacheConfig.APPOINTMENT_TYPES);
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private List<AppointmentType> getCachedList(String key) {
        Cache.ValueWrapper wrapper = cache().get(key);
        return wrapper == null ? null : (List<AppointmentType>) wrapper.get();
    }

    private AppointmentType getCachedAppointmentType(String key) {
        Cache.ValueWrapper wrapper = cache().get(key);
        return wrapper == null ? null : (AppointmentType) wrapper.get();
    }

    private List<AppointmentType> copyAppointmentTypes(List<AppointmentType> source) {
        List<AppointmentType> copies = new ArrayList<>(source.size());
        for (AppointmentType appointmentType : source) {
            copies.add(AppointmentType.copyOf(appointmentType));
        }
        return copies;
    }

    @CacheEvict(value = CacheConfig.APPOINTMENT_TYPES, allEntries = true)
    @Override
    public void persist(AbstractModel<?> o) { super.persist(o); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_TYPES, allEntries = true)
    @Override
    public void merge(AbstractModel<?> o) { super.merge(o); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_TYPES, allEntries = true)
    @Override
    public void remove(AbstractModel<?> o) { super.remove(o); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_TYPES, allEntries = true)
    @Override
    public boolean remove(Object id) { return super.remove(id); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_TYPES, allEntries = true)
    @Override
    public AppointmentType saveEntity(AppointmentType entity) { return super.saveEntity(entity); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<AppointmentType> oList) { super.batchPersist(oList); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<AppointmentType> oList, int batchSize) { super.batchPersist(oList, batchSize); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<AppointmentType> oList) { super.batchRemove(oList); }

    @CacheEvict(value = CacheConfig.APPOINTMENT_TYPES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<AppointmentType> oList, int batchSize) { super.batchRemove(oList, batchSize); }

}
 
